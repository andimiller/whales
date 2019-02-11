package net.andimiller

import java.net.{ConnectException, Socket}

import cats._
import cats.implicits._
import cats.syntax._
import cats.data._
import cats.effect._
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient.LogsParam
import com.spotify.docker.client.exceptions.ImageNotFoundException
import com.spotify.docker.client.messages.ContainerConfig.NetworkingConfig
import com.spotify.docker.client.messages._
import fs2._

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.Try

package object whales {

  object syntax extends DockerSyntax

  case class DockerImage(
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
      case _ => None
    }
    def fromStringUnsafe(s: String): Port = fromString(s).get
  }

  case class Binding(port: Option[Int] = None, hostname: Option[String] = None)

  case class DockerContainer(creation: DockerImage, container: ContainerInfo) {
    def waitForPort[F[_]: Sync: Timer](port: Int, backoffs: Int = 5, delay: FiniteDuration = 1 second, nextDelay: FiniteDuration => FiniteDuration = _ * 2): Resource[F, Unit] =
      Resource.liftF(
        Docker
          .waitTcp[F](container.networkSettings().ipAddress(), port, backoffs = backoffs, delay = delay, nextDelay = nextDelay)
          .attemptT
          .recover {
            case e: ConnectException =>
              throw new ConnectException(s"Unable to connect to ${creation.image}:${creation.version} (${creation.name}) on port $port: ${e.getMessage}")
          }
          .value
          .rethrow
      )

    def waitForExit[F[_]: Sync: Timer](docker: DockerClient[F], backoffs: Int = 5, delay: FiniteDuration = 1 second): Resource[F, ExitedContainer] =
      Resource.liftF(
        Docker.waitExit[F](docker.docker, container.id(), backoffs, delay)
      )

    def ipAddress: String = container.networkSettings().ipAddress()

    def ports: Map[Port, List[(String, Int)]] =
      container.networkSettings().ports().asScala.map{
        case (k, v) => (Port.fromStringUnsafe(k), v.asScala.map(p => (p.hostIp(), Integer.parseInt(p.hostPort()))).toList)
      }.toMap
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


    private[whales] def waitExit[F[_]: Sync: Timer](docker: DefaultDockerClient, id: String, backoffs: Int = 5, delay: FiniteDuration = 1 second): F[ExitedContainer] =
      Stream.retry(Sync[F].delay {
        val state = docker.inspectContainer(id).state()
        assert(state.running() == false, s"Container $id still running")
        ExitedContainer(state.exitCode(), docker.logs(id, LogsParam.stdout(), LogsParam.stderr()).readFully())
      }, delay = delay, nextDelay = _ * 2, maxAttempts = backoffs)
      .take(1)
      .compile
      .lastOrError

    private[whales] def waitTcp[F[_]: Sync: Timer](host: String, port: Int, backoffs: Int = 5, delay: FiniteDuration = 1 second, nextDelay: FiniteDuration => FiniteDuration): F[Unit] =
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
              alwaysPull: Boolean = false)(implicit F: Effect[F]): Resource[F, DockerContainer] =
      apply(DockerImage(image, version, name, network, command, ports, env, volumes, bindings, alwaysPull))

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

    def apply(image: DockerImage)(implicit F: Effect[F]): Resource[F, DockerContainer] =
      Resource.make(
        F.delay {
          val bindings = image.bindings.map { case (k, v) =>
            (k.toString, List(PortBinding.of(v.hostname.getOrElse(""), v.port.map(_.toString).getOrElse(""))).asJava)
          }.toMap.asJava
          val container = ContainerConfig
            .builder()
            .hostConfig(
              HostConfig.builder()
                .appendBinds(image.volumes.map { case (k, v) => s"$k:$v" }.asJava)
                .networkMode("bridge")
                .portBindings(bindings)
                .build()
            )
            .image(image.image + ":" + image.version)
            .exposedPorts(image.ports.map(_.toString): _*)
            .env(image.env.map { case (k, v) => s"$k=$v" }.toList.asJava)

          val withCommand = image.command.foldLeft(container)((c, s) => c.cmd(s.asJava))
          val imageName = image.image + ":" + image.version
          if (image.alwaysPull) {
            docker.pull(imageName)
          } else {
            Try { docker.inspectImage(imageName) }.recover { case _: ImageNotFoundException =>
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
          DockerContainer(image, docker.inspectContainer(creation.id()))
        }
      ) {
        case DockerContainer(_, c) =>
          F.delay {
            val container = docker.inspectContainer(c.id())
            if (container.state().running()) {
              docker.killContainer(c.id())
            }
            docker.removeContainer(c.id())
          }
      }
  }
}
