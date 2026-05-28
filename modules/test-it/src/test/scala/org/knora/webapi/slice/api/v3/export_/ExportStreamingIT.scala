/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.ontology.repo.service.OntologyCache

/**
 * Integration test that exports a realistic-size resource class (incunabula `page`, 4024 resources) against a real
 * Fuseki testcontainer and times the streaming pipeline across a `(batchSize, parallelism)` matrix.
 *
 * Two purposes:
 *   1. Tuning vehicle -- emits per-combo timings so we can pick production defaults from data, not guesses.
 *   2. Regression guard -- the matrix asserts that the production combo (5, 500) completes within a generous wall-time
 *      budget and produces the expected number of CSV rows.
 *
 * Excluded from default CI because it is multi-minute (Fuseki testcontainer startup + incunabula bulk-load + matrix).
 * Run on demand with: `sbt "test-it/testOnly *ExportStreamingIT"`.
 */
object ExportStreamingIT extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = SharedTestDataADM.incunabulaRdfOntologyAndData

  private val pageClassIri: ResourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0803/incunabula#page".toSmartIri.toComplexSchema)

  private val matrix: List[(Int, Int)] = for {
    parallelism <- List(1, 2, 5, 10, 20)
    batchSize   <- List(100, 250, 500, 1000, 2000)
  } yield (parallelism, batchSize)

  // Generous regression-guard threshold -- actual run is expected to be well under this. Tightening would invite
  // flake on busy CI runners; the goal is to catch a 10x regression, not enforce a budget.
  private val regressionGuardWallTime: Duration = 5.minutes

  override val e2eSpec = suite("Export streaming tuning IT")(
    test("sweep (parallelism, batchSize) matrix against incunabula:page") {
      for {
        _       <- ZIO.serviceWithZIO[OntologyCache](_.refreshCache())
        project <- ZIO
                     .serviceWithZIO[KnoraProjectService](_.findById(SharedTestDataADM.incunabulaProjectIri))
                     .map(_.get)
        exportService <- ZIO.service[ExportService]

        runOnce = (parallelism: Int, batchSize: Int) =>
                    for {
                      t0    <- Clock.nanoTime
                      bytes <- exportService
                                 .exportResources(
                                   project,
                                   pageClassIri,
                                   selectedProperties = List.empty,
                                   requestingUser = SharedTestDataADM.incunabulaProjectAdminUser,
                                   language = LanguageCode.EN,
                                   includeIris = false,
                                   includeArkUrls = false,
                                   batchSize = batchSize,
                                   parallelism = parallelism,
                                 )
                                 .flatMap(_.runCollect)
                      t1    = java.lang.System.nanoTime()
                      lines = new String(bytes.toArray, java.nio.charset.StandardCharsets.UTF_8).count(_ == '\n')
                    } yield (parallelism, batchSize, java.time.Duration.ofNanos(t1 - t0), lines)

        // Warmup: prime Fuseki query plan cache + JVM JIT for the production combo. Discarded.
        _ <- runOnce(5, 500)

        results <- ZIO.foreach(matrix) { case (p, b) => runOnce(p, b) }

        _ <- ZIO.foreach(results) { case (p, b, d, lines) =>
               Console.printLine(
                 f"[ExportStreamingIT] parallelism=$p%2d  batchSize=$b%4d  ${d.toMillis}%6d ms  ($lines lines)",
               )
             }

        // All combos must produce the same number of CSV lines (1 header + N data rows, deterministic order).
        lineCounts = results.map(_._4).distinct
        // Production combo (5, 500) is the regression guard.
        production = results.find { case (p, b, _, _) => p == 5 && b == 500 }.get
      } yield assertTrue(
        lineCounts.size == 1,                                // all combos agree on row count
        lineCounts.head > 1000,                              // sanity: incunabula:page has 4024 instances
        production._3.compareTo(regressionGuardWallTime) < 0, // production combo within budget
      )
    },
  )
}
