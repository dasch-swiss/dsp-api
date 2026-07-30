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

  override def spec: Spec[TestEnvironment, Any] = suite("FulltextBreadthGuard")(
    // An over-cap probe refuses the search and interrupts the query; ZIO.never as the query proves it is
    // interrupted rather than awaited (the raced-guard property — a serial guard would hang here).
    test("refuses a query whose probed breadth exceeds the cap") {
      for {
        guard <- guardWith(_ => ZIO.succeed(cap.toLong + 1))
        exit  <- guard.guarded(wildcard, None, None, None)(ZIO.never).exit
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
    // The raced guard must not serialise: an admitted query returns as soon as it succeeds, without waiting for
    // the probe. The probe here never returns a breadth, so a serial guard would block on it forever.
    test("an admitted query does not wait for the probe to finish (raced, not serial)") {
      for {
        guard  <- guardWith(_ => ZIO.never) // probe never completes
        result <- guard.guarded(wildcard, None, None, None)(ZIO.succeed("ok"))
      } yield assertTrue(result == "ok")
    },
    // A refused search must free its in-flight query, not leave it running until it times out. The query's
    // onInterrupt fulfils the promise; awaiting it proves the loser was actually interrupted.
    test("a refused query interrupts the in-flight real query (frees the request)") {
      for {
        interrupted <- Promise.make[Nothing, Unit]
        guard       <- guardWith(_ => ZIO.succeed(cap.toLong + 1))
        query        = ZIO.never.onInterrupt(interrupted.succeed(()))
        exit        <- guard.guarded(wildcard, None, None, None)(query).exit
        _           <- interrupted.await
      } yield assert(exit)(failsWithA[BadRequestException])
    },
  )
}
