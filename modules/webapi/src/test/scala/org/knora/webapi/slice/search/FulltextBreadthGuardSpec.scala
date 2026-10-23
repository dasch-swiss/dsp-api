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
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.search.FulltextBreadthGuard.BreadthKey
import org.knora.webapi.slice.search.repo.SearchFulltextQuery
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

@RunWith(classOf[DspZTestJUnitRunner])
class FulltextBreadthGuardSpec extends ZIOSpecDefault {

  private val cap         = 250000
  private val cacheTtl    = 1.minute
  private val verdictWait = 5.seconds
  private val failureTtl  = 30.seconds

  private def guardWith(probe: BreadthKey => Task[Long], cacheCapacity: Int = 16): URIO[Scope, FulltextBreadthGuard] =
    FulltextBreadthGuard.make(cap, cacheCapacity, cacheTtl, maxConcurrent = 4, verdictWait, failureTtl)(probe)

  // The guard's verdict-wait timeout registers only once the query has finished; advancing the clock earlier hangs.
  private val verdictWaitRegistered: UIO[Unit] =
    TestClock.sleeps.flatMap(s => ZIO.yieldNow.as(s)).repeatUntil(_.nonEmpty).unit

  // A probe that answers `breadth` only once `gate` is released, counting its starts.
  private def gatedProbe(gate: Promise[Nothing, Unit], probes: Ref[Int], breadth: Long): BreadthKey => Task[Long] =
    _ => probes.update(_ + 1) *> gate.await.as(breadth)

  private def countingOverCapProbe(probes: Ref[Int]): BreadthKey => Task[Long] =
    _ => probes.update(_ + 1).as(cap.toLong + 1)

  private def keyOf(term: LuceneQueryString): BreadthKey = BreadthKey(SearchFulltextQuery.buildProbe(term, None, None))

  private val wildcard = LuceneQueryString("der*") // shouldProbe = true
  private val single   = LuceneQueryString("der")  // shouldProbe = false

  private val scoped = Some(InternalIri("http://www.knora.org/data/0001/anything"))

