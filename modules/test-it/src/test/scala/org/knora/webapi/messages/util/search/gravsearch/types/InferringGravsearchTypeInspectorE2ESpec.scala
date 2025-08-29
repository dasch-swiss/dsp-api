/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.types

import zio.*
import zio.test.*

import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.util.search.gravsearch.types.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*

object InferringGravsearchTypeInspectorE2ESpec extends E2EZSpec {

  val QueryRdfTypeRule: String =
    """
      |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
      |
      |CONSTRUCT {
      |    ?letter knora-api:isMainResource true .
      |    ?letter beol:creationDate ?date .
      |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
      |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
      |} WHERE {
      |    ?letter a beol:letter .
      |    ?letter beol:creationDate ?date .
      |    beol:creationDate knora-api:objectType knora-api:Date .
      |    ?date a knora-api:Date .
      |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
      |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> a beol:person .
      |    ?linkingProp1 knora-api:objectType knora-api:Resource .
      |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
      |    beol:hasFamilyName knora-api:objectType xsd:string .
      |    ?name a xsd:string .
      |
      |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
      |} ORDER BY ?date
        """.stripMargin

  val TypeInferenceResult1: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
    entities = Map(
      TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
        objectIsResourceType = true,
      ),
      TypeableVariable(variableName = "linkingProp1") -> PropertyTypeInfo(
        objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
        objectIsResourceType = true,
      ),
      TypeableVariable(variableName = "date") -> NonPropertyTypeInfo(
        typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri,
        isValueType = true,
      ),
      TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#creationDate".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri,
        objectIsValueType = true,
      ),
      TypeableIri(iri =
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasFamilyName".toSmartIri,
      ) -> PropertyTypeInfo(
        objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        objectIsValueType = true,
      ),
      TypeableIri(iri = "http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA".toSmartIri) -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
        isResourceType = true,
      ),
      TypeableVariable(variableName = "name") -> NonPropertyTypeInfo(
        typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        isValueType = true,
      ),
      TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
        isResourceType = true,
      ),
      TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
        objectIsResourceType = true,
      ),
    ),
  )

  val QueryWithInconsistentTypes3: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
      |
      |CONSTRUCT {
      |  ?person knora-api:isMainResource true .
      |  ?document beol:hasAuthor ?person .
      |} WHERE {
      |  ?person a knora-api:Resource .
      |  ?person a beol:person .
      |
      |  ?document beol:hasAuthor ?person .
      |  beol:hasAuthor knora-api:objectType knora-api:Resource .
      |  ?document a knora-api:Resource .
      |  { ?document a beol:manuscript . } UNION { ?document a beol:letter .}
      |}
      |""".stripMargin

  override val e2eSpec = suite("Type refinement")(
    test("refine the inspected types for each typeableEntity") {
      for {
        typeInspectionRunner <- ZIO.service[InferringGravsearchTypeInspector]
        parsedQuery           = GravsearchParser.parseQuery(QueryRdfTypeRule)
        result <- typeInspectionRunner.getUsageIndexAndEntityInfos(
                    parsedQuery.whereClause,
                    requestingUser = anythingAdminUser,
                  )
        (_, entityInfo) = result
        multipleDetectedTypes = IntermediateTypeInspectionResult(
                                  entities = Map(
                                    TypeableVariable(variableName = "letter") -> Set(
                                      NonPropertyTypeInfo(
                                        typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
                                        isResourceType = true,
                                      ),
                                      NonPropertyTypeInfo(
                                        typeIri =
                                          "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
                                        isResourceType = true,
                                      ),
                                      NonPropertyTypeInfo(
                                        typeIri =
                                          "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#basicLetter".toSmartIri,
                                        isResourceType = true,
                                      ),
                                    ),
                                  ),
                                  entitiesInferredFromPropertyIris = Map(
                                    TypeableVariable(variableName = "letter") -> Set(
                                      NonPropertyTypeInfo(
                                        typeIri =
                                          "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#basicLetter".toSmartIri,
                                        isResourceType = true,
                                      ),
                                    ),
                                  ),
                                )
        refinedIntermediateResults = typeInspectionRunner.refineDeterminedTypes(
                                       intermediateResult = multipleDetectedTypes,
                                       entityInfo = entityInfo,
                                     )
      } yield assertTrue(
        refinedIntermediateResults.entities.size == 1,
        refinedIntermediateResults.entities
          .get(TypeableVariable(variableName = "letter"))
          .contains(
            Set(
              NonPropertyTypeInfo(
                typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
                isResourceType = true,
              ),
            ),
          ),
        refinedIntermediateResults.entitiesInferredFromPropertyIris.isEmpty,
      )
    },
    test("sanitize inconsistent resource types that only have knora-base:Resource as base class in common") {
      for {
        typeInspectionRunner <- ZIO.service[InferringGravsearchTypeInspector]
        parsedQuery           = GravsearchParser.parseQuery(QueryRdfTypeRule)
        result <- typeInspectionRunner.getUsageIndexAndEntityInfos(
                    parsedQuery.whereClause,
                    requestingUser = anythingAdminUser,
                  )
        (usageIndex, entityInfo) = result
        inconsistentTypes = IntermediateTypeInspectionResult(
                              entities = Map(
                                TypeableVariable(variableName = "letter") -> Set(
                                  NonPropertyTypeInfo(
                                    typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
                                    isResourceType = true,
                                  ),
                                  NonPropertyTypeInfo(
                                    typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
                                    isResourceType = true,
                                  ),
                                ),
                                TypeableVariable(variableName = "linkingProp1") -> Set(
                                  PropertyTypeInfo(
                                    objectTypeIri =
                                      "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
                                    objectIsResourceType = true,
                                  ),
                                  PropertyTypeInfo(
                                    objectTypeIri =
                                      "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
                                    objectIsResourceType = true,
                                  ),
                                ),
                              ),
                              entitiesInferredFromPropertyIris = Map(
                                TypeableVariable(variableName = "letter") -> Set(
                                  NonPropertyTypeInfo(
                                    typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
                                    isResourceType = true,
                                  ),
                                ),
                              ),
                            )
        refinedIntermediateResults = typeInspectionRunner.refineDeterminedTypes(
                                       intermediateResult = inconsistentTypes,
                                       entityInfo = entityInfo,
                                     )
        sanitizedResults = typeInspectionRunner.sanitizeInconsistentResourceTypes(
                             lastResults = refinedIntermediateResults,
                             usageIndex.querySchema,
                             entityInfo = entityInfo,
                           )
        expectedResult = IntermediateTypeInspectionResult(
                           entities = Map(
                             TypeableVariable(variableName = "letter") -> Set(
                               NonPropertyTypeInfo(
                                 typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
                                 isResourceType = true,
                               ),
                             ),
                             TypeableVariable(variableName = "linkingProp1") -> Set(
                               PropertyTypeInfo(
                                 objectTypeIri =
                                   "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
                                 objectIsResourceType = true,
                               ),
                             ),
                           ),
                           entitiesInferredFromPropertyIris = Map(
                             TypeableVariable(variableName = "letter") -> Set(
                               NonPropertyTypeInfo(
                                 typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
                                 isResourceType = true,
                               ),
                             ),
                           ),
                         )
      } yield assertTrue(sanitizedResults.entities == expectedResult.entities)
    },
    test("sanitize inconsistent resource types that have common base classes other than knora-base:Resource") {
      for {
        typeInspectionRunner <- ZIO.service[InferringGravsearchTypeInspector]
        parsedQuery           = GravsearchParser.parseQuery(QueryWithInconsistentTypes3)
        result <- typeInspectionRunner.getUsageIndexAndEntityInfos(
                    parsedQuery.whereClause,
                    requestingUser = anythingAdminUser,
                  )
        (usageIndex, entityInfo) = result
        inconsistentTypes = IntermediateTypeInspectionResult(
                              entities = Map(
                                TypeableVariable(variableName = "document") -> Set(
                                  NonPropertyTypeInfo(
                                    typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
                                    isResourceType = true,
                                  ),
                                  NonPropertyTypeInfo(
                                    typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#manuscript".toSmartIri,
                                    isResourceType = true,
                                  ),
                                ),
                              ),
                              entitiesInferredFromPropertyIris = Map(
                                TypeableVariable(variableName = "document") -> Set(
                                  NonPropertyTypeInfo(
                                    typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
                                    isResourceType = true,
                                  ),
                                ),
                              ),
                            )
        refinedIntermediateResults = typeInspectionRunner.refineDeterminedTypes(
                                       intermediateResult = inconsistentTypes,
                                       entityInfo = entityInfo,
                                     )
        sanitizedResults = typeInspectionRunner.sanitizeInconsistentResourceTypes(
                             lastResults = refinedIntermediateResults,
                             usageIndex.querySchema,
                             entityInfo = entityInfo,
                           )
        expectedResult = IntermediateTypeInspectionResult(
                           entities = Map(
                             TypeableVariable(variableName = "document") -> Set(
                               NonPropertyTypeInfo(
                                 typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#writtenSource".toSmartIri,
                                 isResourceType = true,
                               ),
                             ),
                           ),
                           entitiesInferredFromPropertyIris = Map(
                             TypeableVariable(variableName = "document") -> Set(
                               NonPropertyTypeInfo(
                                 typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
                                 isResourceType = true,
                               ),
                             ),
                           ),
                         )
      } yield assertTrue(sanitizedResults.entities == expectedResult.entities)
    },
  )
}
