---
layout: docsplus
title: Networking
---

```tut:invisible
import cats._, cats.implicits._
import cats.effect._
import net.andimiller.whales._
implicit val timer = IO.timer(scala.concurrent.ExecutionContext.global)
implicit val sync = Sync[IO]
```

## Using IP addresses

You can network manually by just reading the IP addresses of containers

```tut:silent
for {
  docker <- Docker[IO]
  nginx  <- docker("nginx", "latest")
  _      <- nginx.waitForPort(80)
  curl   <- docker("byrnedo/alpine-curl", "latest", command = Some(s"http://${nginx.ipAddress}"))
  exited <- curl.waitForExit(docker)
} yield exited
```

## Using container names

### Creating a network

You can create a docker network as a resource using the client, this will be cleaned up for you

```tut:silent

for {
  docker <- Docker[IO]
  network <- docker.network("mynetwork")
} yield network
```

### Running containers in a network

You can then run some containers and set their network, allowing them to resolve each other's names

```tut:silent
for {
  docker <- Docker[IO]
  test1  <- docker.network("test1")
  nginx  <- docker("nginx", "latest", name = Some("nginx1"), network = Some(test1.id()))
  _      <- nginx.waitForPort(80)
  curl   <- docker("byrnedo/alpine-curl", "latest", network = Some(test1.id()), command = Some("http://nginx1/"))
  exited <- curl.waitForExit(docker)
} yield exited
```
