import java.io.File
import java.util.concurrent.{Executor, ExecutorService, Executors}

import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl.io._
import cats._
import cats.implicits._
import cats.data._
import cats.effect._
import cats.effect.internals.IOContextShift
import net.andimiller.docker.Docker.DockerImage
import net.andimiller.docker._
import net.andimiller.docker.UnsafeResourceSyntax._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.util.CaseInsensitiveString
import org.scalatest.{FlatSpec, MustMatchers, WordSpec}

import scala.concurrent.ExecutionContext

class DockerSpec extends FlatSpec with MustMatchers {

  object implicit0 {
    def unapply[A](a: A): Option[A] = Some(a)
  }

  def fixedPool[F[_] : Sync](n: Int): Resource[F, ExecutorService] =
    Resource.make(Sync[F].delay(Executors.newFixedThreadPool(n)))(p => Sync[F].delay {
      p.shutdown()
    })

  implicit val cs = IOContextShift.global
  implicit val ce = ConcurrentEffect[IO]

  "Docker" must "successfully spin up nginx" in {
    val resources = for {
      docker <- Docker[IO]
      nginx <- docker(DockerImage("nginx", "latest"))
      client <- BlazeClientBuilder[IO](ExecutionContext.global).resource
    } yield (client, nginx)
    resources.use { case (c, n) =>
      c.expect[String](s"http://${n.networkSettings().ipAddress()}/")
    }.unsafeRunSync() must include("Welcome to nginx!")
  }
  "Docker" must "mount volumes into nginx" in {
    val webroot = new File("src/test/resources/webroot/").getAbsolutePath
    val resources = for {
      docker <- Docker[IO]
      nginx  <- docker(DockerImage("nginx", "latest", volumes = Map(webroot -> "/usr/share/nginx/html")))
      client <- BlazeClientBuilder[IO](ExecutionContext.global).resource
    } yield (client, nginx)
    resources.use { case (c, n) =>
      c.expect[String](s"http://${n.networkSettings().ipAddress()}/index.txt")
    }.unsafeRunSync() must equal("I'm a resource which got mounted into nginx")
  }
}
