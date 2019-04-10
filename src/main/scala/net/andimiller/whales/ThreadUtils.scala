package net.andimiller.whales

import java.io.OutputStream

import cats.effect._
import cats._
import cats.implicits._
import cats.data._
import com.spotify.docker.client.{LogMessage, LogStream}
import fs2._

import scala.concurrent.ExecutionContext

object ThreadUtils {

  def startThread[F[_]](body: F[Unit])(implicit F: ConcurrentEffect[F]): F[Fiber[F, Unit]] = {
    @volatile var keepRunning: Boolean = true
    F.delay {
        new Thread(() => {
          do {
            try {
              ConcurrentEffect[F].toIO(body).unsafeRunSync()
            } catch {
              case _: InterruptedException => ()
            }
          } while (keepRunning)
        })
      }
      .map { t =>
        new Fiber[F, Unit] {
          override def cancel: CancelToken[F] = F.delay {
            keepRunning = false
            t.interrupt()
          }
          override def join: F[Unit] = F.delay {
            t.join()
          }
        }
      }
  }

}
