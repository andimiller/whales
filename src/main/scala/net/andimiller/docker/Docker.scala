package net.andimiller.docker

import cats._
import cats.implicits._
import cats.syntax._
import cats.data._
import cats.effect._
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.{ContainerConfig, HostConfig}

import scala.collection.JavaConverters._

object Docker {

  case class DockerImage(
                          name: String,
                          version: String,
                          command: Option[String] = None,
                          ports: List[Int] = List.empty,
                          env: Map[String, String] = Map.empty,
                          volumes: Map[String, String] = Map.empty
                        )

  def client[F[_]](implicit F: ConcurrentEffect[F]) = Resource.make(
    F.delay {
      DefaultDockerClient.fromEnv().build()
    }
  ) { c =>
    F.delay {
      c.close()
    }
  }

  def apply[F[_]: ConcurrentEffect]: Resource[F, DockerClient[F]] = client[F].flatMap(c => Resource.pure(DockerClient[F](c)))

  case class DockerClient[F[_]](docker: DefaultDockerClient) {
    def apply(image: DockerImage)(implicit F: ConcurrentEffect[F]) = Resource.make(
      F.delay {
        val container = ContainerConfig.builder()
          .hostConfig(
            HostConfig.builder().appendBinds(image.volumes.map { case (k, v) => s"$k:$v" }.asJava).build()
          )
          .image(image.name + ":" + image.version)
          .exposedPorts(image.ports.map(_.toString): _*)
          .env(image.env.map { case (k, v) => s"$k=$v" }.toList.asJava)
        val withCommand = image.command.foldLeft(container)((c, s) => c.cmd(s))
        val creation = docker.createContainer(withCommand.build())
        docker.startContainer(creation.id())
        docker.inspectContainer(creation.id())
      }
    ) { c =>
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
