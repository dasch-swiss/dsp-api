/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.PropertyInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsClassification.ProjectOntologyIndex
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsClassification.ValueKind

/**
 * Tests for [[ViewRestrictionsClassification]].
 *
 * The classification decides the property report's **entire row set**: a property it rejects never appears
 * in step 1, so it can never be counted in step 2 and no admin will ever see its restrictions. There is no
 * downstream check that would catch the mistake, hence direct tests rather than trusting the E2E spec, whose
 * fixture cannot distinguish "correctly excluded" from "wrongly dropped".
 *
 * No triplestore and no ontology cache: the flags `ReadPropertyInfoV2` carries are set directly.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsClassificationSpec extends ZIOSpecDefault {

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

  private def classInfo(iri: String, isResourceClass: Boolean): ReadClassInfoV2 =
    ReadClassInfoV2(
      entityInfoContent = ClassInfoContentV2(classIri = sf.toSmartIri(iri), ontologySchema = InternalSchema),
      allBaseClasses = Seq.empty,
      isResourceClass = isResourceClass,
    )

  private val kb       = "http://www.knora.org/ontology/knora-base#"
  private val anything = "http://www.knora.org/ontology/0001/anything#"

  def spec = suite("ViewRestrictionsClassification")(
    suite("ValueKind.of")(
      test("classifies a plain value property as Plain") {
        assertTrue(ValueKind.of(property(s"${anything}hasText")) == Some(ValueKind.Plain))
      },
      test("classifies a file-value property as File") {
        assertTrue(
          ValueKind.of(property(s"${kb}hasStillImageFileValue", isFileValueProp = true)) == Some(ValueKind.File),
        )
      },
      test("classifies a user link-value property as Link") {
        assertTrue(
          ValueKind.of(property(s"${anything}hasOtherThingValue", isLinkValueProp = true)) == Some(ValueKind.Link),
        )
      },
      test("rejects a link property") {
        assertTrue(ValueKind.of(property(s"${anything}hasOtherThing", isLinkProp = true)).isEmpty)
      },
      test("rejects a standoff internal reference property") {
        assertTrue(
          ValueKind
            .of(property(s"${kb}standoffTagHasInternalReference", isStandoffInternalReferenceProperty = true))
            .isEmpty,
        )
      },
      test("rejects a non-resource property") {
        assertTrue(ValueKind.of(property(s"${kb}subjectClassConstraint", isResourceProp = false)).isEmpty)
      },
      test("rejects the standoff link-value property") {
        assertTrue(ValueKind.of(property(s"${kb}hasStandoffLinkToValue", isLinkValueProp = true)).isEmpty)
      },
    ),
    suite("ProjectOntologyIndex.from")(
      test("keeps resource classes and drops other classes") {
        val index = ProjectOntologyIndex.from(
          Seq.empty,
          Seq(classInfo(s"${anything}Thing", isResourceClass = true), classInfo(s"${anything}TextValue", false)),
        )
        assertTrue(index.resourceClasses == Set(s"${anything}Thing"))
      },
      test("keeps value properties by kind and drops link properties") {
        val index = ProjectOntologyIndex.from(
          Seq(
            property(s"${anything}hasText"),
            property(s"${anything}hasOtherThing", isLinkProp = true),
            property(s"${anything}hasOtherThingValue", isLinkValueProp = true),
          ),
          Seq.empty,
        )
        assertTrue(
          index.valueProperties == Map(
            s"${anything}hasText"            -> ValueKind.Plain,
            s"${anything}hasOtherThingValue" -> ValueKind.Link,
          ),
          index.knownProperties == Set(
            s"${anything}hasText",
            s"${anything}hasOtherThing",
            s"${anything}hasOtherThingValue",
          ),
        )
      },
    ),
  )
}
