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
    // The probe runs before the query and refuses over-cap, so the query is never reached — ZIO.never proves it.
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
  )
}
