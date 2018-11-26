name := "docker-cats-effect"

version := "0.1"

scalaVersion := "2.12.7"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "1.1.0-M1",
  "com.spotify" % "docker-client" % "8.14.4",
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "org.http4s" %% "http4s-blaze-client" % "0.20.0-M1" % Test,
  "org.http4s" %% "http4s-blaze-server" % "0.20.0-M1" % Test,
  "org.http4s" %% "http4s-dsl" % "0.20.0-M1" % Test,
)




