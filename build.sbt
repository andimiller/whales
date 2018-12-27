enablePlugins(MicrositesPlugin)

name := "whales"

version := "0.1"

scalaVersion := "2.12.8"

scalacOptions += "-Ypartial-unification"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "1.1.0-M1",
  "co.fs2" %% "fs2-core" % "1.0.0",
  "co.fs2" %% "fs2-io" % "1.0.0",
  "com.spotify" % "docker-client" % "8.14.4",
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "org.http4s" %% "http4s-blaze-client" % "0.20.0-M1" % Test,
  "org.http4s" %% "http4s-blaze-server" % "0.20.0-M1" % Test,
  "org.http4s" %% "http4s-dsl" % "0.20.0-M1" % Test,
  "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
)

micrositeName := "Whales"

micrositeDescription := "Cats-based Docker Client"

micrositeAuthor := "Andi Miller"

micrositeOrganizationHomepage := "http://andimiller.net/"

micrositeGithubOwner := "andimiller"
micrositeGithubRepo := "whales"

micrositeUrl := "http://andimiller.github.io/"

micrositeBaseUrl := "/whales"

micrositeHomepage := "http://andimiller.github.io/andimiller/"




