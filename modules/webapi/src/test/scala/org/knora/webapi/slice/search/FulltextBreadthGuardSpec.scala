/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search

import org.junit.runner.RunWith
import zio.*
import zio.test.*
import zio.test.Assertion.failsWithA

import dsp.errors.BadRequestException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.search.FulltextBreadthGuard.BreadthKey
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

@RunWith(classOf[DspZTestJUnitRunner])
class FulltextBreadthGuardSpec extends ZIOSpecDefault {

  private val cap = 250000

  private def guardWith(probe: BreadthKey => Task[Long]): UIO[FulltextBreadthGuard] =
    FulltextBreadthGuard.make(cap, cacheCapacity = 16, cacheTtl = 1.minute, maxConcurrent = 4)(probe)

  private val wildcard = LuceneQueryString("der*") // shouldProbe = true
  private val single   = LuceneQueryString("der")  // shouldProbe = false

  // A query failure unrelated to the breadth cap (stands in for e.g. a TriplestoreTimeoutException).
  private final class QueryBoom extends Exception("boom")

  override def spec: Spec[TestEnvironment, Any] = suite("FulltextBreadthGuard")(
    // An over-cap probe refuses with a 400 and interrupts the still-running query — a query that never completes
    // on its own does not hold the refusal up, and its onInterrupt firing proves it was actually freed.
    test("refuses an over-cap term and interrupts the in-flight query") {
      for {
        interrupted <- Promise.make[Nothing, Unit]
        guard       <- guardWith(_ => ZIO.succeed(cap.toLong + 1))
        query        = ZIO.never.onInterrupt(interrupted.succeed(()))
        exit        <- guard.guarded(wildcard, None, None, None)(query).exit
        _           <- interrupted.await
      } yield assert(exit)(failsWithA[BadRequestException])
    },
    test("admits a query whose probed breadth is exactly at the cap") {
      for {
        guard  <- guardWith(_ => ZIO.succeed(cap.toLong))
        result <- guard.guarded(wildcard, None, None, None)(ZIO.succeed("ok"))
      } yield assertTrue(result == "ok")
    },
    test("fails open when the probe errors (the real query is untouched)") {
      for {
        guard  <- guardWith(_ => ZIO.fail(new RuntimeException("no lucene index")))
        result <- guard.guarded(wildcard, None, None, None)(ZIO.succeed("ok"))
      } yield assertTrue(result == "ok")
    },
    test("does not probe a single plain term, even one that would be refused") {
      for {
        probes <- Ref.make(0)
        guard  <- guardWith(_ => probes.update(_ + 1).as(cap.toLong + 1))
        result <- guard.guarded(single, None, None, None)(ZIO.succeed("ok"))
        count  <- probes.get
      } yield assertTrue(result == "ok", count == 0)
    },
    // The guard must not serialise: the query starts immediately, concurrently with the probe, rather than only
    // after it. The probe here is gated open, yet the query still runs (signals `queryStarted`) — a serial guard
    // that ran the probe first would block on the gate forever and `queryStarted.await` would deadlock.
    test("starts the query concurrently with the probe (the query is not delayed by the probe)") {
      for {
        queryStarted <- Promise.make[Nothing, Unit]
        probeGate    <- Promise.make[Nothing, Unit]
        guard        <- guardWith(_ => probeGate.await.as(cap.toLong)) // probe blocks until released
        query         = queryStarted.succeed(()) *> ZIO.succeed("ok")
        fib          <- guard.guarded(wildcard, None, None, None)(query).fork
        _            <- queryStarted.await                             // the query ran while the probe was still gated
        _            <- probeGate.succeed(())
        result       <- fib.join
      } yield assertTrue(result == "ok")
    },
    // A query failure unrelated to the cap (a timeout, say) is surfaced once the probe admits the term.
    test("propagates a query failure for an admitted term") {
      for {
        guard <- guardWith(_ => ZIO.succeed(cap.toLong))
        exit  <- guard.guarded(wildcard, None, None, None)(ZIO.fail(new QueryBoom)).exit
      } yield assert(exit)(failsWithA[QueryBoom])
    },
    // Measured breadth is cached single-flight: a repeated term for the same key reuses the cached verdict and
    // does not re-probe. The probe function is invoked exactly once across two calls.
    test("caches the probed breadth so a repeated term does not re-probe") {
      for {
        probes <- Ref.make(0)
        guard  <- guardWith(_ => probes.update(_ + 1).as(cap.toLong))
        first  <- guard.guarded(wildcard, None, None, None)(ZIO.succeed("a"))
        second <- guard.guarded(wildcard, None, None, None)(ZIO.succeed("b"))
        count  <- probes.get
      } yield assertTrue(first == "a", second == "b", count == 1)
    },
  )
}
