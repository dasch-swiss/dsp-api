/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.runner.RunWith
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test.*

import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.SchemaRendering
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.anonymousUser
import org.knora.webapi.testservices.InMemoryTracing
import org.knora.webapi.testservices.SpanAssertions

@RunWith(classOf[DspZTestJUnitRunner])
class SearchResponderV2GravsearchSpanE2ESpec extends E2EZSpec {

  // Held externally so the test can read finished spans directly (the exporter is not exposed in the env).
  private lazy val exporter: InMemorySpanExporter = InMemorySpanExporter.create()

  override protected def otelLayer = InMemoryTracing.layerFor(exporter)

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

  /**
   * Reset the exporter, run the responder call, then read the captured spans. The exporter is a
   * synchronous [[SimpleSpanProcessor]], so spans are visible as soon as the awaited request ends;
   * poll until they appear (bounded) instead of a fixed delay - prompt on success, and resilient to
   * a span finishing on a detached fiber under CI load without paying a flat 500ms per scenario.
   * Every scenario expects at least one span, so `nonEmpty` is a valid settle condition.
   */
  private def spansAfter[R](request: ZIO[R, Throwable, Any]): ZIO[R, Throwable, Chunk[SpanData]] =
    for {
      _     <- ZIO.succeed(exporter.reset())
      _     <- request
      spans <- readSpans.repeatUntil(_.nonEmpty).timeoutTo(Chunk.empty[SpanData])(identity)(2.seconds)
    } yield spans

  private def runGravsearch(query: String) =
    ZIO.serviceWithZIO[SearchResponderV2](_.gravsearchV2(query, SchemaRendering.default, anonymousUser, None))

  private def runGravsearchCount(query: String) =
    ZIO.serviceWithZIO[SearchResponderV2](_.gravsearchCountV2(query, anonymousUser, None))

  /** A CLIENT-kind span exists whose direct parent is the span named `parent`. */
  private def clientSpanNestsUnder(spans: Seq[SpanData], parent: String): Boolean =
    spans.exists(s =>
      s.getKind == SpanKind.CLIENT &&
        spans.exists(p => p.getName == parent && p.getSpanId == s.getParentSpanId),
    )

  override val e2eSpec = suite("SearchResponderV2 Gravsearch span topology")(
    test("a full-path Gravsearch emits the root + 7 stage spans, each a child of the root") {
      for {
        spans <- spansAfter(runGravsearch(bookByTitleQuery(existingTitle)))
      } yield assertTrue(stageSpans.forall(s => spans.exists(_.getName == s))) &&
        SpanAssertions.hasSpan(spans, "gravsearch") &&
        SpanAssertions.hasSpan(spans, "gravsearch.parse") &&
        SpanAssertions.isParentChild(spans, "gravsearch", "gravsearch.parse") &&
        SpanAssertions.isParentChild(spans, "gravsearch", "gravsearch.type_inspection") &&
        SpanAssertions.isParentChild(spans, "gravsearch", "gravsearch.prequery.execute") &&
        SpanAssertions.isParentChild(spans, "gravsearch", "gravsearch.mainquery.execute") &&
        SpanAssertions.isParentChild(spans, "gravsearch", "gravsearch.result_transform") &&
        SpanAssertions.hasAttributeKey(spans, "gravsearch", shapeKey)
    },
    test("the gravsearch root nests under an active SERVER-kind span (REQ-1.1)") {
      for {
        spans <- spansAfter(
                   ZIO.serviceWithZIO[Tracing](
                     _.span("test.server", SpanKind.SERVER)(runGravsearch(bookByTitleQuery(existingTitle))),
                   ),
                 )
      } yield SpanAssertions.hasSpan(spans, "test.server") &&
        SpanAssertions.isParentChild(spans, "test.server", "gravsearch")
    },
    test("the triplestore CLIENT span nests under the prequery.execute and mainquery.execute stage spans (REQ-1.4)") {
      for {
        spans <- spansAfter(runGravsearch(bookByTitleQuery(existingTitle)))
      } yield assertTrue(
        clientSpanNestsUnder(spans, "gravsearch.prequery.execute"),
        clientSpanNestsUnder(spans, "gravsearch.mainquery.execute"),
      )
    },
    test("an empty-result Gravsearch omits the main-query and result-transform spans (REQ-1.9)") {
      for {
        spans <- spansAfter(runGravsearch(bookByTitleQuery("a title that matches no book at all")))
      } yield SpanAssertions.hasSpan(spans, "gravsearch.prequery.execute") &&
        SpanAssertions.hasNoSpan(spans, "gravsearch.mainquery.generate") &&
        SpanAssertions.hasNoSpan(spans, "gravsearch.mainquery.execute") &&
        SpanAssertions.hasNoSpan(spans, "gravsearch.result_transform")
    },
    test("a count Gravsearch emits exactly the four prequery-side stages (REQ-1.7)") {
      for {
        spans <- spansAfter(runGravsearchCount(bookByTitleQuery(existingTitle)))
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
