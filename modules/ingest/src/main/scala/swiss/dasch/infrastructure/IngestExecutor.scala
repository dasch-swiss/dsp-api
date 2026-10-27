/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.config.Configuration.ExecutorConfig
import zio.*

import java.lang.Runtime as JRuntime
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bounds the CPU ingest work may take from the process it is embedded in.
 *
 * `run` moves an effect onto a fixed pool of `ingest-<n>` threads; fibers it forks stay there. `withPermit` bounds
 * work that leaves that pool (child processes, and blocking calls, which `attemptBlocking` shifts to ZIO's unbounded
 * blocking pool) to as many concurrent sections as the pool has threads.
 *
 * A permit section must be leaf-level: it must never call code that takes a permit itself. The semaphore is not
 * reentrant, so a nested section deadlocks once every permit is held.
 */
final class IngestExecutor private (executor: Executor, semaphore: Semaphore) {

  def run[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] = zio.onExecutor(executor)

  def withPermit[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] = semaphore.withPermit(zio)
}

object IngestExecutor {

  private[infrastructure] def poolSize(availableProcessors: Int, cpuShare: Double): Int =
    math.max(1, math.floor(availableProcessors * cpuShare).toInt)

  /** An executor of `size` threads and permits, shut down when the scope closes. */
  def make(size: Int): URIO[Scope, IngestExecutor] = {
    val newPool = ZIO.succeed(Executors.newFixedThreadPool(size, threadFactory))
    for {
      pool      <- ZIO.acquireRelease(newPool)(pool => ZIO.succeed(pool.shutdown()))
      semaphore <- Semaphore.make(size.toLong)
    } yield IngestExecutor(Executor.fromJavaExecutor(pool), semaphore)
  }

  private def threadFactory: ThreadFactory = {
    val counter = AtomicInteger(0)
    (runnable: Runnable) => {
      val thread = Thread(runnable, s"ingest-${counter.incrementAndGet()}")
      thread.setDaemon(true)
      thread
    }
  }

  val layer: URLayer[ExecutorConfig, IngestExecutor] = ZLayer.scoped {
    for {
      config <- ZIO.service[ExecutorConfig]
      size    = poolSize(JRuntime.getRuntime.availableProcessors(), config.cpuShare)
      _      <- ZIO.logInfo(s"Ingest executor: $size threads and permits (cpu-share ${config.cpuShare})")
      exec   <- make(size)
    } yield exec
  }
}
