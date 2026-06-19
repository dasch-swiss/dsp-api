/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import io.opentelemetry.api
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import sttp.client4.UriContext
import zio.*
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test.*

import scala.jdk.CollectionConverters.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.testservices.InMemoryTracing
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.SpanAssertions
import org.knora.webapi.testservices.TestApiClient

/**
 * Full-path span-topology test for the Gravsearch instrumentation, exercised end-to-end over real HTTP
 * against a real triplestore. The application under test runs with an in-memory span exporter (injected via
 * the overridable [[E2EZSpec.otelLayer]] seam) so the emitted spans can be asserted.
 *
 * Verifies the span-topology acceptance criteria (REQ-1.1/1.2/1.4/1.7/1.9):
 *   - a full-path search emits the root `gravsearch` span + all 7 stage spans, each a child of the root,
 *     and the root nests under the HTTP SERVER span;
 *   - the triplestore CLIENT span (sttp backend) nests under the `prequery.execute` stage span;
 *   - an empty-result search omits the three main-query spans;
 *   - the count path emits exactly the four prequery-side stages.
 */
object SearchResponderV2GravsearchSpanE2ESpec extends E2EZSpec {

  // Held externally so the test can read finished spans directly (the exporter is not exposed in the env).
  private lazy val exporter: InMemorySpanExporter = InMemorySpanExporter.create()

  override protected def otelLayer: ULayer[api.OpenTelemetry & Tracing & ContextStorage] =
    InMemoryTracing.layerFor(exporter)

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
  )

  private val stageSpans = List(
    "gravsearch.type_inspection",
    "gravsearch.prequery.generate",
    "gravsearch.prequery.execute",
    "gravsearch.mainquery.generate",
    "gravsearch.mainquery.execute",
    "gravsearch.result_transform",
  )

  private val shapeKey = AttributeKey.stringKey("gravsearch.query.shape")

  private def bookByTitleQuery(title: String): String =
    s"""PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
       |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
       |CONSTRUCT {
       |    ?book knora-api:isMainResource true .
       |    ?book incunabula:title ?title .
       |} WHERE {
       |    ?book a incunabula:book .
       |    ?book a knora-api:Resource .
       |    ?book incunabula:title ?title .
       |    incunabula:title knora-api:objectType xsd:string .
       |    ?title a xsd:string .
       |    FILTER(?title = "$title")
       |}""".stripMargin

  private val existingTitle = "Zeitglöcklein des Lebens und Leidens Christi"

  private val readSpans: UIO[Chunk[SpanData]] =
    ZIO.succeed(Chunk.fromIterable(exporter.getFinishedSpanItems.asScala.toList))

  /** Reset the exporter, run the request, give the synchronous exporter a moment, then read the spans. */
  private def spansAfter[R](label: String)(request: ZIO[R, Throwable, Any]): ZIO[R, Throwable, Chunk[SpanData]] =
    for {
      _     <- ZIO.succeed(exporter.reset())
      _     <- request
      _     <- ZIO.sleep(1.second)
      spans <- readSpans
      _     <- Console
             .printLine(s"[GRAVSEARCH-SPAN-DIAG] $label: ${spans.size} spans: ${spans.map(_.getName).mkString(", ")}")
             .orDie
    } yield spans

  /** `child` exists and its direct parent is a span of the given kind. */
  private def nestsUnderKind(spans: Seq[SpanData], child: String, kind: SpanKind): Boolean =
    (for {
      c <- spans.find(_.getName == child)
      p <- spans.find(_.getSpanId == c.getParentSpanId)
    } yield p.getKind == kind).getOrElse(false)

  /** A CLIENT-kind span exists whose direct parent is the span named `parent`. */
  private def clientSpanNestsUnder(spans: Seq[SpanData], parent: String): Boolean =
    spans.exists(s =>
      s.getKind == SpanKind.CLIENT &&
        spans.exists(p => p.getName == parent && p.getSpanId == s.getParentSpanId),
    )

  override val e2eSpec = suite("SearchResponderV2 Gravsearch span topology")(
    test(
      "a full-path Gravsearch emits the root + 7 stage spans, each a child of the root, nested under the server span",
    ) {
      for {
        spans <-
          spansAfter("full-path")(
            TestApiClient.postSparql(uri"/v2/searchextended", bookByTitleQuery(existingTitle)).flatMap(_.assert200),
          )
      } yield assertTrue(stageSpans.forall(s => spans.exists(_.getName == s))) &&
        SpanAssertions.hasSpan(spans, "gravsearch") &&
        SpanAssertions.hasSpan(spans, "gravsearch.parse") &&
        SpanAssertions.isParentChild(spans, "gravsearch", "gravsearch.parse") &&
        SpanAssertions.isParentChild(spans, "gravsearch", "gravsearch.type_inspection") &&
        SpanAssertions.isParentChild(spans, "gravsearch", "gravsearch.prequery.execute") &&
        SpanAssertions.isParentChild(spans, "gravsearch", "gravsearch.mainquery.execute") &&
        SpanAssertions.isParentChild(spans, "gravsearch", "gravsearch.result_transform") &&
        SpanAssertions.hasAttributeKey(spans, "gravsearch", shapeKey) &&
        assertTrue(nestsUnderKind(spans, "gravsearch", SpanKind.SERVER))
    },
    test("the triplestore CLIENT span nests under the prequery.execute and mainquery.execute stage spans (REQ-1.4)") {
      for {
        spans <-
          spansAfter("client-nesting")(
            TestApiClient.postSparql(uri"/v2/searchextended", bookByTitleQuery(existingTitle)).flatMap(_.assert200),
          )
      } yield assertTrue(
        clientSpanNestsUnder(spans, "gravsearch.prequery.execute"),
        clientSpanNestsUnder(spans, "gravsearch.mainquery.execute"),
      )
    },
    test("an empty-result Gravsearch omits the main-query and result-transform spans (REQ-1.9)") {
      for {
        spans <- spansAfter("empty-result")(
                   TestApiClient
                     .postSparql(uri"/v2/searchextended", bookByTitleQuery("a title that matches no book at all"))
                     .flatMap(_.assert200),
                 )
      } yield SpanAssertions.hasSpan(spans, "gravsearch.prequery.execute") &&
        SpanAssertions.hasNoSpan(spans, "gravsearch.mainquery.generate") &&
        SpanAssertions.hasNoSpan(spans, "gravsearch.mainquery.execute") &&
        SpanAssertions.hasNoSpan(spans, "gravsearch.result_transform")
    },
    test("a count Gravsearch emits exactly the four prequery-side stages (REQ-1.7)") {
      for {
        spans <- spansAfter("count")(
                   TestApiClient
                     .postSparql(uri"/v2/searchextended/count", bookByTitleQuery(existingTitle))
                     .flatMap(_.assert200),
                 )
      } yield SpanAssertions.hasSpan(spans, "gravsearch") &&
        SpanAssertions.hasSpan(spans, "gravsearch.parse") &&
        SpanAssertions.hasSpan(spans, "gravsearch.type_inspection") &&
        SpanAssertions.hasSpan(spans, "gravsearch.prequery.generate") &&
        SpanAssertions.hasSpan(spans, "gravsearch.prequery.execute") &&
        SpanAssertions.hasNoSpan(spans, "gravsearch.mainquery.generate") &&
        SpanAssertions.hasNoSpan(spans, "gravsearch.mainquery.execute") &&
        SpanAssertions.hasNoSpan(spans, "gravsearch.result_transform")
    },
  )
}
