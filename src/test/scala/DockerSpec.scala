import java.io.File
import java.util.concurrent.{Executor, ExecutorService, Executors}
import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl.io._
import cats.effect._
import cats.effect.unsafe.IORuntime
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
  implicit val runtime = IORuntime.builder().build()

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
      _      <- nginx.waitForPort[IO](80)
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
                           "BUFFER_TYPE" -> "memory"
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
                     "BUFFER_TYPE"           -> "memory"
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
                     "BUFFER_TYPE"           -> "memory"
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
                     "BUFFER_TYPE"           -> "memory"
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
                     "BUFFER_TYPE"           -> "memory"
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
      curl   <- docker("appropriate/curl", "latest", network = Some(test1.id()), command = Some(List("-s", "http://nginx1/")))
      exited <- curl.waitForExit(docker)
    } yield exited
    resources
      .use { exited =>
        IO {
          exited.code must equal(0)
        }
      }
      .unsafeRunSync()
  }

  "Docker" must "be able to expose ports on the host" in {
    import net.andimiller.whales.syntax._
    val resources = for {
      docker <- Docker[IO]
      nginx  <- docker("nginx", "latest", ports = List(80), bindings = Map(80.tcp -> Binding(Some(9090))))
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

  "Docker" must "be able to expose random ports on the host" in {
    import net.andimiller.whales.syntax._
    val resources = for {
      docker <- Docker[IO]
      nginx  <- docker("nginx", "latest", ports = List(80), bindings = Map(80.tcp -> Binding(hostname = Some("0.0.0.0"))))
      _      <- nginx.waitForPort[IO](80)
    } yield nginx
    resources
      .use {
        case n =>
          IO { n.ports.get(80.tcp) must not be empty }
      }
      .unsafeRunSync()
  }

  "Docker" must "be able to create volumes" in {
    val resources = for {
      docker  <- Docker[IO]
      volume  <- docker.volume("whales-storage")
      creater <- docker("busybox", "latest", command = Some(List("touch", "/volume/hello")), volumes = Map(volume.name() -> "/volume"))
      _       <- creater.waitForExit(docker)
      reader  <- docker("busybox", "latest", command = Some(List("ls", "/volume/")), volumes = Map(volume.name() -> "/volume"))
      output  <- reader.waitForExit(docker)
    } yield output
    resources
      .use { output =>
        IO {
          output.code must equal(0L)
          output.logs must include("hello")
        }
      }
      .unsafeRunSync()
  }
}
