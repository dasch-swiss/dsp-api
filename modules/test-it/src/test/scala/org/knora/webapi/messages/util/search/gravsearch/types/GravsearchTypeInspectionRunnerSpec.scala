/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.types
import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*

object GravsearchTypeInspectionRunnerSpec extends E2EZSpec {

  private def inspectTypes(query: String) = for {
    parsedQuery <- ZIO.attempt(GravsearchParser.parseQuery(query))
    result <- ZIO.serviceWithZIO[GravsearchTypeInspectionRunner](
                _.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser),
              )
  } yield result

  private val QueryWithInconsistentTypes3: String =
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

  override val e2eSpec = suite("GravsearchTypeInspectionRunnerSpec")(
    test("sanitize inconsistent types resulted from a union") {
      val expectedResult: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
        entities = Map(
          TypeableVariable(variableName = "person") ->
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
              isResourceType = true,
            ),
          TypeableVariable(variableName = "document") ->
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#writtenSource".toSmartIri,
              isResourceType = true,
            ),
          TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri) ->
            PropertyTypeInfo(
              objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
              objectIsResourceType = true,
            ),
        ),
        entitiesInferredFromProperties = Map(
          TypeableVariable(variableName = "person") -> Set(
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
              isResourceType = true,
            ),
          ),
        ),
      )

      inspectTypes(QueryWithInconsistentTypes3)
        .map(actual => assertTrue(actual.entities == expectedResult.entities))
    },
  )
}
