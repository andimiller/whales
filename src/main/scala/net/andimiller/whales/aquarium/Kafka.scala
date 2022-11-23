package net.andimiller.whales.aquarium

import cats._
import cats.effect.{Resource, Temporal}
import cats.implicits._
import net.andimiller.whales._
import net.andimiller.whales.syntax._

import scala.concurrent.duration._

object Kafka {
  def singleNode[F[_]: Temporal](topics: List[String]): Resource[F, DockerContainer] =
    for {
      docker <- Docker[F]
      zookeeper <- docker(
                    "zookeeper",
                    "latest",
                    name = "zookeeper".some
                  )
      _ <- zookeeper.waitForPort[F](2181, delay = 100.millis, backoffs = 10)
      kafka <- docker(
                "andimiller/kafka",
                "latest",
                name = "kafka".some,
                bindings = Map(9092.tcp -> Binding(port = Option(9092))),
                env = Map(
                  "KAFKA_ZOOKEEPER_CONNECT"    -> s"${zookeeper.ipAddress}:2181",
                  "KAFKA_ADVERTISED_HOST_NAME" -> "kafka",
                  "KAFKA_BROKER_ID"            -> "1",
                  "KAFKA_LISTENERS"            -> "PLAINTEXT://0.0.0.0:9092",
                  "KAFKA_ADVERTISED_LISTENERS" -> "PLAINTEXT://localhost:9092",
                  "KAFKA_CREATE_TOPICS"        -> topics.map(t => s"$t:8:1").mkString(",")
                )
              )
      _ <- kafka.waitForPort[F](9092, delay = 100.millis, nextDelay = identity, backoffs = 100)
    } yield kafka
}
