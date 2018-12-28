---
layout: docsplus
title: Getting Started
---

# Dependencies

This library is currently available on my bintray with:

```scala
resolvers += Resolver.bintrayRepo("andimiller", "maven")
libraryDependencies += "net.andimiller" %% "whales" % "0.3"
```

# Imports

Make sure you have the standard cats imports.

```tut:silent
import cats._, cats.implicits._
```

And the main cats-effect import.

```tut:silent
import cats.effect._
```

Then you can also import this library.

```tut:silent
import net.andimiller.whales._
```


# Docker client

To make a docker client you simply call the Docker object with your effect type (see cats-effect to learn more about effect types).

```tut:silent
Docker[IO]
```

This client itself is a resource, (since it internally keeps a spotify docker client with a pool), so it's easiest if we use it in a for comprehension.

```tut:silent
for {
  docker <- Docker[IO]
  nginx <- docker("nginx", "latest")
} yield nginx
```

The client has an apply method which takes arguments on what kind of docker image we want, if you'd like to see all available options see the `DockerImage` class, and the default parameters on the apply method.

You can also pass in a DockerImage if you'd rather work with the case class.

```tut:silent
for {
  docker <- Docker[IO]
  nginx <- docker(DockerImage("nginx", "latest"))
} yield nginx
```
