/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.infrastructure

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class IngestExecutorSpec extends ZIOSpecDefault {

  private val threadName = ZIO.succeed(Thread.currentThread().getName)

  val spec = suite("IngestExecutor")(
    test("run executes on an ingest thread") {
      ZIO.scoped(IngestExecutor.make(2).flatMap(_.run(threadName))).map(name => assertTrue(name.startsWith("ingest-")))
    },
    test("run returns to an ingest thread after a blocking call") {
      ZIO.scoped {
        IngestExecutor.make(2).flatMap(_.run(ZIO.attemptBlocking(()) *> threadName))
      }.map(name => assertTrue(name.startsWith("ingest-")))
    },
    test("a fiber forked as a daemon inside run executes on an ingest thread") {
      ZIO.scoped {
        IngestExecutor.make(2).flatMap(_.run(threadName.forkDaemon.flatMap(_.join)))
      }.map(name => assertTrue(name.startsWith("ingest-")))
    },
    test("no more withPermit sections than permits run at once") {
      ZIO.scoped {
        for {
          executor <- IngestExecutor.make(2)
          running  <- Ref.make(0)
          maxSeen  <- Ref.make(0)
          section   = executor.withPermit(
                      running.updateAndGet(_ + 1).flatMap(n => maxSeen.update(math.max(_, n))) *>
                        ZIO.sleep(50.millis) *>
                        running.update(_ - 1),
                    )
          _   <- ZIO.foreachParDiscard(1 to 6)(_ => section)
          max <- maxSeen.get
        } yield assertTrue(max == 2)
      }
    } @@ TestAspect.withLiveClock,
    test("the pool size is the floored share of the processors, at least one") {
      assertTrue(
        IngestExecutor.poolSize(4, 0.5) == 2,
        IngestExecutor.poolSize(3, 0.5) == 1,
        IngestExecutor.poolSize(1, 0.5) == 1,
        IngestExecutor.poolSize(8, 1.0) == 8,
      )
    },
  )
}
