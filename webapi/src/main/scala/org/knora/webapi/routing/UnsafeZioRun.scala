package org.knora.webapi.routing

import zio._

import scala.concurrent.Future

object UnsafeZioRun {

  def run[R, E, A](zioAction: ZIO[R, E, A])(implicit r: Runtime[R]): Exit[E, A] =
    Unsafe.unsafe(implicit u => r.unsafe.run(zioAction))

  def runToFuture[R, A](zioAction: ZIO[R, Throwable, A])(implicit r: Runtime[R]): Future[A] =
    Unsafe.unsafe(implicit u => r.unsafe.runToFuture(zioAction))
}
