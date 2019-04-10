package net.andimiller

import java.io.OutputStream
import java.net.{ConnectException, Socket}
import java.nio.charset.StandardCharsets

import cats._
import cats.implicits._
import cats.syntax._
import cats.data._
import cats.effect._
import com.spotify.docker.client.{DefaultDockerClient, LogMessage}
import com.spotify.docker.client.DockerClient.{AttachParameter, LogsParam}
import com.spotify.docker.client.exceptions.ImageNotFoundException
import com.spotify.docker.client.messages.ContainerConfig.NetworkingConfig
import com.spotify.docker.client.messages._
import fs2._

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.Try

package object whales {

  object syntax extends DockerSyntax

  sealed trait LogType
  object LogType {
    case object StdOut extends LogType
    case object StdErr extends LogType
    case object StdIn  extends LogType
  }

  case class ContainerLog[F[_]](container: DockerImage[F], logType: LogType, message: String)

  case class DockerImage[F[_]](
      image: String,
      version: String,
      name: Option[String] = None,
      network: Option[String] = None,
      command: Option[List[String]] = None,
      ports: List[Int] = List.empty,
      env: Map[String, String] = Map.empty,
      volumes: Map[String, String] = Map.empty,
      bindings: Map[Port, Binding] = Map.empty,
      alwaysPull: Boolean = false,
      logSink: Option[Kleisli[F, ContainerLog[F], Unit]] = None
  )

  case class ExitedContainer(code: Long, logs: String)

  trait Port
  case class TCP(port: Int) extends Port {
    override def toString: String = s"$port/tcp"
  }
  case class UDP(port: Int) extends Port {
    override def toString: String = s"$port/udp"
  }
  object Port {
    def fromString(s: String): Option[Port] = s match {
      case s1 if s1.endsWith("/tcp") => Some(TCP(Integer.parseInt(s.stripSuffix("/tcp"))))
      case s1 if s1.endsWith("/udp") => Some(UDP(Integer.parseInt(s.stripSuffix("/udp"))))
      case _                         => None
    }
    def fromStringUnsafe(s: String): Port = fromString(s).get
  }

  case class Binding(port: Option[Int] = None, hostname: Option[String] = None)

  case class DockerContainer[F[_]: Sync: Timer](creation: DockerImage[F], container: ContainerInfo) {
    def waitForPort(port: Int,
                    backoffs: Int = 5,
                    delay: FiniteDuration = 1 second,
                    nextDelay: FiniteDuration => FiniteDuration = _ * 2): Resource[F, Unit] =
      Resource.liftF(
        Docker
          .waitTcp[F](container.networkSettings().ipAddress(), port, backoffs = backoffs, delay = delay, nextDelay = nextDelay)
          .attemptT
          .recover {
            case e: ConnectException =>
              throw new ConnectException(
                s"Unable to connect to ${creation.image}:${creation.version} (${creation.name}) on port $port: ${e.getMessage}")
          }
          .value
          .rethrow
      )

    def waitForExit(docker: DockerClient[F], backoffs: Int = 5, delay: FiniteDuration = 1 second): Resource[F, ExitedContainer] =
      Resource.liftF(
        Docker.waitExit[F](docker.docker, container.id(), backoffs, delay)
      )

    def ipAddress: String = container.networkSettings().ipAddress()

    def ports: Map[Port, List[(String, Int)]] =
      container
        .networkSettings()
        .ports()
        .asScala
        .map {
          case (k, v) => (Port.fromStringUnsafe(k), v.asScala.map(p => (p.hostIp(), Integer.parseInt(p.hostPort()))).toList)
        }
        .toMap
  }

  object Docker {
    private[whales] def client[F[_]](implicit F: Effect[F]): Resource[F, DefaultDockerClient] =
      Resource.make(
        F.delay {
          DefaultDockerClient.fromEnv().build()
        }
      ) { c =>
        F.delay {
          c.close()
        }
      }

    private[whales] def waitExit[F[_]: Sync: Timer](docker: DefaultDockerClient,
                                                    id: String,
                                                    backoffs: Int = 5,
                                                    delay: FiniteDuration = 1 second): F[ExitedContainer] =
      Stream
        .retry(
          Sync[F].delay {
            val state = docker.inspectContainer(id).state()
            assert(state.running() == false, s"Container $id still running")
            ExitedContainer(state.exitCode(), docker.logs(id, LogsParam.stdout(), LogsParam.stderr()).readFully())
          },
          delay = delay,
          nextDelay = _ * 2,
          maxAttempts = backoffs
        )
        .take(1)
        .compile
        .lastOrError

    private[whales] def waitTcp[F[_]: Sync: Timer](host: String,
                                                   port: Int,
                                                   backoffs: Int = 5,
                                                   delay: FiniteDuration = 1 second,
                                                   nextDelay: FiniteDuration => FiniteDuration): F[Unit] =
      Stream
        .retry(Sync[F].delay {
          new Socket(host, port)
        }, delay = delay, nextDelay = nextDelay, maxAttempts = backoffs)
        .compile
        .drain

    def apply[F[_]: Effect]: Resource[F, DockerClient[F]] = client[F].map(c => DockerClient[F](c))
  }

  case class DockerClient[F[_]](docker: DefaultDockerClient) {

    def apply(image: String,
              version: String,
              name: Option[String] = None,
              network: Option[String] = None,
              command: Option[List[String]] = None,
              ports: List[Int] = List.empty,
              env: Map[String, String] = Map.empty,
              volumes: Map[String, String] = Map.empty,
              bindings: Map[Port, Binding] = Map.empty,
              alwaysPull: Boolean = false,
              logSink: Option[Kleisli[F, ContainerLog[F], Unit]] = None)(implicit F: ConcurrentEffect[F],
                                                                         t: Timer[F],
                                                                         cs: ContextShift[F]): Resource[F, DockerContainer[F]] =
      apply(DockerImage(image, version, name, network, command, ports, env, volumes, bindings, alwaysPull, logSink))

    def network(name: String)(implicit F: Effect[F]): Resource[F, NetworkCreation] =
      Resource.make(
        F.delay {
          val network = NetworkConfig.builder().name(name).build()
          docker.createNetwork(network)
        }
      ) { n =>
        F.delay {
          docker.removeNetwork(n.id())
        }
      }

    def apply(image: DockerImage[F])(implicit F: ConcurrentEffect[F], t: Timer[F], cs: ContextShift[F]): Resource[F, DockerContainer[F]] =
      Resource
        .make(
          F.delay {
              val bindings = image.bindings
                .map {
                  case (k, v) =>
                    (k.toString, List(PortBinding.of(v.hostname.getOrElse(""), v.port.map(_.toString).getOrElse(""))).asJava)
                }
                .toMap
                .asJava
              val container = ContainerConfig
                .builder()
                .hostConfig(
                  HostConfig
                    .builder()
                    .appendBinds(image.volumes.map { case (k, v) => s"$k:$v" }.asJava)
                    .networkMode("bridge")
                    .portBindings(bindings)
                    .build()
                )
                .image(image.image + ":" + image.version)
                .exposedPorts(image.ports.map(_.toString): _*)
                .env(image.env.map { case (k, v) => s"$k=$v" }.toList.asJava)

              val withCommand = image.command.foldLeft(container)((c, s) => c.cmd(s.asJava))
              val imageName   = image.image + ":" + image.version
              if (image.alwaysPull) {
                docker.pull(imageName)
              } else {
                Try { docker.inspectImage(imageName) }.recover {
                  case _: ImageNotFoundException =>
                    docker.pull(imageName)
                }
              }
              val creation = image.name match {
                case Some(name) => docker.createContainer(withCommand.build(), name)
                case None       => docker.createContainer(withCommand.build())
              }
              image.network.foreach { network =>
                docker.connectToNetwork(creation.id(), network)
              }
              docker.startContainer(creation.id())
              val logThread = image.logSink.traverse {
                sink =>
                  ThreadUtils.startThread(
                    F.delay { "started thread" } *> Stream
                      .fromIterator[F, LogMessage](
                        docker
                          .attachContainer(creation.id(),
                                           AttachParameter.LOGS,
                                           AttachParameter.STDOUT,
                                           AttachParameter.STDERR,
                                           AttachParameter.STREAM)
                          .asScala)
                      .flatMap { lm =>
                        val logType = lm.stream() match {
                          case LogMessage.Stream.STDOUT => LogType.StdOut
                          case LogMessage.Stream.STDERR => LogType.StdErr
                          case LogMessage.Stream.STDIN  => LogType.StdIn
                        }
                        Stream.eval(
                          sink.run(ContainerLog(image, logType, StandardCharsets.UTF_8.decode(lm.content()).toString))
                        )
                      }
                      .compile
                      .drain
                  )
              }
              (logThread, DockerContainer(image, docker.inspectContainer(creation.id())))
            }
            .flatMap {
              case (logThread, container) =>
                F.start(logThread).tupleRight(container) <* F.delay { println("started thread") }
            }
        ) {
          case (logThread, DockerContainer(_, c)) =>
            F.delay {
              println("cleaning up")
              val container = docker.inspectContainer(c.id())
              if (container.state().running()) {
                docker.killContainer(c.id())
              }
            } *> F.delay { println("killing log thread") } *>
              logThread.cancel *>
              F.delay {
                docker.removeContainer(c.id())
              } *> logThread.cancel
        }
        .map(_._2)
  }
}
