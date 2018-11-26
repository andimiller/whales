package net.andimiller.docker

import cats._
import cats.implicits._
import cats.syntax._
import cats.data._
import cats.effect._

object UnsafeResourceSyntax {
  implicit class MappableResource[F[_]: Sync, A](r: Resource[F, A]) {
    def map[B](f: A => B) =
      r.flatMap(a => Resource.make(Sync[F].delay { f(a) })(_ => Sync[F].pure(Unit)))
  }
}
