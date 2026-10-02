/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.slice.resources.service.ValueHasXmlBackfill.RunState
import org.knora.webapi.slice.resources.service.ValueHasXmlBackfill.StopReason

@RunWith(classOf[DspZTestJUnitRunner])
class ValueHasXmlBackfillSpec extends ZIOSpecDefault {

  private def value(iri: String, nodes: String*): Map[String, Seq[(String, String)]] =
    Map(
      iri -> (Seq(
        KnoraBase.ValueHasString  -> "text",
        KnoraBase.ValueHasMapping -> "mapping",
      ) ++ nodes.map(KnoraBase.ValueHasStandoff -> _)),
    )

  private def node(iri: String, start: Int, extra: (String, String)*): Map[String, Seq[(String, String)]] =
    Map(iri -> (Seq(KnoraBase.StandoffTagHasStartIndex -> start.toString) ++ extra))

  private val groupByValueSuite = suite("groupByValue")(
    test("each value gets only its own nodes") {
      val statements = value("v1", "n1") ++ value("v2", "n2") ++ node("n1", 0) ++ node("n2", 0)
      val result     = ValueHasXmlBackfill.groupByValue(statements, Seq("v1", "v2"))
      assertTrue(
        result("v1").map(_.standoffNodes.keySet) == Right(Set("n1")),
        result("v2").map(_.standoffNodes.keySet) == Right(Set("n2")),
      )
    },
    test("an internal reference keeps both nodes") {
      val statements =
        value("v1", "n1", "n2") ++ node("n1", 0, KnoraBase.StandoffTagHasLink -> "n2") ++ node("n2", 1)
      val result = ValueHasXmlBackfill.groupByValue(statements, Seq("v1"))
      assertTrue(result("v1").map(_.standoffNodes.keySet) == Right(Set("n1", "n2")))
    },
    test("a node with a negative start index is excluded") {
      val statements = value("v1", "n1", "n2") ++ node("n1", 0) ++ node("n2", -1)
      val result     = ValueHasXmlBackfill.groupByValue(statements, Seq("v1"))
      assertTrue(result("v1").map(_.standoffNodes.keySet) == Right(Set("n1")))
    },
    test("a linked standoff node missing from the response is Left, so no partial XML is rendered") {
      val statements = value("v1", "n1", "n2") ++ node("n1", 0)
      assertTrue(ValueHasXmlBackfill.groupByValue(statements, Seq("v1"))("v1").isLeft)
    },
    test("a requested value missing from the response is Left") {
      assertTrue(ValueHasXmlBackfill.groupByValue(Map.empty, Seq("v1"))("v1").isLeft)
    },
    test("a value without mapping is Left") {
      val statements = Map("v1" -> Seq(KnoraBase.ValueHasString -> "text"))
      assertTrue(ValueHasXmlBackfill.groupByValue(statements, Seq("v1"))("v1").isLeft)
    },
    test("a value without valueHasString is Left") {
      val statements = Map("v1" -> Seq(KnoraBase.ValueHasMapping -> "mapping"))
      assertTrue(ValueHasXmlBackfill.groupByValue(statements, Seq("v1"))("v1").isLeft)
    },
    test("text value type and fields are read") {
      val statements = Map(
        "v1" -> Seq(
          KnoraBase.ValueHasString   -> "text",
          KnoraBase.ValueHasMapping  -> "mapping",
          KnoraBase.HasTextValueType -> "type",
          KnoraBase.ValueHasStandoff -> "n1",
        ),
      ) ++ node("n1", 0)
      assertTrue(
        ValueHasXmlBackfill.groupByValue(statements, Seq("v1"))("v1") ==
          Right(
            StoredTextValue(
              "v1",
              "text",
              "mapping",
              Some("type"),
              Map("n1" -> Map(KnoraBase.StandoffTagHasStartIndex -> "0")),
            ),
          ),
      )
    },
    test("a value whose only standoff nodes have a negative start index is Left") {
      val statements = value("v1", "n1") ++ node("n1", -1)
      assertTrue(ValueHasXmlBackfill.groupByValue(statements, Seq("v1"))("v1").isLeft)
    },
  )

