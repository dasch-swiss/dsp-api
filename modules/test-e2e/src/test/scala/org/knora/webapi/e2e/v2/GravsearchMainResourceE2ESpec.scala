/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.junit.runner.RunWith
import sttp.client4.UriContext
import sttp.model.Uri
import zio.ZIO
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

@RunWith(classOf[DspZTestJUnitRunner])
class GravsearchMainResourceE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = SearchEndpointE2ESpecHelper.rdfDataObjects

  private val searchUri: Uri = uri"/v2/searchextended"
  private val countUri: Uri  = uri"/v2/searchextended/count"

  private def thingQuery(construct: String, where: String): String =
    s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
       |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
       |CONSTRUCT {
       |  $construct
       |} WHERE {
       |  ?thing a anything:Thing .
       |  $where
       |}""".stripMargin

  private val mainThing = "?thing knora-api:isMainResource true ."

  private def count(query: String) = for {
    response <- TestApiClient.postJsonLdDocument(countUri, query, None)
    jsonLd   <- response.assert200
    count    <- ZIO.fromEither(jsonLd.body.getRequiredInt(OntologyConstants.SchemaOrg.NumberOfItems))
  } yield count

  private def assertRejected(uri: Uri, query: String, expectedMessage: String) =
    TestApiClient.postSparql(uri, query).map { response =>
      assertTrue(response.code.code == 400, response.body.left.exists(_.contains(expectedMessage)))
    }

  private def rejectedOnBothEndpoints(name: String, query: String, expectedMessage: String) =
    suite(name)(
      test("search")(assertRejected(searchUri, query, expectedMessage)),
      test("count")(assertRejected(countUri, query, expectedMessage)),
    )

  override def e2eSpec = suite("Gravsearch knora-api:isMainResource")(
    suite("in the WHERE clause, marking the same variable as the CONSTRUCT clause")(
      test("is ignored by a count query") {
        for {
          withStatement    <- count(thingQuery(mainThing, mainThing))
          withoutStatement <- count(thingQuery(mainThing, ""))
        } yield assertTrue(withStatement > 0, withStatement == withoutStatement)
      },
      test("is accepted by a search query") {
        TestApiClient.postSparql(searchUri, thingQuery(mainThing, mainThing)).map { response =>
          assertTrue(response.code.code == 200)
        }
      },
      test("is ignored in the complex schema without type annotations") {
        val query =
          """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
            |CONSTRUCT { ?r knora-api:isMainResource true . ?r knora-api:hasStillImageFileValue ?d . }
            |WHERE { ?r knora-api:isMainResource true . ?r knora-api:hasStillImageFileValue ?d . }""".stripMargin
        TestApiClient.postSparql(searchUri, query).map(response => assertTrue(response.code.code == 200))
      },
    ),
    rejectedOnBothEndpoints(
      "as the only statement in the WHERE clause",
      """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
        |CONSTRUCT { ?thing knora-api:isMainResource true . }
        |WHERE { ?thing knora-api:isMainResource true . }""".stripMargin,
      "The WHERE clause contains no statements other than knora-api:isMainResource",
    ),
    rejectedOnBothEndpoints(
      "in the WHERE clause, marking a different variable than the CONSTRUCT clause",
      thingQuery(mainThing, "?other knora-api:isMainResource true ."),
      "knora-api:isMainResource in the WHERE clause marks ?other, but the CONSTRUCT clause marks ?thing",
    ),
    rejectedOnBothEndpoints(
      "in the WHERE clause, with the object false",
      thingQuery(mainThing, "?thing knora-api:isMainResource false ."),
      "The object of knora-api:isMainResource can only be true",
    ),
    rejectedOnBothEndpoints(
      "in the WHERE clause, inside OPTIONAL",
      thingQuery(mainThing, "OPTIONAL { ?thing knora-api:isMainResource true . }"),
      "knora-api:isMainResource can only be used at the top level of the WHERE clause, not inside a nested block",
    ),
    rejectedOnBothEndpoints(
      "in the CONSTRUCT clause, with the object false",
      thingQuery("?thing knora-api:isMainResource false .", ""),
      "The object of knora-api:isMainResource can only be true",
    ),
  )
}
