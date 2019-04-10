package net.andimiller.whales

import cats._, cats.implicits._, cats.data._
import cats.effect._
import org.slf4j.LoggerFactory

object Sinks {

  def slf4jSink[F[_]: Sync]: Kleisli[F, ContainerLog[F], Unit] = Kleisli { log =>
    Sync[F].delay {
      val name = log.container.name.getOrElse(
        s"${log.container.image}#${log.container.version}"
      )
      val logger = LoggerFactory.getLogger(s"net.andimiller.whales.container.$name")
      log.logType match {
        case LogType.StdOut => logger.info(log.message.trim)
        case LogType.StdErr => logger.warn(log.message.trim)
        case _              => ()
      }
    }
  }

}
