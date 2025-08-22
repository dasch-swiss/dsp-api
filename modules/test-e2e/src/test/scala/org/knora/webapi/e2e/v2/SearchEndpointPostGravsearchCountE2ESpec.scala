/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import sttp.client4.UriContext
import zio.ZIO
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

object SearchEndpointPostGravsearchCountE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
    RdfDataObject(path = "test_data/project_data/beol-data.ttl", name = "http://www.knora.org/data/0801/beol"),
    RdfDataObject(
      path = "test_data/project_ontologies/books-onto.ttl",
      name = "http://www.knora.org/ontology/0001/books",
    ),
    RdfDataObject(path = "test_data/project_data/books-data.ttl", name = "http://www.knora.org/data/0001/books"),
    RdfDataObject(
      path = "test_data/generated_test_data/e2e.v2.SearchRouteV2R2RSpec/gravsearchtest1-admin.ttl",
      name = "http://www.knora.org/data/admin",
    ),
    RdfDataObject(
      path = "test_data/generated_test_data/e2e.v2.SearchRouteV2R2RSpec/gravsearchtest1-onto.ttl",
      name = "http://www.knora.org/ontology/0666/gravsearchtest1",
    ),
    RdfDataObject(
      path = "test_data/generated_test_data/e2e.v2.SearchRouteV2R2RSpec/gravsearchtest1-data.ttl",
      name = "http://www.knora.org/data/0666/gravsearchtest1",
    ),
  )

  private def verifySearchCountResult(query: String, expectedCount: Int) = for {
    response    <- TestApiClient.postJsonLdDocument(uri"/v2/searchextended/count", query)
    jsonLd      <- response.assert200
    actualCount <- ZIO.fromEither(jsonLd.body.getRequiredInt(OntologyConstants.SchemaOrg.NumberOfItems))
  } yield assertTrue(actualCount == expectedCount)

  override def e2eSpec = suite("SearchEndpoints POST /v2/searchextended/count")(
    suite("without type inference")(
      test("perform a Gravsearch count query for an anything:Thing with an optional date used as a sort criterion") {
        val query =
          """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
            |
            |CONSTRUCT {
            |  ?thing knora-api:isMainResource true .
            |  ?thing anything:hasDate ?date .
            |} WHERE {
            |  ?thing a knora-api:Resource .
            |  ?thing a anything:Thing .
            |
            |  OPTIONAL {
            |    ?thing anything:hasDate ?date .
            |    anything:hasDate knora-api:objectType knora-api:Date .
            |    ?date a knora-api:Date .
            |  }
            |
            |  MINUS {
            |    ?thing anything:hasInteger ?intVal .
            |    anything:hasInteger knora-api:objectType xsd:integer .
            |    ?intVal a xsd:integer .
            |    FILTER(?intVal = 123454321 || ?intVal = 999999999)
            |  }
            |}
            |ORDER BY DESC(?date)
            |""".stripMargin
        verifySearchCountResult(query, 44)
      },
      test(
        "perform a Gravsearch count query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer",
      ) {
        val query =
          """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
            |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |
            |CONSTRUCT {
            |    ?book knora-api:isMainResource true .
            |
            |    ?book incunabula:title ?title .
            |
            |} WHERE {
            |
            |    ?book a incunabula:book .
            |    ?book a knora-api:Resource .
            |
            |    ?book incunabula:title ?title .
            |    incunabula:title knora-api:objectType xsd:string .
            |
            |    ?title a xsd:string .
            |
            |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
            |
            |}
            |""".stripMargin
        verifySearchCountResult(query, 2)
      },
      test("perform a Gravsearch count query for books that do not have the title 'Zeitglöcklein des Lebens'") {
        val query =
          """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
            |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |
            |CONSTRUCT {
            |    ?book knora-api:isMainResource true .
            |
            |    ?book incunabula:title ?title .
            |
            |} WHERE {
            |
            |    ?book a incunabula:book .
            |    ?book a knora-api:Resource .
            |
            |    ?book incunabula:title ?title .
            |    incunabula:title knora-api:objectType xsd:string .
            |
            |    ?title a xsd:string .
            |
            |    FILTER(?title != "Zeitglöcklein des Lebens und Leidens Christi")
            |
            |}
            |""".stripMargin
        // 19 - 2 = 18 :-)
        // there is a total of 19 incunabula books of which two have the title "Zeitglöcklein des Lebens und Leidens Christi" (see test above)
        // however, there are 18 books that have a title that is not "Zeitglöcklein des Lebens und Leidens Christi"
        // this is because there is a book that has two titles, one "Zeitglöcklein des Lebens und Leidens Christi" and the other in Latin "Horologium devotionis circa vitam Christi"
        verifySearchCountResult(query, 18)
      },
      test("do a Gravsearch count query for a letter that links to a specific person via two possible properties") {
        val query =
          """
            |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
            |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |
            |CONSTRUCT {
            |    ?letter knora-api:isMainResource true .
            |
            |    ?letter beol:creationDate ?date .
            |
            |    ?letter ?linkingProp1  <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> .
            |
            |
            |} WHERE {
            |    ?letter a knora-api:Resource .
            |    ?letter a beol:letter .
            |
            |    ?letter beol:creationDate ?date .
            |
            |    beol:creationDate knora-api:objectType knora-api:Date .
            |    ?date a knora-api:Date .
            |
            |    # testperson2
            |    ?letter ?linkingProp1 <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> .
            |
            |    <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> a knora-api:Resource .
            |
            |    ?linkingProp1 knora-api:objectType knora-api:Resource .
            |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
            |
            |    beol:hasAuthor knora-api:objectType knora-api:Resource .
            |    beol:hasRecipient knora-api:objectType knora-api:Resource .
            |
            |}
            |ORDER BY ?date
            |""".stripMargin
        verifySearchCountResult(query, 1)
      },
      test("do a Gravsearch count query for a letter that links to a person with a specified name") {
        val query =
          """
            |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
            |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |
            |CONSTRUCT {
            |    ?letter knora-api:isMainResource true .
            |
            |    ?letter beol:creationDate ?date .
            |
            |    ?letter ?linkingProp1  ?person1 .
            |
            |    ?person1 beol:hasFamilyName ?name .
            |
            |} WHERE {
            |    ?letter a knora-api:Resource .
            |    ?letter a beol:letter .
            |
            |    ?letter beol:creationDate ?date .
            |
            |    beol:creationDate knora-api:objectType knora-api:Date .
            |    ?date a knora-api:Date .
            |
            |    ?letter ?linkingProp1 ?person1 .
            |
            |    ?person1 a knora-api:Resource .
            |
            |    ?linkingProp1 knora-api:objectType knora-api:Resource .
            |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
            |
            |    beol:hasAuthor knora-api:objectType knora-api:Resource .
            |    beol:hasRecipient knora-api:objectType knora-api:Resource .
            |
            |    ?person1 beol:hasFamilyName ?name .
            |
            |    beol:hasFamilyName knora-api:objectType xsd:string .
            |    ?name a xsd:string .
            |
            |    FILTER(?name = "Meier")
            |
            |
            |}
            |ORDER BY ?date
            |""".stripMargin
        verifySearchCountResult(query, 1)
      },
    ),
    suite("with type inference")(
      test(
        "perform a Gravsearch count query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer (with type inference)",
      ) {
        val query =
          """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
            |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |
            |CONSTRUCT {
            |    ?book knora-api:isMainResource true .
            |
            |    ?book incunabula:title ?title .
            |
            |} WHERE {
            |
            |    ?book a incunabula:book .
            |
            |    ?book incunabula:title ?title .
            |
            |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
            |
            |}
            |""".stripMargin
        verifySearchCountResult(query, 2)
      },
      test(
        "perform a Gravsearch count query for books that do not have the title 'Zeitglöcklein des Lebens' (with type inference)",
      ) {
        val query =
          """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
            |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |
            |    CONSTRUCT {
            |        ?book knora-api:isMainResource true .
            |
            |        ?book incunabula:title ?title .
            |
            |    } WHERE {
            |
            |        ?book a incunabula:book .
            |
            |        ?book incunabula:title ?title .
            |
            |        FILTER(?title != "Zeitglöcklein des Lebens und Leidens Christi")
            |
            |    }
                """.stripMargin
        // 19 - 2 = 18 :-)
        // there is a total of 19 incunabula books of which two have the title "Zeitglöcklein des Lebens und Leidens Christi" (see test above)
        // however, there are 18 books that have a title that is not "Zeitglöcklein des Lebens und Leidens Christi"
        // this is because there is a book that has two titles, one "Zeitglöcklein des Lebens und Leidens Christi" and the other in Latin "Horologium devotionis circa vitam Christi"
        verifySearchCountResult(query, 18)
      },
      test("do a Gravsearch count query that searches for a list node (with type inference)") {
        val query =
          """
            |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
            |
            |CONSTRUCT {
            |
            |?mainRes knora-api:isMainResource true .
            |
            |?mainRes anything:hasListItem ?propVal0 .
            |
            |} WHERE {
            |
            |?mainRes anything:hasListItem ?propVal0 .
            |
            |FILTER(?propVal0 = "Tree list node 02"^^knora-api:ListNode)
            |
            |}
            |
            |OFFSET 0
            |""".stripMargin
        verifySearchCountResult(query, 1)
      },
    ),
    suite("Queries that submit the complex schema")(
      test(
        "perform a Gravsearch count query for an anything:Thing with an optional date used as a sort criterion (submitting the complex schema)",
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
            |ORDER BY DESC(?date)
            |""".stripMargin
        verifySearchCountResult(query, 44)
      },
      test(
        "perform a Gravsearch count query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer (submitting the complex schema)",
      ) {
        val query =
          """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
            |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
            |
            |CONSTRUCT {
            |    ?book knora-api:isMainResource true .
            |
            |    ?book incunabula:title ?title .
            |
            |} WHERE {
            |
            |    ?book a incunabula:book .
            |
            |    ?book incunabula:title ?title .
            |
            |    ?title knora-api:valueAsString "Zeitglöcklein des Lebens und Leidens Christi" .
            |
            |}
            |""".stripMargin
        verifySearchCountResult(query, 2)
      },
      test(
        "perform a Gravsearch count query for books that do not have the title 'Zeitglöcklein des Lebens' (submitting the complex schema)",
      ) {
        val query =
          """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
            |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
            |
            |CONSTRUCT {
            |    ?book knora-api:isMainResource true .
            |
            |    ?book incunabula:title ?title .
            |
            |} WHERE {
            |
            |    ?book a incunabula:book .
            |
            |    ?book incunabula:title ?title .
            |
            |    ?title knora-api:valueAsString ?titleStr .
            |
            |    FILTER(?titleStr != "Zeitglöcklein des Lebens und Leidens Christi")
            |
            |}
            |""".stripMargin
        // 19 - 2 = 18 :-)
        // there is a total of 19 incunabula books of which two have the title "Zeitglöcklein des Lebens und Leidens Christi" (see test above)
        // however, there are 18 books that have a title that is not "Zeitglöcklein des Lebens und Leidens Christi"
        // this is because there is a book that has two titles, one "Zeitglöcklein des Lebens und Leidens Christi" and the other in Latin "Horologium devotionis circa vitam Christi"
        verifySearchCountResult(query, 18)
      },
      test(
        "search for a list value that does not refer to a particular list node, performing a count query (submitting the complex schema)",
      ) {
        val query =
          """
            |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
            |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
            |
            |CONSTRUCT {
            |    ?thing knora-api:isMainResource true .
            |
            |    ?thing anything:hasListItem ?listItem .
            |
            |} WHERE {
            |    ?thing anything:hasListItem ?listItem .
            |
            |    FILTER NOT EXISTS {
            |
            |     ?listItem knora-api:listValueAsListNode <http://rdfh.ch/lists/0001/treeList02> .
            |
            |    }
            |
            |}
            |""".stripMargin
        verifySearchCountResult(query, 2)
      },
    ),
  )
}