  private val reportSuite = suite("ValueHasXmlBackfillReport")(
    test("zero is the identity") {
      val r = ValueHasXmlBackfillReport(1, 2, 3, true)
      assertTrue(r + ValueHasXmlBackfillReport.zero == r, ValueHasXmlBackfillReport.zero + r == r)
    },
    test("+ sums counts and ors stoppedEarly") {
      val sum = ValueHasXmlBackfillReport(1, 2, 3, false) + ValueHasXmlBackfillReport(4, 5, 6, true)
      assertTrue(sum == ValueHasXmlBackfillReport(5, 7, 9, true))
    },
  )

  private val nextBatchSuite = suite("nextBatch")(
    test("drops failed, keeps order and caps at batchSize") {
      assertTrue(ValueHasXmlBackfill.nextBatch(Seq("a", "b", "c", "d", "e"), Set("b"), 3) == Seq("a", "c", "d"))
    },
    test("returns fewer than batchSize when fewer candidates are left") {
      assertTrue(ValueHasXmlBackfill.nextBatch(Seq("a", "b"), Set.empty, 3) == Seq("a", "b"))
    },
    test("returns nothing when every candidate failed") {
      assertTrue(ValueHasXmlBackfill.nextBatch(Seq("a", "b"), Set("a", "b"), 3).isEmpty)
    },
  )

  private val stopReasonSuite = {
    def stop(state: RunState, batch: Seq[String], max: Int = 5) = ValueHasXmlBackfill.stopReason(state, batch, max)
    suite("stopReason")(
      test("empty batch is NoCandidates") {
        assertTrue(stop(RunState.zero, Seq.empty) == Some(StopReason.NoCandidates))
      },
      test("candidates that all failed before yield NoCandidates after nextBatch") {
        val state = RunState.zero.copy(failed = Set("a"))
        assertTrue(
          stop(state, ValueHasXmlBackfill.nextBatch(Seq("a"), state.failed, 10)) == Some(StopReason.NoCandidates),
        )
      },
      test("failed.size == maxFailures is MaxFailures") {
        val state = RunState.zero.copy(failed = Set("a", "b"))
        assertTrue(stop(state, Seq("c"), 2) == Some(StopReason.MaxFailures))
      },
      test("an empty batch is NoCandidates even when maxFailures is reached") {
        val state = RunState.zero.copy(failed = Set("a", "b"))
        assertTrue(stop(state, Seq.empty, 2) == Some(StopReason.NoCandidates))
      },
      test("failed.size above maxFailures is MaxFailures") {
        val state = RunState.zero.copy(failed = Set("a", "b", "c"))
        assertTrue(stop(state, Seq("d"), 2) == Some(StopReason.MaxFailures))
      },
      test("a batch that overlaps the previous batch only in part is None") {
        val state = RunState.zero.copy(previousBatch = Set("a", "b"))
        assertTrue(stop(state, Seq("a")).isEmpty, stop(state, Seq("a", "b", "c")).isEmpty)
      },
      test("the first batch is None") {
        assertTrue(stop(RunState.zero, Seq("a")).isEmpty)
      },
      test("the same IRI set as the previous batch is Stalled") {
        val state = RunState.zero.copy(previousBatch = Set("a", "b"))
        assertTrue(stop(state, Seq("b", "a")) == Some(StopReason.Stalled))
      },
      test("a fresh batch is None") {
        val state = RunState.zero.copy(previousBatch = Set("a"))
        assertTrue(stop(state, Seq("b")).isEmpty)
      },
      test("stoppedEarly is false only for NoCandidates") {
        assertTrue(
          !StopReason.NoCandidates.stoppedEarly,
          StopReason.MaxFailures.stoppedEarly,
          StopReason.Stalled.stoppedEarly,
        )
      },
    )
  }

  override def spec: Spec[Any, Any] =
    suite("ValueHasXmlBackfill")(groupByValueSuite, reportSuite, nextBatchSuite, stopReasonSuite)
}
