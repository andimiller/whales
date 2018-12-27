---
layout: home
technologies:
 - first: ["cats", "Cats is pretty cool"]
 - second: ["cats-effect", "cats-effect is also pretty cool"]
 - third: ["docker", "docker is a sad fact of life"]
---



# Overview

Whales is a library which gives you a cats-friendly interface for Docker.

It's intended to be used for integration testing but you can use it for whatever you want.

# Example

```scala
import cats._, cats.implicits._
import net.andimiller.docker._
import cats.effect._

val nginx = for {
  docker <- Docker[IO]
  nginx <- docker("nginx", "latest")
  _ <- nginx.waitForPort[IO](80)
} yield nginx

nginx.use { (n) =>
  IO {
    println(s"a web server is now available on http://${n.ipAddress}/")
    readLine()
  }
}
```
