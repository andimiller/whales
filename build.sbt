enablePlugins(MicrositesPlugin)

name := "docker-cats-effect"

version := "0.1"

scalaVersion := "2.12.7"

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

micrositeName := "Cats Effect Docker"

micrositeDescription := "A Docker Client for use with cats-effect"

micrositeAuthor := "Andi Miller"

micrositeOrganizationHomepage := "http://andimiller.net/"

micrositeGithubOwner := "andimiller"
micrositeGithubRepo := "docker-cats-effect"




