---
layout: docsplus
title: Waiting
---

```tut:invisible
import cats._, cats.implicits._
import cats.effect._
import net.andimiller.docker._
```


# Waiting for containers to come up

The main way to wait for a container right now is waiting for a TCP socket to be bound, this is done like so:

```tut:invisible
implicit val timer = IO.timer(scala.concurrent.ExecutionContext.global)
implicit val sync = Sync[IO]
```

```tut:silent
for {
  docker <- Docker[IO]
  nginx <- docker("nginx", "latest")
  _ <- nginx.waitForPort(80)
} yield nginx
```

This will attempt to acquire an open TCP socket to the specified port on that container and will do an exponential backoff, this backoff can be tuned with the optional parameters, such as `backoffs` which is defaulted to `5`.
