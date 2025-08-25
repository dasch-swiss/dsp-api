/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import zio.*
import zio.test.*
import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2e.v2.ResponseCheckerV2.checkSearchResponseNumberOfResults
import org.knora.webapi.e2e.v2.SearchEndpointE2ESpecHelper.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
//import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
//import org.knora.webapi.testservices.RequestsUpdates.addSimpleSchemaHeader
import org.knora.webapi.testservices.ResponseOps.assert200
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
    test(
      "perform a Gravsearch query for an anything:Thing that has an optional decimal value greater than 2 and sort by the decimal value (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasDecimal ?decimal .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |     ?thing a knora-api:Resource .
          |
          |     OPTIONAL {
          |        ?thing anything:hasDecimal ?decimal .
          |        ?decimal knora-api:decimalValueAsDecimal ?decimalVal .
          |        FILTER(?decimalVal > "1"^^xsd:decimal)
          |     }
          |
          |     MINUS {
          |        ?thing anything:hasInteger ?intVal .
          |        ?intVal knora-api:intValueAsInt 123454321 .
          |     }
          |
          |     MINUS {
          |       ?thing anything:hasInteger ?intVal .
          |       ?intVal knora-api:intValueAsInt 999999999 .
          |     }
          |} ORDER BY DESC(?decimal)
          |""".stripMargin
      verifyQueryResult(query, "ThingsWithOptionalDecimalGreaterThan1.jsonld")
    },
    test(
      "do a Gravsearch query that finds all the books that have a page with seqnum 100 (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |
          |  ?book knora-api:isMainResource true .
          |
          |  ?page incunabula:partOf ?book ;
          |    incunabula:seqnum ?seqnum .
          |
          |} WHERE {
          |
          |  ?page incunabula:partOf ?book ;
          |    incunabula:seqnum ?seqnum .
          |
          |  ?seqnum knora-api:intValueAsInt 100 .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "booksWithPage100.jsonld")
    },
    test(
      "do a Gravsearch query that finds all the letters sent by someone called Meier, ordered by date (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |CONSTRUCT {
          |
          |  ?letter knora-api:isMainResource true ;
          |    beol:creationDate ?date ;
          |    beol:hasAuthor ?author .
          |
          |  ?author beol:hasFamilyName ?name .
          |
          |} WHERE {
          |
          |  ?letter beol:hasAuthor ?author ;
          |    beol:creationDate ?date .
          |
          |  ?author beol:hasFamilyName ?name .
          |
          |  ?name knora-api:valueAsString "Meier" .
          |
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "lettersByMeier.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer (submitting the complex schema)",
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
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString "Zeitglöcklein des Lebens und Leidens Christi" .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' not returning the title in the answer (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
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
      verifyQueryResult(query, "ZeitgloeckleinExtendedSearchNoTitleInAnswer.jsonld")
    },
    test(
      "perform a Gravsearch query for books that do not have the title 'Zeitglöcklein des Lebens' (submitting the complex schema)",
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
      verifyQueryResult(query, "NotZeitgloeckleinExtendedSearch.jsonld")
    },
    test(
      "perform a Gravsearch query for the page of a book whose seqnum equals 10, returning the seqnum and the link value (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?page knora-api:isMainResource true .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |} WHERE {
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |
          |    ?seqnum knora-api:intValueAsInt 10 .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "PageWithSeqnum10WithSeqnumAndLinkValueInAnswer.jsonld")
    },
    test(
      "perform a Gravsearch count query for the page of a book whose seqnum equals 10, returning the seqnum and the link value (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?page knora-api:isMainResource true .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |} WHERE {
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |
          |    ?seqnum knora-api:intValueAsInt 10 .
          |}
          |""".stripMargin
      for {
        response <- postGravsearchQuery(query)
        jsonLd   <- response.assert200
        _        <- ZIO.attempt(checkSearchResponseNumberOfResults(jsonLd, 1))
      } yield assertCompletes
    },
  )
}
