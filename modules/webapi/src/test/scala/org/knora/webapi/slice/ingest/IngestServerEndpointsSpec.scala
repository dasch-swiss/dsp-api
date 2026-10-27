/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest

import org.junit.runner.RunWith
import zio.*
import zio.http.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.ingest.IngestModuleSpec.serve

@RunWith(classOf[DspZTestJUnitRunner])
class IngestServerEndpointsSpec extends ZIOSpecDefault {

  /** The routes of the standalone ingest service, each of which dsp-api serves under `/ingest/`. */
  private val standaloneRoutes = List(
    "GET /info",
    "GET /health",
    "GET /metrics",
    "GET /projects",
    "GET /projects/{shortcode}",
    "GET /projects/{shortcode}/checksumreport",
    "DELETE /projects/{shortcode}/erase",
    "GET /projects/{shortcode}/assets/{assetId}",
    "GET /projects/{shortcode}/assets/{assetId}/original",
    "POST /projects/{shortcode}/assets/ingest/{filename}",
    "POST /projects/{shortcode}/bulk-ingest",
    "POST /projects/{shortcode}/bulk-ingest/finalize",
    "GET /projects/{shortcode}/bulk-ingest/mapping.csv",
    "POST /projects/{shortcode}/export",
    "POST /projects/{shortcode}/import",
    "POST /projects/{shortcode}/bulk-ingest/ingest/{file}",
    "POST /maintenance/{name}",
    "GET /maintenance/needs-top-left-correction",
    "GET /maintenance/was-top-left-correction-applied",
    "POST /report/asset-overview",
  )

  val spec = suite("IngestServerEndpoints")(
    test("serves every ingest endpoint under /ingest/") {
      for {
        endpoints <- ZIO.serviceWith[IngestServerEndpoints](_.serverEndpoints.map(_.endpoint))
        templates  = endpoints.map(e => e.method.fold("")(_.method + " ") + e.showPathTemplate(showQueryParam = None))
      } yield assertTrue(
        templates.forall(_.matches("[A-Z]+ /ingest/.*")),
        templates.sorted == standaloneRoutes.map(_.replace(" /", " /ingest/")).sorted,
      )
    },
    test("answers /ingest/info and not /info") {
      for {
        ingest <- serve(Request.get(URL(Path.root / "ingest" / "info")))
        root   <- serve(Request.get(URL(Path.root / "info")))
      } yield assertTrue(ingest.status == Status.Ok, root.status == Status.NotFound)
    },
  ).provide(IngestModuleSpec.layer)
}
