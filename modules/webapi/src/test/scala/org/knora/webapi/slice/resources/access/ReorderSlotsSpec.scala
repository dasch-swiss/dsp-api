/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.access

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import dsp.errors.BadRequestException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.resources.access.AccessFixtures.*

@RunWith(classOf[DspZTestJUnitRunner])
class ReorderSlotsSpec extends ZIOSpecDefault {

  private given StringFormatter = StringFormatter.getInitializedTestInstance

  private def value(name: String) = ValueIri.unsafeFrom(s"http://rdfh.ch/0001/a-thing/values/$name")
  private val (a, b, c, h)        = (value("a"), value("b"), value("c"), value("hidden"))

  private def orders(slots: (ValueIri, Option[Int])*) = slots.map((v, o) => v.value -> o).toMap

  private val visibleA = intValue("visible-a", "V knora-admin:ProjectMember")
  private val visibleB = intValue("visible-b", "V knora-admin:ProjectMember")
  private val target   = ValuesToReorder(
    resource(Map(hasInteger -> Seq(visibleA, visibleB))),
    PropertyIri.unsafeFrom(hasInteger),
    Seq(visibleA, visibleB),
  )
  private val (va, vb) = (visibleA.valueIri, visibleB.valueIri)

  // The loader is never reached: a refused request fails before the order read.
  private val gate  = ObjectAccess(null, null, null)
  private val grant = new Grant(member, TestDataFactory.someProject, target, new Unfiltered(None))

  val spec: Spec[Any, Any] = suite("ReorderSlots")(
    suite("mismatch")(
      test("accepts each visible value once, in any order") {
        assertTrue(ReorderSlots.mismatch(target, List(vb, va)).isEmpty)
      },
      test("refuses a visible value listed twice") {
        assertTrue(ReorderSlots.mismatch(target, List(va, vb, va)).exists(_.getMessage.contains("Duplicate")))
      },
      test("names an omitted visible value and an extra request IRI, and nothing else") {
        val other = value("not-visible")
        val msg   = ReorderSlots.mismatch(target, List(va, other)).map(_.getMessage).getOrElse("")
        assertTrue(msg.contains(s"Missing: $vb."), msg.contains(s"Extra: $other."))
      },
      test("reorderSlots fails with 400 before it reads any order") {
        gate
          .reorderSlots(grant, List(va, va, vb))
          .exit
          .map(exit =>
            assertTrue(exit.causeOption.flatMap(_.failureOption).exists(_.isInstanceOf[BadRequestException])),
          )
      },
    ),
    suite("assign")(
      test("permutes the visible values among their own slots and leaves a hidden value's slot unused") {
        val assigned =
          ReorderSlots.assign(List(c, a, b), orders(a -> Some(0), h -> Some(1), b -> Some(2), c -> Some(5)))
        assertTrue(assigned == List(c -> 0, a -> 2, b -> 5))
      },
      test("keeps duplicate slots") {
        val assigned = ReorderSlots.assign(List(b, a, c), orders(a -> Some(3), b -> Some(3), c -> Some(4)))
        assertTrue(assigned == List(b -> 3, a -> 3, c -> 4))
      },
      test("gives a value without a slot a new one after the highest order, hidden values included") {
        val assigned = ReorderSlots.assign(List(b, a), orders(a -> Some(1), b -> None, h -> Some(9)))
        assertTrue(assigned == List(b -> 1, a -> 10))
      },
      test("counts a missing order as 0 when no value has a slot") {
        val assigned = ReorderSlots.assign(List(b, a), orders(a -> None, b -> None, h -> None))
        assertTrue(assigned == List(b -> 1, a -> 2))
      },
    ),
    test("query selects the non-deleted values of the property with their orders") {
      val query = ReorderSlots
        .query(
          ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing"),
          PropertyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri),
        )
        .sparql
      assertTrue(
        query.contains("<http://rdfh.ch/0001/a-thing> <http://www.knora.org/ontology/0001/anything#hasInteger> ?value"),
        query.contains("FILTER ( !bound(?isDeleted) || ?isDeleted = false )"),
        query.contains("OPTIONAL { ?value knora-base:valueHasOrder ?order . }"),
      )
    },
  )
}
