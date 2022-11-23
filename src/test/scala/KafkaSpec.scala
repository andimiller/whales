import cats._
import cats.implicits._
import cats.effect._
import cats.effect.unsafe.IORuntime
import fs2.kafka._
import net.andimiller.whales._
import net.andimiller.whales.aquarium
import net.andimiller.whales.syntax._
import org.scalatest.{FlatSpec, MustMatchers}

import scala.concurrent.duration._

class KafkaSpec extends FlatSpec with MustMatchers {
  implicit val runtime = IORuntime.builder().build()

  val consumerSettings =
    ConsumerSettings[IO, Unit, String]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers("localhost:9092")
      .withGroupId("group")

  val producerSettings =
    ProducerSettings[IO, Unit, String]
      .withBootstrapServers("localhost:9092")

  "the Kafka zoo recipe" should "spin up a single node cluster" in {
    val data = List("hello", "world", "have", "a", "few", "messages")
    (
      aquarium.Kafka.singleNode[IO](List("example_topic")),
      KafkaProducer.resource[IO, Unit, String](producerSettings),
      KafkaConsumer.resource[IO, Unit, String](consumerSettings)
    ).tupled
      .use {
        case (container, producer, consumer) =>
          for {
            _ <- consumer.subscribeTo("example_topic")
            _ <- fs2.Stream
                  .emits(data)
                  .covary[IO]
                  .evalTap(s => producer.produce(ProducerRecords.one(ProducerRecord("example_topic", (), s))).flatten.void)
                  .compile
                  .drain
            _       <- IO.sleep(2 seconds)
            results <- consumer.stream.take(6).compile.toList.map(_.map(_.record.value))
          } yield results must equal(data)
      }
      .unsafeRunSync()
  }
}
