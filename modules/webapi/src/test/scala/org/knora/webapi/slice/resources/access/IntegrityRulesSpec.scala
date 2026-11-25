/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.access

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.model.Cardinality.*
import org.knora.webapi.slice.resources.access.AccessFixtures.*

@RunWith(classOf[DspZTestJUnitRunner])
class IntegrityRulesSpec extends ZIOSpecDefault {

  private val property = PropertyIri.unsafeFrom(hasInteger)
  private val hidden   = intValue("hidden", "CR knora-admin:Creator")
  private val visible  = intValue("visible", "V knora-admin:ProjectMember")

  private def removable(cardinality: Cardinality, onlyHistory: Boolean, values: Seq[ReadValueV2]) =
    IntegrityRules.valueRemovable(resourceIri, property, cardinality, onlyHistory)(
      new Unfiltered(Some(resource(Map(hasInteger -> values)))),
    )

  private def addable(cardinality: Cardinality, values: Seq[ReadValueV2]) =
    IntegrityRules.valueAddable(resourceIri, property, cardinality)(
      new Unfiltered(Some(resource(Map(hasInteger -> values)))),
    )

  private val hasOtherThingValueProperty = PropertyIri.unsafeFrom(hasOtherThingValue)
  private val target                     = ResourceIri.unsafeFrom("http://rdfh.ch/0001/link-target")

  private def duplicate(values: ReadValueV2*) =
    IntegrityRules.noDuplicateLink(resourceIri, hasOtherThingValueProperty, target)(
      new Unfiltered(Some(resource(Map(hasOtherThingValue -> values)))),
    )

  val spec: Spec[Any, Any] = suite("IntegrityRules")(valueRemovableSuite, valueAddableSuite, noDuplicateLinkSuite)

  private def noDuplicateLinkSuite = suite("noDuplicateLink")(
    test("refuses a second link to the same target, counting links the caller cannot see") {
      assertTrue(
        duplicate(linkValue("hidden", "CR knora-admin:Creator", target))
          .contains(IntegrityViolation.DuplicateLink(resourceIri, hasOtherThingValueProperty, target)),
      )
    },
    test("allows a link when only a deleted link to the target exists") {
      assertTrue(duplicate(linkValue("old", "CR knora-admin:Creator", target, deleted = true)).isEmpty)
    },
    test("allows a link to the same target through another property") {
      val unfiltered =
        new Unfiltered(Some(resource(Map(hasInteger -> Seq(linkValue("l", "CR knora-admin:Creator", target))))))
      assertTrue(IntegrityRules.noDuplicateLink(resourceIri, hasOtherThingValueProperty, target)(unfiltered).isEmpty)
    },
    test("allows a link to another target") {
      val other = ResourceIri.unsafeFrom("http://rdfh.ch/0001/other-target")
      assertTrue(duplicate(linkValue("other", "CR knora-admin:Creator", other)).isEmpty)
    },
  )

  private def valueRemovableSuite = suite("valueRemovable")(
    test("refuses removing the only value a required cardinality needs") {
      assertTrue(
        removable(AtLeastOne, onlyHistory = false, Seq(visible))
          .contains(IntegrityViolation.CardinalityViolated(resourceIri, property)),
      )
    },
    test("counts values hidden from the caller") {
      assertTrue(removable(AtLeastOne, onlyHistory = false, Seq(visible, hidden)).isEmpty)
    },
    test("allows removing the last value of an optional property") {
      assertTrue(removable(Unbounded, onlyHistory = false, Seq(visible)).isEmpty)
    },
    test("allows erasing only the history of a required value") {
      assertTrue(removable(ExactlyOne, onlyHistory = true, Seq(visible)).isEmpty)
    },
  )

  private def valueAddableSuite = suite("valueAddable")(
    test("refuses a second value where the cardinality allows one, counting values the caller cannot see") {
      val violated = Some(IntegrityViolation.CardinalityViolated(resourceIri, property))
      assertTrue(
        addable(ZeroOrOne, Seq(hidden)) == violated,
        addable(ExactlyOne, Seq(visible)) == violated,
      )
    },
    test("allows the first value of an optional property") {
      assertTrue(addable(ZeroOrOne, Seq.empty).isEmpty)
    },
    test("allows the missing value of a required property") {
      assertTrue(addable(ExactlyOne, Seq.empty).isEmpty, addable(AtLeastOne, Seq.empty).isEmpty)
    },
    test("allows another value where the cardinality has no maximum") {
      assertTrue(addable(Unbounded, Seq(visible, hidden)).isEmpty, addable(AtLeastOne, Seq(visible)).isEmpty)
    },
  )
}
