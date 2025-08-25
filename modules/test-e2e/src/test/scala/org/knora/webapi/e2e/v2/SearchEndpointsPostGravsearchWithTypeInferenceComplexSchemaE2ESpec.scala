/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
//import org.knora.webapi.e2e.v2.ResponseCheckerV2.checkSearchResponseNumberOfResults
import org.knora.webapi.e2e.v2.SearchEndpointE2ESpecHelper.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
//import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
//import org.knora.webapi.testservices.RequestsUpdates.addSimpleSchemaHeader
//import org.knora.webapi.testservices.ResponseOps.assert200
//import org.knora.webapi.testservices.ResponseOps.assert400

object SearchEndpointsPostGravsearchWithTypeInferenceComplexSchemaE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = SearchEndpointE2ESpecHelper.rdfDataObjects

  override def e2eSpec = suite("SearchEndpoints POST /v2/searchextended (with type inference, complex schema)")(
    test(
      "perform a Gravsearch query for an anything:Thing with an optional date and sort by date (submitting the complex schema)",
    ) {
      val query =
        """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |  ?thing anything:hasDate ?date .
          |} WHERE {
          |
          |  ?thing a knora-api:Resource .
          |  ?thing a anything:Thing .
          |
          |  OPTIONAL {
          |    ?thing anything:hasDate ?date .
          |  }
          |
          |  MINUS {
          |    ?thing anything:hasInteger ?intVal .
          |    ?intVal knora-api:intValueAsInt 123454321 .
          |  }
          |
          |  MINUS {
          |    ?thing anything:hasInteger ?intVal .
          |    ?intVal knora-api:intValueAsInt 999999999 .
          |  }
          |}
          |ORDER BY DESC(?date)""".stripMargin
      verifyQueryResult(query, "thingWithOptionalDateSortedDesc.jsonld")
    },
  )
}
