import java.io.File
import java.util.concurrent.{Executor, ExecutorService, Executors}

import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl.io._
import cats._
import cats.implicits._
import cats.data._
import cats.effect._
import cats.effect.implicits._
import cats.effect.internals.IOContextShift
import net.andimiller.whales._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.util.CaseInsensitiveString
import org.scalatest.{FlatSpec, MustMatchers, WordSpec}

import scala.concurrent.ExecutionContext

class DockerSpec extends FlatSpec with MustMatchers {

  object implicit0 {
    def unapply[A](a: A): Option[A] = Some(a)
  }

  def fixedPool[F[_]: Sync](n: Int): Resource[F, ExecutorService] =
    Resource.make(Sync[F].delay(Executors.newFixedThreadPool(n)))(p =>
      Sync[F].delay {
        p.shutdown()
    })

  implicit val is = Sync[IO]

  implicit val cs    = IO.contextShift(ExecutionContext.global)
  implicit val ce    = ConcurrentEffect[IO]
  implicit val timer = IO.timer(ExecutionContext.global)

  "Docker" must "successfully spin up nginx" in {
    val resources = for {
      docker <- Docker[IO]
      nginx  <- docker("nginx", "latest")
      _      <- nginx.waitForPort[IO](80)
      client <- BlazeClientBuilder[IO](ExecutionContext.global).resource
    } yield (client, nginx)
    resources
      .use {
        case (c, n) =>
          c.expect[String](s"http://${n.ipAddress}/")
      }
      .unsafeRunSync() must include("Welcome to nginx!")
  }
  "Docker" must "mount volumes into nginx" in {
    val webroot = new File("src/test/resources/webroot/").getAbsolutePath
    val resources = for {
      docker <- Docker[IO]
      nginx  <- docker(DockerImage("nginx", "latest", volumes = Map(webroot -> "/usr/share/nginx/html")))
      client <- BlazeClientBuilder[IO](ExecutionContext.global).resource
    } yield (client, nginx)
    resources
      .use {
        case (c, n) =>
          c.expect[String](s"http://${n.ipAddress}/index.txt")
      }
      .unsafeRunSync() must equal("I'm a resource which got mounted into nginx")
  }
  "Docker" must "do benthos chains" in {
    val resources = for {
      docker <- Docker[IO]
      benthos1 <- docker("jeffail/benthos",
                         "latest",
                         env = Map(
                           "INPUT_TYPE"  -> "http_server",
                           "OUTPUT_TYPE" -> "http_server",
                           "BUFFER_TYPE" -> "memory",
                         ),
                         ports = List(4195))
      _ <- benthos1.waitForPort[IO](4195)
      benthos2 <- docker(
                   "jeffail/benthos",
                   "latest",
                   env = Map(
                     "INPUT_TYPE"            -> "http_client",
                     "INPUT_HTTP_CLIENT_URL" -> s"http://${benthos1.ipAddress}:4195/benthos/get",
                     "OUTPUT_TYPE"           -> "http_server",
                     "BUFFER_TYPE"           -> "memory",
                   ),
                   ports = List(4195)
                 )
      _ <- benthos2.waitForPort[IO](4195)
      benthos3 <- docker(
                   "jeffail/benthos",
                   "latest",
                   env = Map(
                     "INPUT_TYPE"            -> "http_client",
                     "INPUT_HTTP_CLIENT_URL" -> s"http://${benthos2.ipAddress}:4195/benthos/get",
                     "OUTPUT_TYPE"           -> "http_server",
                     "BUFFER_TYPE"           -> "memory",
                   ),
                   ports = List(4195)
                 )
      _ <- benthos3.waitForPort[IO](4195)
      benthos4 <- docker(
                   "jeffail/benthos",
                   "latest",
                   env = Map(
                     "INPUT_TYPE"            -> "http_client",
                     "INPUT_HTTP_CLIENT_URL" -> s"http://${benthos3.ipAddress}:4195/benthos/get",
                     "OUTPUT_TYPE"           -> "http_server",
                     "BUFFER_TYPE"           -> "memory",
                   ),
                   ports = List(4195)
                 )
      _ <- benthos4.waitForPort[IO](4195)
      benthos5 <- docker(
                   "jeffail/benthos",
                   "latest",
                   env = Map(
                     "INPUT_TYPE"            -> "http_client",
                     "INPUT_HTTP_CLIENT_URL" -> s"http://${benthos4.ipAddress}:4195/benthos/get",
                     "OUTPUT_TYPE"           -> "http_server",
                     "BUFFER_TYPE"           -> "memory",
                   ),
                   ports = List(4195)
                 )
      _      <- benthos5.waitForPort[IO](4195)
      client <- BlazeClientBuilder[IO](ExecutionContext.global).resource
    } yield (benthos1, benthos5, client)
    resources
      .use {
        case (benthos1, benthos5, client) =>
          for {
            _ <- client.expect[String](Request[IO](Method.POST, Uri.unsafeFromString(s"http://${benthos1.ipAddress}:4195/benthos/post"))
                  .withEntity[String]("hello world"))
            res <- client.expect[String](Request[IO](Method.GET, Uri.unsafeFromString(s"http://${benthos5.ipAddress}:4195/benthos/get")))
          } yield (res)
      }
      .unsafeRunSync() must equal("hello world")
  }

  "Docker" must "support networks" in {
    val resources: Resource[IO, ExitedContainer] = for {
      docker <- Docker[IO]
      test1  <- docker.network("test1")
      nginx  <- docker("nginx", "latest", name = Some("nginx1"), network = Some(test1.id()))
      _      <- nginx.waitForPort(80)
      curl   <- docker("byrnedo/alpine-curl", "latest", network = Some(test1.id()), command = Some("http://nginx1/"))
      exited <- curl.waitForExit(docker)
    } yield exited
    resources.use { exited =>
      IO {
        exited.code must equal(0)
        exited.logs must include("Welcome to nginx!")
      }
    }.unsafeRunSync()
  }

  "Docker" must "be able to expose ports on the host" in {
    import net.andimiller.whales.syntax._
    val resources = for {
      docker <- Docker[IO]
      nginx  <- docker("nginx", "latest", ports = List(80), bindings = Map(80.tcp -> Binding(9090)))
      _      <- nginx.waitForPort[IO](80)
      client <- BlazeClientBuilder[IO](ExecutionContext.global).resource
    } yield (client, nginx)
    resources
      .use {
        case (c, n) =>
          println("docker came up")
          c.expect[String](s"http://localhost:9090/")
      }
      .unsafeRunSync() must include("Welcome to nginx!")
  }
}