  // A query failure unrelated to the breadth cap (stands in for e.g. a TriplestoreTimeoutException).
  private final class QueryBoom extends Exception("boom")

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("FulltextBreadthGuard")(
    // An over-cap probe refuses with a 400 and interrupts the still-running query — a query that never completes
    // on its own does not hold the refusal up, and its onInterrupt firing proves it was actually freed.
    test("refuses an over-cap term and interrupts the in-flight query") {
      for {
        interrupted <- Promise.make[Nothing, Unit]
        guard       <- guardWith(_ => ZIO.succeed(cap.toLong + 1))
        query        = ZIO.never.onInterrupt(interrupted.succeed(()))
        exit        <- guard.guarded(wildcard, None, None)(query).exit
        _           <- interrupted.await
      } yield assert(exit)(failsWithA[BadRequestException])
    },
    test("admits a query whose probed breadth is exactly at the cap") {
      for {
        guard  <- guardWith(_ => ZIO.succeed(cap.toLong))
        result <- guard.guarded(wildcard, None, None)(ZIO.succeed("ok"))
      } yield assertTrue(result == "ok")
    },
    test("fails open when the probe errors (the real query is untouched)") {
      for {
        guard  <- guardWith(_ => ZIO.fail(new RuntimeException("no lucene index")))
        result <- guard.guarded(wildcard, None, None)(ZIO.succeed("ok"))
      } yield assertTrue(result == "ok")
    },
    // The clock is advanced inside the window before the second call: under a frozen TestClock even a zero TTL has
    // not expired, so a second call at the same instant would pass without the failure being cached at all.
    test("caches a failed probe for failure-ttl, failing open without re-probing") {
      for {
        probes   <- Ref.make(0)
        guard    <- guardWith(_ => probes.update(_ + 1) *> ZIO.fail(new RuntimeException("no lucene index")))
        first    <- guard.guarded(wildcard, None, None)(ZIO.succeed("a"))
        _        <- TestClock.adjust(failureTtl.dividedBy(2))
        second   <- guard.guarded(wildcard, None, None)(ZIO.succeed("b"))
        inWindow <- probes.get
        _        <- TestClock.adjust(failureTtl.dividedBy(2) + 1.milli)
        third    <- guard.guarded(wildcard, None, None)(ZIO.succeed("c"))
        afterTtl <- probes.get
      } yield assertTrue(first == "a", second == "b", third == "c", inWindow == 1, afterTtl == 2)
    },
    test("does not probe a single plain term, even one that would be refused") {
      for {
        probes <- Ref.make(0)
        guard  <- guardWith(_ => probes.update(_ + 1).as(cap.toLong + 1))
        result <- guard.guarded(single, None, None)(ZIO.succeed("ok"))
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
        fib          <- guard.guarded(wildcard, None, None)(query).fork
        _            <- queryStarted.await                             // the query ran while the probe was still gated
        _            <- probeGate.succeed(())
        result       <- fib.join
      } yield assertTrue(result == "ok")
    },
    test("propagates a query failure for an admitted term") {
      for {
        guard <- guardWith(_ => ZIO.succeed(cap.toLong))
        exit  <- guard.guarded(wildcard, None, None)(ZIO.fail(new QueryBoom)).exit
      } yield assert(exit)(failsWithA[QueryBoom])
    },
    // Measured breadth is cached single-flight: a repeated term for the same key reuses the cached verdict and
    // does not re-probe.
    test("caches the probed breadth so a repeated term does not re-probe") {
      for {
        probes <- Ref.make(0)
        guard  <- guardWith(_ => probes.update(_ + 1).as(cap.toLong))
        first  <- guard.guarded(wildcard, None, None)(ZIO.succeed("a"))
        second <- guard.guarded(wildcard, None, None)(ZIO.succeed("b"))
        count  <- probes.get
      } yield assertTrue(first == "a", second == "b", count == 1)
    },
    // The guard must hand the data graph to the probe: a term too broad repository-wide but rare in the project
    // is refused unscoped and admitted scoped. The double answers by the probe SPARQL it receives.
    test("measures a scoped search with the scoped probe") {
      val unscopedProbe = SearchFulltextQuery.buildProbe(wildcard, None, None)
      for {
        guard    <- guardWith(key => ZIO.succeed(if (key.probeSparql == unscopedProbe) cap.toLong + 1 else 1L))
        unscoped <- guard.guarded(wildcard, None, None)(ZIO.succeed("ok")).exit
        admitted <- guard.guarded(wildcard, None, scoped)(ZIO.succeed("ok"))
      } yield assert(unscoped)(failsWithA[BadRequestException]) && assertTrue(admitted == "ok")
    },
    // A known over-cap term is refused before anything starts: the repeat call's query never runs (no HTTP request,
    // no Fuseki thread) and no second probe is made. The repeat call runs uninterruptibly so a forked query inherits
    // that and always runs to completion; an interruptible fork could be interrupted before its first step, and the
    // counter would stay 0 even if the guard had started it.
    test("refuses a known over-cap term without starting its query or probing") {
      for {
        probes        <- Ref.make(0)
        queryStarts   <- Ref.make(0)
        guard         <- guardWith(countingOverCapProbe(probes))
        first         <- guard.guarded(wildcard, None, None)(ZIO.succeed("ok")).exit
        second        <- guard.guarded(wildcard, None, None)(queryStarts.update(_ + 1).as("ok")).uninterruptible.exit
        queryStartCnt <- queryStarts.get
        probeCount    <- probes.get
      } yield assert(first)(failsWithA[BadRequestException]) &&
        assert(second)(failsWithA[BadRequestException]) &&
        assertTrue(queryStartCnt == 0, probeCount == 1)
    },
    test("re-probes an over-cap term once its verdict has expired") {
      for {
        probes     <- Ref.make(0)
        guard      <- guardWith(countingOverCapProbe(probes))
        first      <- guard.guarded(wildcard, None, None)(ZIO.succeed("ok")).exit
        _          <- TestClock.adjust(cacheTtl + 1.milli)
        second     <- guard.guarded(wildcard, None, None)(ZIO.succeed("ok")).exit
        probeCount <- probes.get
      } yield assert(first)(failsWithA[BadRequestException]) &&
        assert(second)(failsWithA[BadRequestException]) &&
        assertTrue(probeCount == 2)
    },
    // Three live over-cap terms in a capacity-2 guard: the oldest leaves both the memo and the cache, so it is
    // probed again, while the newest is still refused from the memo.
    test("bounds the over-cap memo by the cache capacity, evicting the oldest live entry") {
      val terms = List("a*", "b*", "c*").map(LuceneQueryString(_))
      for {
        probes   <- Ref.make(0)
        guard    <- guardWith(countingOverCapProbe(probes), cacheCapacity = 2)
        _        <- ZIO.foreachDiscard(terms)(t => guard.guarded(t, None, None)(ZIO.unit).exit *> TestClock.adjust(1.milli))
        newest   <- guard.guarded(terms.last, None, None)(ZIO.unit).exit
        afterNew <- probes.get
        oldest   <- guard.guarded(terms.head, None, None)(ZIO.unit).exit
        afterOld <- probes.get
      } yield assert(newest)(failsWithA[BadRequestException]) &&
        assert(oldest)(failsWithA[BadRequestException]) &&
        assertTrue(afterNew == 3, afterOld == 4)
    },
    // Two entries that expired together are both dropped when a third is recorded, not only the earliest.
    test("the over-cap memo drops expired entries before evicting a live one") {
      val keys = List("a*", "b*", "c*").map(t => keyOf(LuceneQueryString(t)))
      for {
        memo    <- FulltextBreadthGuard.OverCapMemo.make(capacity = 2, ttl = cacheTtl)
        _       <- memo.record(keys(0)) *> TestClock.adjust(1.milli) *> memo.record(keys(1))
        _       <- TestClock.adjust(cacheTtl + 1.milli)
        _       <- memo.record(keys(2))
        size    <- memo.size
        live    <- memo.contains(keys(2))
        expired <- memo.contains(keys(1))
      } yield assertTrue(size == 1, live, !expired)
    },
    suite("bounded verdict wait")(
      test("returns a finished query's result after verdict-wait while the probe is still running") {
        for {
          probes <- Ref.make(0)
          gate   <- Promise.make[Nothing, Unit]
          guard  <- guardWith(gatedProbe(gate, probes, cap.toLong + 1))
          fib    <- guard.guarded(wildcard, None, None)(ZIO.succeed("ok")).fork
          _      <- verdictWaitRegistered
          before <- fib.poll
          _      <- TestClock.adjust(verdictWait)
          result <- fib.join
          open   <- gate.isDone
        } yield assertTrue(before.isEmpty, result == "ok", !open)
      },
      // The probe keeps running after the wait times out and caches its verdict: the next call is refused (its
      // query may start, awaiting the in-flight entry), and the call after that is refused from the memo.
      test("does not interrupt the probe when the verdict wait times out; its verdict serves later calls") {
        for {
          probes      <- Ref.make(0)
          gate        <- Promise.make[Nothing, Unit]
          interrupted <- Ref.make(false)
          guard       <- guardWith(key => gatedProbe(gate, probes, cap.toLong + 1)(key).onInterrupt(interrupted.set(true)))
          fib         <- guard.guarded(wildcard, None, None)(ZIO.succeed("ok")).fork
          _           <- verdictWaitRegistered *> TestClock.adjust(verdictWait)
          admitted    <- fib.join
          _           <- gate.succeed(())
          next        <- guard.guarded(wildcard, None, None)(ZIO.never).exit
          queryStarts <- Ref.make(0)
          last        <- guard.guarded(wildcard, None, None)(queryStarts.update(_ + 1).as("ok")).uninterruptible.exit
          wasInterr   <- interrupted.get
          startCount  <- queryStarts.get
          probeCount  <- probes.get
        } yield assertTrue(admitted == "ok", !wasInterr, startCount == 0, probeCount == 1) &&
          assert(next)(failsWithA[BadRequestException]) &&
          assert(last)(failsWithA[BadRequestException])
      },
      test("refuses a finished query when the over-cap verdict arrives within verdict-wait") {
        for {
          probes <- Ref.make(0)
          gate   <- Promise.make[Nothing, Unit]
          guard  <- guardWith(gatedProbe(gate, probes, cap.toLong + 1))
          fib    <- guard.guarded(wildcard, None, None)(ZIO.succeed("ok")).fork
          _      <- verdictWaitRegistered
          _      <- gate.succeed(())
          exit   <- fib.join.exit
        } yield assert(exit)(failsWithA[BadRequestException])
      },
      test("returns a failed query's failure at once, without waiting for the verdict") {
        for {
          probes <- Ref.make(0)
          gate   <- Promise.make[Nothing, Unit]
          guard  <- guardWith(gatedProbe(gate, probes, cap.toLong + 1))
          exit   <- guard.guarded(wildcard, None, None)(ZIO.fail(new QueryBoom)).exit
        } yield assert(exit)(failsWithA[QueryBoom])
      },
      // An uninterruptible caller (zio/zio#11044) must not turn the bounded wait back into an unbounded one.
      test("bounds the verdict wait for an uninterruptible caller too") {
        for {
          probes <- Ref.make(0)
          gate   <- Promise.make[Nothing, Unit]
          guard  <- guardWith(gatedProbe(gate, probes, cap.toLong + 1))
          fib    <- guard.guarded(wildcard, None, None)(ZIO.succeed("ok")).uninterruptible.fork
          _      <- verdictWaitRegistered *> TestClock.adjust(verdictWait)
          result <- fib.join
        } yield assertTrue(result == "ok")
      },
      test("interrupting a call interrupts its query but not the probe") {
        for {
          queryStarted     <- Promise.make[Nothing, Unit]
          queryInterrupted <- Promise.make[Nothing, Unit]
          probeStarted     <- Promise.make[Nothing, Unit]
          probeInterrupted <- Ref.make(false)
          gate             <- Promise.make[Nothing, Unit]
          guard            <- guardWith(_ =>
                     (probeStarted.succeed(()) *> gate.await.as(cap.toLong)).onInterrupt(probeInterrupted.set(true)),
                   )
          query           = (queryStarted.succeed(()) *> ZIO.never).onInterrupt(queryInterrupted.succeed(()))
          fib            <- guard.guarded(wildcard, None, None)(query).fork
          _              <- queryStarted.await *> probeStarted.await
          _              <- fib.interrupt
          _              <- queryInterrupted.await
          wasInterrupted <- probeInterrupted.get
        } yield assertTrue(!wasInterrupted)
      },
      // zio-cache runs a lookup in the fiber that first calls `get`; interrupting that fiber would interrupt every
      // other waiter on the entry, so the probe must not run in a request's fiber.
      test("interrupting one call does not interrupt another call awaiting the same probe") {
        for {
          probes       <- Ref.make(0)
          gate         <- Promise.make[Nothing, Unit]
          probeStarted <- Promise.make[Nothing, Unit]
          guard        <- guardWith(key => probeStarted.succeed(()) *> gatedProbe(gate, probes, cap.toLong + 1)(key))
          first        <- guard.guarded(wildcard, None, None)(ZIO.never).fork
          _            <- probeStarted.await
          second       <- guard.guarded(wildcard, None, None)(ZIO.never).fork
          _            <- first.interrupt
          _            <- gate.succeed(())
          exit         <- second.join.exit
          probeCount   <- probes.get
        } yield assert(exit)(failsWithA[BadRequestException]) && assertTrue(probeCount == 1)
      },
      // `join` on a probe interrupted by its scope closing would interrupt the request; the guard must fail open.
      test("fails open when the probe ends interrupted because the guard's scope is closed") {
        for {
          closed <- Scope.make
          _      <- closed.close(Exit.unit)
          guard  <- closed.extend[Any](guardWith(_ => ZIO.never))
          exit   <- guard.guarded(wildcard, None, None)(ZIO.succeed("ok")).exit
        } yield assertTrue(exit == Exit.succeed("ok"))
      },
      test("does not accumulate finalizers in the guard's scope per call") {
        val terms = List("a*", "b*", "c*").map(LuceneQueryString(_))
        for {
          scope  <- Scope.make
          guard  <- scope.extend[Any](guardWith(_ => ZIO.succeed(1L)))
          initial = scope.size
          _      <- ZIO.foreachDiscard(terms)(t => guard.guarded(t, None, None)(ZIO.succeed("ok")))
          after   = scope.size
          _      <- scope.close(Exit.unit)
        } yield assertTrue(after == initial)
      },
    ) @@ TestAspect.timeout(30.seconds),
  )
}
