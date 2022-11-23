//enablePlugins(MicrositesPlugin)
//enablePlugins(TutPlugin)

organization := "net.andimiller"

name := "whales"

scalaVersion := "2.13.0"

crossScalaVersions := List("2.12.8", "2.11.12", "2.13.0")

def enablePartialUnification(version: String) = version match {
  case "2.11" | "2.12" => List("-Ypartial-unification")
  case "2.13"          => List.empty
}

scalacOptions ++= Seq(
  "-language:higherKinds",
)

scalacOptions ++= enablePartialUnification(scalaBinaryVersion.value)

lazy val catsEffectVersion    = "3.3.14"
lazy val fs2Version           = "3.4.0"
lazy val spotifyDockerVersion = "8.14.4"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect"  % catsEffectVersion,
  "co.fs2"        %% "fs2-core"     % fs2Version,
  "co.fs2"        %% "fs2-io"       % fs2Version,
  "com.spotify"   % "docker-client" % spotifyDockerVersion,
)

lazy val http4sVersion = "0.23.11"

libraryDependencies ++= Seq(
  "org.scalatest"  %% "scalatest"           % "3.0.8"       % Test,
  "org.http4s"     %% "http4s-blaze-client" % http4sVersion % Test,
  "org.http4s"     %% "http4s-blaze-server" % http4sVersion % Test,
  "org.http4s"     %% "http4s-dsl"          % http4sVersion % Test,
  "ch.qos.logback" % "logback-classic"      % "1.2.3"       % Test,
  "org.tpolecat"   %% "doobie-core"         % "0.8.0-M1"    % Test,
  "mysql"          % "mysql-connector-java" % "8.0.15"      % Test,
  "com.ovoenergy"  %% "fs2-kafka"           % "0.20.0-M1"   % Test,
)

//libraryDependencies ++= Seq(
//  "org.http4s" %% "http4s-blaze-client" % http4sVersion % Tut,
//)

scalafmtConfig in ThisBuild := Some(file("scalafmt.conf"))

// Microsite things
//
//micrositeName := "whales"
//
//micrositeDescription := "Cats-based Docker Client"
//
//micrositeAuthor := "Andi Miller"
//
//micrositeOrganizationHomepage := "http://andimiller.net/"
//
//micrositeGithubOwner := "andimiller"
//micrositeGithubRepo := "whales"
//
//micrositeUrl := "https://whales.andimiller.net"
//
//micrositeHomepage := "https://whales.andimiller.net"
//
//micrositeDocumentationUrl := "/getting-started.html"
//
//micrositePalette := Map(
//  "brand-primary"   -> "#80CBC4",
//  "brand-secondary" -> "#00796B",
//  "brand-tertiary"  -> "#004D40",
//  "gray-dark"       -> "#453E46",
//  "gray"            -> "#837F84",
//  "gray-light"      -> "#E3E2E3",
//  "gray-lighter"    -> "#F4F3F4",
//  "white-color"     -> "#FFFFFF"
//)
//
//excludeFilter in ghpagesCleanSite :=
//  new FileFilter {
//    def accept(f: File) = (ghpagesRepository.value / "CNAME").getCanonicalPath == f.getCanonicalPath
//  }

// publishing/releasing settings
import xerial.sbt.Sonatype._

useGpg := true
publishTo := sonatypePublishTo.value
licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))
sonatypeProjectHosting := Some(GitHubHosting("andimiller", "whales", "andi at andimiller dot net"))
developers := List(Developer(id = "andimiller", name = "Andi Miller", email = "andi@andimiller.net", url = url("http://andimiller.net")))

import ReleaseTransformations._
releaseCrossBuild := false
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepCommandAndRemaining("+test"),
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
