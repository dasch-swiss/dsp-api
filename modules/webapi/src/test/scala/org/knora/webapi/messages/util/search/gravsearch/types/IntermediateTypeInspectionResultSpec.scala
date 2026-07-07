/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.types

import zio.test.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter

object IntermediateTypeInspectionResultSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  val spec = suite("IntermediateTypeInspectionResultSpec")(
    test("addTypes should remove types for an entity") {

      val multipleDetectedTypes: IntermediateTypeInspectionResult = IntermediateTypeInspectionResult(
        entities = Map(
          TypeableVariable(variableName = "mainRes") -> Set(
            NonPropertyTypeInfo(
              typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
              isResourceType = true,
            ),
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
              isResourceType = true,
            ),
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#basicLetter".toSmartIri,
              isResourceType = true,
            ),
          ),
        ),
        entitiesInferredFromPropertyIris = Map(
          TypeableVariable(variableName = "mainRes") -> Set(
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
              isResourceType = true,
            ),
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#basicLetter".toSmartIri,
              isResourceType = true,
            ),
          ),
        ),
      )

      // remove type basicLetter
      val actual: IntermediateTypeInspectionResult = multipleDetectedTypes.removeType(
        TypeableVariable(variableName = "mainRes"),
        NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#basicLetter".toSmartIri,
          isResourceType = true,
        ),
      )

      assertTrue(
        // Is it removed from entities?
        actual.entities
          .get(TypeableVariable(variableName = "mainRes"))
          .contains(
            Set(
              NonPropertyTypeInfo(
                typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
                isResourceType = true,
              ),
              NonPropertyTypeInfo(
                typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
                isResourceType = true,
              ),
            ),
          ),
        // Is it removed from entitiesInferredFromProperties?
        actual.entitiesInferredFromPropertyIris
          .get(TypeableVariable(variableName = "mainRes"))
          .contains(
            Set(
              NonPropertyTypeInfo(
                typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
                isResourceType = true,
              ),
            ),
          ),
      )
    },
  )
}
