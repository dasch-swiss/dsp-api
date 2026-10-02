/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.runner.RunWith
import zio.*
import zio.test.*

import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.IRI
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.ValueHasXmlBackfillConfig
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.resources.repo.service.ValueHasXmlBackfillRepo
import org.knora.webapi.testservices.InMemoryTracing
import org.knora.webapi.testservices.SpanAssertions

/**
 * Guards the span contract of [[ValueHasXmlBackfillService.run]]: the run span, the select stage and one linked root span per batch are
 * emitted, the report is attached as bounded attributes on every exit, and neither a failure message nor a value IRI reaches any span. Needs no container.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ValueHasXmlBackfillServiceSpanSpec extends ZIOSpecDefault {

  private val root  = "value_has_xml_backfill"
  private val batch = s"$root.batch"

  private val project = KnoraProject(
    ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
    Shortname.unsafeFrom("shortname"),
    Shortcode.unsafeFrom("0001"),
    None,
    NonEmptyChunk(Description.unsafeFrom(StringLiteralV2.from("Test project"))),
    List.empty,
    None,
    SelfJoin.CannotJoin,
    None,
    Set.empty,
    Set.empty,
  )

  private val mappingIri = "http://rdfh.ch/standoff/mappings/shared"

  private final class StubRepo(
    candidates: Ref[Vector[IRI]],
    selectCount: Ref[Int],
    loadCount: Ref[Int],
    failSelectOn: Option[Int],
    failLoadOn: Option[Int],
    dieOnLoad: Boolean,
    blockedOnSelect: Option[Promise[Nothing, Unit]],
  ) extends ValueHasXmlBackfillRepo {

    override def selectCandidates(project: KnoraProject): Task[Seq[IRI]] =
      for {
        n <- selectCount.updateAndGet(_ + 1)
        _ <- ZIO.foreachDiscard(blockedOnSelect)(_.succeed(()) *> ZIO.never)
        _ <- ZIO.fail(new RuntimeException("secret user text")).when(failSelectOn.contains(n))
        c <- candidates.get
      } yield c

    override def loadStandoff(project: KnoraProject, valueIris: Seq[IRI]): Task[Map[IRI, Seq[(IRI, String)]]] =
      loadCount.updateAndGet(_ + 1).flatMap { n =>
        if (dieOnLoad) ZIO.die(new IllegalStateException("secret"))
        else if (failLoadOn.contains(n)) ZIO.fail(new RuntimeException("secret user text"))
        else
          ZIO.succeed {
            valueIris.flatMap { iri =>
              Seq(
                iri -> Seq(
                  KnoraBase.ValueHasString   -> "text",
                  KnoraBase.ValueHasMapping  -> mappingIri,
                  KnoraBase.ValueHasStandoff -> s"$iri/node",
                ),
                s"$iri/node" -> Seq(KnoraBase.StandoffTagHasStartIndex -> "0"),
              )
            }.toMap
          }
      }

    override def insertXml(project: KnoraProject, values: Seq[(IRI, String)]): Task[Unit] =
      candidates.update(_.filterNot(values.map(_._1).toSet))
  }

  private object StubRenderer extends ValueHasXmlRenderer {
    override def loadMapping(mappingIri: IRI): Task[MappingXMLtoStandoff] =
      ZIO.succeed(MappingXMLtoStandoff(Map.empty, None))

    override def render(value: StoredTextValue, mapping: MappingXMLtoStandoff): Task[Option[String]] =
      ZIO.some(s"<xml>${value.valueIri}</xml>")
  }

  private def service(
    failSelectOn: Option[Int],
    failLoadOn: Option[Int],
    dieOnLoad: Boolean,
    blockedOnSelect: Option[Promise[Nothing, Unit]],
  ) =
    for {
      tracing   <- ZIO.service[zio.telemetry.opentelemetry.tracing.Tracing]
      appConfig <- AppConfig.parseConfig.map(
                     _.copy(valueHasXmlBackfill = ValueHasXmlBackfillConfig(2, java.time.Duration.ofMillis(1), 100)),
                   )
      candidates <- Ref.make((1 to 3).map(i => s"http://rdfh.ch/0001/v$i").toVector)
      selects    <- Ref.make(0)
      loads      <- Ref.make(0)
      running    <- Ref.make(Option.empty[ProjectIri])
      repo        = StubRepo(candidates, selects, loads, failSelectOn, failLoadOn, dieOnLoad, blockedOnSelect)
    } yield new ValueHasXmlBackfillService(repo, StubRenderer, appConfig, tracing, running)

  private def runBackfill(
    failSelectOn: Option[Int] = None,
    failLoadOn: Option[Int] = None,
    dieOnLoad: Boolean = false,
  ) =
    for {
      svc   <- service(failSelectOn, failLoadOn, dieOnLoad, None)
      exit  <- svc.run(project).exit
      spans <- InMemoryTracing.finishedSpans
    } yield (exit, spans)

  private val runInterrupted =
    for {
      entered <- Promise.make[Nothing, Unit]
      svc     <- service(None, None, dieOnLoad = false, Some(entered))
      fiber   <- svc.run(project).fork
      _       <- entered.await
      _       <- fiber.interrupt
      spans   <- InMemoryTracing.finishedSpans
    } yield spans

  private val exitReasonKey = AttributeKey.stringKey(s"$root.exit_reason")

  private def exitReason(spans: Seq[SpanData], name: String): Option[String] =
    SpanAssertions.findSpan(spans, name).flatMap(span => Option(span.getAttributes.get(exitReasonKey)))

  private def runCounts(spans: Seq[SpanData], found: Long, rendered: Long, failed: Long): TestResult =
    SpanAssertions.hasAttribute(spans, root, AttributeKey.stringKey(s"$root.shortcode"), "0001") &&
      SpanAssertions.hasAttribute(spans, root, AttributeKey.longKey(s"$root.found"), found) &&
      SpanAssertions.hasAttribute(spans, root, AttributeKey.longKey(s"$root.rendered"), rendered) &&
      SpanAssertions.hasAttribute(spans, root, AttributeKey.longKey(s"$root.failed"), failed)

  private def runChildren(spans: Seq[SpanData]): Seq[SpanData] =
    SpanAssertions.findSpan(spans, root).toSeq.flatMap(r => spans.filter(_.getParentSpanId == r.getSpanId))

  private def batchSpansAreLinkedRoots(spans: Seq[SpanData]): TestResult = {
    val run     = SpanAssertions.findSpan(spans, root).get
    val batches = spans.filter(_.getName == batch)
    assertTrue(
      batches.size == 2,
      batches.forall(b => b.getTraceId != run.getTraceId && !b.getParentSpanContext.isValid),
      batches.forall(_.getLinks.asScala.map(_.getSpanContext.getSpanId).toSeq == Seq(run.getSpanId)),
      batches.forall(b =>
        batchChildren(spans, b).map(_.getName).toSet == Set("load", "render", "write").map(n => s"$root.$n"),
      ),
      batches.map(b => b.getAttributes.get(AttributeKey.longKey(s"$root.found"))) == Seq(2L, 1L),
      batches.forall(_.getAttributes.get(AttributeKey.stringKey(s"$root.shortcode")) == "0001"),
      batches.forall(_.getAttributes.get(AttributeKey.longKey(s"$root.failed")) == 0L),
      batches.map(b => b.getAttributes.get(AttributeKey.longKey(s"$root.rendered"))) == Seq(2L, 1L),
    )
  }

  private def batchChildren(spans: Seq[SpanData], b: SpanData): Seq[SpanData] =
    spans.filter(s => s.getParentSpanId == b.getSpanId && s.getTraceId == b.getTraceId)

  private def noValueIriInAttributes(spans: Seq[SpanData]): TestResult =
    assertTrue(
      !spans.exists(_.getAttributes.asMap.values.asScala.exists(_.toString.contains("http://rdfh.ch/"))),
    )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ValueHasXmlBackfillService spans")(
      test("a successful run leaves the root span unset, carries the report, and nests the stage spans") {
        runBackfill().map { case (exit, spans) =>
          val rootSpan = SpanAssertions.findSpan(spans, root)
          assertTrue(
            exit.isSuccess,
            rootSpan.exists(_.getStatus.getStatusCode != StatusCode.ERROR),
            exitReason(spans, root).isEmpty,
          ) &&
          runCounts(spans, 3L, 3L, 0L) &&
          SpanAssertions.hasAttribute(spans, root, AttributeKey.booleanKey(s"$root.stopped_early"), false) &&
          SpanAssertions.hasAttribute(spans, root, AttributeKey.stringKey(s"$root.stop_reason"), "NoCandidates") &&
          SpanAssertions.isParentChild(spans, root, s"$root.select") &&
          assertTrue(runChildren(spans).map(_.getName) == Seq(s"$root.select")) &&
          batchSpansAreLinkedRoots(spans) &&
          noValueIriInAttributes(spans)
        }
      },
      test("a typed failure yields a sanitized ERROR status and leaks no message") {
        runBackfill(failSelectOn = Some(1)).map { case (exit, spans) =>
          assertTrue(
            exit.isFailure,
            !spans.exists(_.toString.contains("secret user text")),
            spans.forall(_.getEvents.isEmpty),
            exitReason(spans, root).isEmpty,
          ) &&
          SpanAssertions.hasErrorStatus(spans, root) &&
          SpanAssertions.hasStatusDescription(spans, root, s"$root: RuntimeException") &&
          SpanAssertions.hasAttribute(spans, root, AttributeKey.stringKey("error.type"), "RuntimeException") &&
          assertTrue(
            spans
              .filter(_.getName == s"$root.select")
              .lastOption
              .exists(_.getStatus.getDescription == s"$root.select: RuntimeException"),
          ) &&
          noValueIriInAttributes(spans)
        }
      },
      test("a failed load in the second batch leaves the run span with the counts of the first batch") {
        runBackfill(failLoadOn = Some(2)).map { case (exit, spans) =>
          assertTrue(exit.isFailure, !spans.exists(_.toString.contains("secret user text"))) &&
          runCounts(spans, 2L, 2L, 0L) &&
          SpanAssertions.hasAttribute(spans, root, AttributeKey.booleanKey(s"$root.stopped_early"), false) &&
          SpanAssertions.hasNoAttributeKey(spans, root, AttributeKey.stringKey(s"$root.stop_reason")) &&
          SpanAssertions.hasErrorStatus(spans, root) &&
          assertTrue(spans.count(_.getName == batch) == 2) &&
          noValueIriInAttributes(spans)
        }
      },
      test("a defect yields the sanitized defect status and leaks no message") {
        runBackfill(dieOnLoad = true).map { case (exit, spans) =>
          assertTrue(
            exit.causeOption.exists(_.dieOption.exists(_.getMessage == "secret")),
            !spans.exists(_.toString.contains("secret")),
            spans.forall(_.getEvents.isEmpty),
            exitReason(spans, root).isEmpty,
          ) &&
          SpanAssertions.hasErrorStatus(spans, root) &&
          SpanAssertions.hasStatusDescription(spans, root, s"$root: defect") &&
          SpanAssertions.hasStatusDescription(spans, s"$root.load", s"$root.load: defect") &&
          runCounts(spans, 0L, 0L, 0L) &&
          SpanAssertions.hasNoAttributeKey(spans, root, AttributeKey.stringKey(s"$root.stop_reason")) &&
          noValueIriInAttributes(spans)
        }
      },
      test("an interrupted run marks the root and the interrupted stage span with exit_reason interrupted") {
        runInterrupted.map { spans =>
          assertTrue(
            exitReason(spans, root).contains("interrupted"),
            exitReason(spans, s"$root.select").contains("interrupted"),
          ) &&
          SpanAssertions.hasStatusDescription(spans, root, "interrupted") &&
          runCounts(spans, 0L, 0L, 0L) &&
          noValueIriInAttributes(spans)
        }
      },
    ).provideSomeLayer[TestEnvironment](InMemoryTracing.layer) @@ TestAspect.withLiveClock
}
