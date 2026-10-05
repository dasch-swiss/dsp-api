/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.ontologymessages

import org.eclipse.rdf4j.model.vocabulary.XSD
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Simple
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter

@RunWith(classOf[DspZTestJUnitRunner])
class KnoraBaseToApiV2TransformationRulesSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private def predicateObject(
    rules: OntologyTransformationRules,
    propertyIri: String,
    predicateIri: String,
  ): Option[SmartIri] =
    rules.externalPropertiesToAdd
      .get(propertyIri.toSmartIri)
      .flatMap(_.entityInfoContent.getPredicateIriObject(predicateIri.toSmartIri))

  def spec: Spec[TestEnvironment, Any] = suite("KnoraBaseToApiV2TransformationRules")(
    suite("knora-api:externalUrl keeps xsd:anyURI although knora-base stores xsd:string")(
      test("in the complex schema") {
        val rules = KnoraBaseToApiV2ComplexTransformationRules
        val iri   = KnoraApiV2Complex.ExternalUrl
        assertTrue(
          predicateObject(rules, iri, KnoraApiV2Complex.ObjectType).contains(XSD.ANYURI.toString.toSmartIri),
          predicateObject(rules, iri, KnoraApiV2Complex.SubjectType)
            .contains(KnoraApiV2Complex.StillImageExternalFileValue.toSmartIri),
        )
      },
      test("in the simple schema") {
        val rules = KnoraBaseToApiV2SimpleTransformationRules
        val iri   = KnoraApiV2Simple.ExternalUrl
        assertTrue(
          predicateObject(rules, iri, KnoraApiV2Simple.ObjectType).contains(XSD.ANYURI.toString.toSmartIri),
          predicateObject(rules, iri, KnoraApiV2Simple.SubjectType).contains(KnoraApiV2Simple.File.toSmartIri),
        )
      },
    ),
  )
}
