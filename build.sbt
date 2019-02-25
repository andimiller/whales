enablePlugins(MicrositesPlugin)
enablePlugins(TutPlugin)

organization := "net.andimiller"

name := "whales"

scalaVersion := "2.12.8"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalacOptions += "-Ypartial-unification"

crossScalaVersions := List("2.12.8", "2.11.12")

lazy val catsEffectVersion    = "1.1.0"
lazy val fs2Version           = "1.0.0"
lazy val spotifyDockerVersion = "8.14.4"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect"  % catsEffectVersion,
  "co.fs2"        %% "fs2-core"     % fs2Version,
  "co.fs2"        %% "fs2-io"       % fs2Version,
  "com.spotify"   % "docker-client" % spotifyDockerVersion,
)

lazy val http4sVersion = "0.20.0-M1"

libraryDependencies ++= Seq(
  "org.scalatest"  %% "scalatest"           % "3.0.5"       % Test,
  "org.http4s"     %% "http4s-blaze-client" % http4sVersion % Test,
  "org.http4s"     %% "http4s-blaze-server" % http4sVersion % Test,
  "org.http4s"     %% "http4s-dsl"          % http4sVersion % Test,
  "ch.qos.logback" % "logback-classic"      % "1.2.3"       % Test,
)

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-blaze-client" % http4sVersion % Tut,
)

scalafmtConfig in ThisBuild := Some(file("scalafmt.conf"))

// Microsite things

micrositeName := "whales"

micrositeDescription := "Cats-based Docker Client"

micrositeAuthor := "Andi Miller"

micrositeOrganizationHomepage := "http://andimiller.net/"

micrositeGithubOwner := "andimiller"
micrositeGithubRepo := "whales"

micrositeUrl := "http://andimiller.github.io/"

micrositeBaseUrl := "/whales"

micrositeHomepage := "http://andimiller.github.io/andimiller/"

micrositeDocumentationUrl := "/whales/getting-started.html"

micrositePalette := Map(
  "brand-primary"   -> "#80CBC4",
  "brand-secondary" -> "#00796B",
  "brand-tertiary"  -> "#004D40",
  "gray-dark"       -> "#453E46",
  "gray"            -> "#837F84",
  "gray-light"      -> "#E3E2E3",
  "gray-lighter"    -> "#F4F3F4",
  "white-color"     -> "#FFFFFF"
)
