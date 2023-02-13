package org.knora.webapi.util
import zio.ZIO
import zio.Task

object ZioHelper {

  def sequence[A](x: Seq[Task[A]]): Task[List[A]] =
    x.map(a => a.map(x => List(x))).fold(ZIO.succeed(List[A]()))((x, y) => x.flatMap(a => y.map(b => a ++ b)))

  def sequence[A](x: Set[Task[A]]): Task[Set[A]] =
    x.map(a => a.map(x => Set(x))).fold(ZIO.succeed(Set[A]()))((x, y) => x.flatMap(a => y.map(b => a ++ b)))

}
