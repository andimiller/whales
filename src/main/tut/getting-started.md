---
layout: docsplus
title: Getting Started
---

# Dependencies

insert it here once I've published it to bintray

# Imports

Make sure you have the standard cats imports.

```tut
import cats._, cats.implicits._
```

And the main cats-effect import.

```tut
import cats.effect._
```

Then you can also import this library.

```tut
import net.andimiller.docker._
```


# Docker client

To make a docker client you simply call the Docker object with your effect type (see cats-effect to learn more about effect types).

```tut
Docker[IO]
```

This client itself is a resource, (since it internally keeps a spotify docker client with a pool), so it's easiest if we use it in a for comprehension.

```tut
for {
  docker <- Docker[IO]
  nginx <- docker("nginx", "latest")
} yield nginx
```

The client has an apply method which takes arguments on what kind of docker image we want, if you'd like to see all available options see the `DockerImage` class, and the default parameters on the apply method.

You can also pass in a DockerImage if you'd rather work with the case class.

```tut
import net.andimiller.docker.Docker.DockerImage
for {
  docker <- Docker[IO]
  nginx <- docker(DockerImage("nginx", "latest"))
} yield nginx
```

# Waiting for containers to come up

The main way to wait for a container right now is waiting for a TCP socket to be bound, this is done like so:

```tut:invisible
implicit val timer = IO.timer(scala.concurrent.ExecutionContext.global)
implicit val sync = Sync[IO]
```

```tut
for {
  docker <- Docker[IO]
  nginx <- docker("nginx", "latest")
  _ <- nginx.waitForPort(80)
} yield nginx
```

This will attempt to acquire an open TCP socket to the specified port on that container and will do an exponential backoff, this backoff can be tuned with the optional parameters, such as `backoffs` which is defaulted to `5`.
