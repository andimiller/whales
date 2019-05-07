import cats._
import cats.implicits._
import cats.effect._
import fs2.kafka._
import net.andimiller.whales._
import net.andimiller.whales.aquarium
import net.andimiller.whales.syntax._
import org.scalatest.{FlatSpec, MustMatchers}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.global

class KafkaSpec extends FlatSpec with MustMatchers {
  implicit val timer = IO.timer(global)
  implicit val cs    = IO.contextShift(global)

  val consumerSettings =
    ConsumerSettings[Unit, String]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers("localhost:9092")
      .withGroupId("group")

  val producerSettings =
    ProducerSettings[Unit, String]
      .withBootstrapServers("localhost:9092")

  "the Kafka zoo recipe" should "spin up a single node cluster" in {
    val data = List("hello", "world", "have", "a", "few", "messages")
    (
      aquarium.Kafka.singleNode[IO](List("example_topic")),
      producerResource[IO, Unit, String](producerSettings),
      consumerResource[IO, Unit, String](consumerSettings)
    ).tupled
      .use {
        case (container, producer, consumer) =>
          for {
            _ <- consumer.subscribeTo("example_topic")
            _ <- fs2.Stream
                  .emits(data)
                  .covary[IO]
                  .evalTap(s => producer.produce(ProducerMessage.one(ProducerRecord("example_topic", (), s))).flatten.void)
                  .compile
                  .drain
            _       <- IO.sleep(2 seconds)
            results <- consumer.stream.take(6).compile.toList.map(_.map(_.record.value()))
          } yield results must equal(data)
      }
      .unsafeRunSync()
  }
}
