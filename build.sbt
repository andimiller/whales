enablePlugins(MicrositesPlugin)
enablePlugins(TutPlugin)

organization := "net.andimiller"

name := "whales"

version := "0.1"

scalaVersion := "2.12.8"

scalacOptions += "-Ypartial-unification"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "1.1.0",
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

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-blaze-client" % "0.20.0-M1" % Tut,
)

micrositeName := "whales"

micrositeDescription := "Cats-based Docker Client"

micrositeAuthor := "Andi Miller"

micrositeOrganizationHomepage := "http://andimiller.net/"

micrositeGithubOwner := "andimiller"
micrositeGithubRepo := "whales"

micrositeUrl := "http://andimiller.github.io/"

micrositeBaseUrl := "/whales"

micrositeHomepage := "http://andimiller.github.io/andimiller/"

micrositeDocumentationUrl := "/getting-started.html"

micrositePalette := Map(
        "brand-primary"     -> "#80CBC4",
        "brand-secondary"   -> "#00796B",
        "brand-tertiary"    -> "#004D40",
        "gray-dark"         -> "#453E46",
        "gray"              -> "#837F84",
        "gray-light"        -> "#E3E2E3",
        "gray-lighter"      -> "#F4F3F4",
        "white-color"       -> "#FFFFFF")




