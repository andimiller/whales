---
layout: home
technologies:
 - first: ["cats", "Cats is pretty cool"]
 - second: ["cats-effect", "cats-effect is also pretty cool"]
 - third: ["docker", "docker is a sad fact of life"]
---



# Overview

Whales is a library which gives you a cats-friendly interface for Docker, it lets you manage docker containers with [cats.effect.Resource](https://typelevel.org/cats-effect/datatypes/resource.html).

It's intended to be used for integration testing but you can use it for whatever you want.

# Dependency


```scala
resolvers += Resolver.bintrayRepo("andimiller", "maven")
libraryDependencies += "net.andimiller" %% "whales" % "0.3"
```


# Example

```tut:silent
import cats._, cats.implicits._, cats.effect._
import net.andimiller.whales._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.Client

import scala.concurrent.ExecutionContext

object Example extends IOApp {
  val nginx: Resource[IO, DockerContainer] = for {
    docker <- Docker[IO]
    nginx  <- docker("nginx", "latest")
    _      <- nginx.waitForPort[IO](80)
  } yield nginx

  val client: Resource[IO, Client[IO]] = BlazeClientBuilder[IO](ExecutionContext.global).resource

  override def run(args: List[String]): IO[ExitCode] = (nginx, client).bisequence.use { case (n, c) =>
    for {
      _      <- IO { println("docker started") }
      result <- c.expect[String](s"http://${n.ipAddress}/")
      _      <- IO { println(result) }
    } yield ExitCode.Success

  }
}
```
