package org.knora.webapi.util

import zio.Task
import zio.ZIO

import scala.reflect.ClassTag

object ZioHelper {

  def sequence[K: ClassTag, A](x: Map[K, Task[A]]): Task[Map[K, A]] =
    ZIO
      .foreach(x) { case (key, value) => value.map(it => key -> it) }
      .map(_.toMap)

  def sequence[A](x: Seq[Task[A]]): Task[Seq[A]] = ZIO.collectAll(x)

  def sequence[A](x: Set[Task[A]]): Task[Set[A]] = ZIO.collectAll(x)
}
