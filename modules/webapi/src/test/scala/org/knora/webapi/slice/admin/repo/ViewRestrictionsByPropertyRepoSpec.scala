/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.PropertyInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2

/**
 * Tests for [[ViewRestrictionsByPropertyRepo.isValueProperty]].
 *
 * That one predicate decides the property report's **entire row set**: a property it rejects never appears
 * in step 1, so it can never be counted in step 2 and no admin will ever see its restrictions. There is no
 * downstream check that would catch the mistake — an over-strict filter looks exactly like a project with
 * fewer properties. Hence a direct test rather than trusting the E2E spec, whose fixture cannot distinguish
 * "correctly excluded" from "wrongly dropped".
 *
 * No triplestore and no ontology cache: the predicate reads only the boolean flags `ReadPropertyInfoV2`
 * already carries, so the flags are set directly rather than derived from a fixture ontology.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsByPropertyRepoSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private def property(
    iri: String,
    isResourceProp: Boolean = true,
    isLinkProp: Boolean = false,
    isLinkValueProp: Boolean = false,
    isFileValueProp: Boolean = false,
    isStandoffInternalReferenceProperty: Boolean = false,
  ): ReadPropertyInfoV2 =
    ReadPropertyInfoV2(
      entityInfoContent = PropertyInfoContentV2(
        propertyIri = sf.toSmartIri(iri),
        ontologySchema = InternalSchema,
      ),
      isResourceProp = isResourceProp,
      isLinkProp = isLinkProp,
      isLinkValueProp = isLinkValueProp,
      isFileValueProp = isFileValueProp,
      isStandoffInternalReferenceProperty = isStandoffInternalReferenceProperty,
    )

  private val kb       = "http://www.knora.org/ontology/knora-base#"
  private val anything = "http://www.knora.org/ontology/0001/anything#"

  def spec = suite("ViewRestrictionsByPropertyRepo.isValueProperty")(
    test("accepts a plain value property") {
      assertTrue(ViewRestrictionsByPropertyRepo.isValueProperty(property(s"${anything}hasText")))
    },
    test("accepts a file-value property") {
      // Deliberately included: file values are permissioned objects and the report's `File` item type
      // exists precisely to count them, so excluding them here would make that filter always return zero.
      assertTrue(
        ViewRestrictionsByPropertyRepo.isValueProperty(property(s"${kb}hasStillImageFileValue", isFileValueProp = true)),
      )
    },
    test("rejects a link property") {
      // A link property points at another resource. It carries no value of its own to be restricted; the
      // permissioned object is the reified LinkValue, which the queries exclude anyway.
      assertTrue(
        !ViewRestrictionsByPropertyRepo.isValueProperty(property(s"${anything}hasOtherThing", isLinkProp = true)),
      )
    },
    test("rejects the reified link-value property") {
      assertTrue(
        !ViewRestrictionsByPropertyRepo
          .isValueProperty(property(s"${anything}hasOtherThingValue", isLinkValueProp = true)),
      )
    },
    test("rejects a standoff internal reference property") {
      assertTrue(
        !ViewRestrictionsByPropertyRepo
          .isValueProperty(
            property(s"${kb}standoffTagHasInternalReference", isStandoffInternalReferenceProperty = true),
          ),
      )
    },
    test("rejects a non-resource property, such as an ontology-level annotation") {
      // `isResourceProp` is the outer gate: without it the list would fill with properties that never
      // appear on project data at all, each rendering as a permanently all-zero row.
      assertTrue(
        !ViewRestrictionsByPropertyRepo.isValueProperty(
          property(s"${kb}subjectClassConstraint", isResourceProp = false),
        ),
      )
    },
  )
}
