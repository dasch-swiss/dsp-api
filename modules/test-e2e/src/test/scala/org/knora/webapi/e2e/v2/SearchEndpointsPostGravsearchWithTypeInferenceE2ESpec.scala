/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import sttp.client4.UriContext
import zio.*
import zio.test.*
import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2e.v2.ResponseCheckerV2.checkSearchResponseNumberOfResults
import org.knora.webapi.e2e.v2.SearchEndpointE2ESpecHelper.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.testservices.RequestsUpdates.addSimpleSchemaHeader
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

object SearchEndpointsPostGravsearchWithTypeInferenceE2ESpec extends E2EZSpec {

  override lazy val rdfDataObjects: List[RdfDataObject] = SearchEndpointE2ESpecHelper.rdfDataObjects

  override def e2eSpec = suite("SearchEndpoints POST /v2/searchextended (with type inference)")(
    test(
      "do a Gravsearch query in which 'rdf:type knora-api:Resource' is inferred from a more specific rdf:type (with type inference)",
    ) {
      val query =
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
          |""".stripMargin
      verifyQueryResult(query, "letterWithPersonWithName2.jsonld")
    },
    test(
      "do a Gravsearch query in which 'rdf:type knora-api:Resource' is inferred from a more specific rdf:type (with type inference)",
    ) {
      val query =
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
          |""".stripMargin
      verifyQueryResult(query, "letterWithPersonWithName2.jsonld")
    },
    test("do a Gravsearch query in which the object types of property IRIs are inferred (with type inference)") {
      val query =
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
          |    ?date a knora-api:Date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> a beol:person .
          |    ?linkingProp1 knora-api:objectType knora-api:Resource .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |    ?name a xsd:string .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "letterWithPersonWithName2.jsonld")
    },
    test(
      "do a Gravsearch query in which the types of property objects are inferred from the knora-api:objectType of each property (with type inference)",
    ) {
      val query =
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
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> a beol:person .
          |    ?linkingProp1 knora-api:objectType knora-api:Resource .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "letterWithPersonWithName2.jsonld")
    },
    test(
      "do a Gravsearch query in which a property's knora-api:objectType is inferred from its object (with type inference)",
    ) {
      val query =
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
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> a beol:person .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "letterWithPersonWithName2.jsonld")
    },
    test(
      "do a Gravsearch query in which the types of property subjects are inferred from the knora-api:subjectType of each property (with type inference)",
    ) {
      val query =
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
          |    ?letter beol:creationDate ?date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "letterWithPersonWithName2.jsonld")
    },
    test(
      "do a Gravsearch query in which the knora-api:objectType of a property variable is inferred from a FILTER (with type inference)",
    ) {
      val query =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?letter beol:creationDate ?date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |} WHERE {
          |    ?letter beol:creationDate ?date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "letterWithPersonWithoutName.jsonld")
    },
    test(
      "do a Gravsearch query that finds all the books that have a page with seqnum 100, inferring types (with type inference)",
    ) {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |  FILTER(?seqnum = 100)
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "booksWithPage100.jsonld")
    },
    test(
      "do a Gravsearch query that finds all the letters sent by someone called Meier, ordered by date, inferring types (with type inference)",
    ) {
      val query =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |  FILTER(?name = "Meier")
          |
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "lettersByMeier.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer (in the complex schema) (with type inference)",
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
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have the dcterms:title 'Zeitglöcklein des Lebens' returning the title in the answer (in the complex schema) (with type inference)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX dcterms: <http://purl.org/dc/terms/>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book dcterms:title ?title .
          |
          |} WHERE {
          |    ?book a incunabula:book .
          |
          |    ?book dcterms:title ?title .
          |
          |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer (in the simple schema) (with type inference)",
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
          |}""".stripMargin
      verifyQueryResult(query, "ZeitgloeckleinExtendedSearchWithTitleInAnswerSimple.jsonld", addSimpleSchemaHeader)
    },
    test(
      "perform a Gravsearch query for books that have the dcterms:title 'Zeitglöcklein des Lebens' returning the title in the answer (in the simple schema) (with type inference)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX dcterms: <http://purl.org/dc/terms/>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book dcterms:title ?title .
          |
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book dcterms:title ?title .
          |
          |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "ZeitgloeckleinExtendedSearchWithTitleInAnswerSimple.jsonld", addSimpleSchemaHeader)
    },
    test(
      "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' not returning the title in the answer (with type inference)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "ZeitgloeckleinExtendedSearchNoTitleInAnswer.jsonld")
    },
    test(
      "perform a Gravsearch query for books that do not have the title 'Zeitglöcklein des Lebens' (with type inference)",
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
          |    FILTER(?title != "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "NotZeitgloeckleinExtendedSearch.jsonld")
    },
    test(
      "perform a Gravsearch query for the page of a book whose seqnum equals 10, returning the seqnum and the link value (with type inference)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |    FILTER(?seqnum = 10)
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "PageWithSeqnum10WithSeqnumAndLinkValueInAnswer.jsonld")
    },
    test(
      "perform a Gravsearch count query for the page of a book whose seqnum equals 10, returning the seqnum and the link value (with type inference)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |    FILTER(?seqnum = 10)
          |
          |}
          |""".stripMargin
      for {
        response <- TestApiClient.postSparql(uri"/v2/searchextended", query)
        jsonLd   <- response.assert200
        _        <- ZIO.attempt(checkSearchResponseNumberOfResults(jsonLd, 1))
      } yield assertCompletes
    },
    test(
      "perform a Gravsearch query for the page of a book whose seqnum equals 10, returning only the seqnum (with type inference)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?page knora-api:isMainResource true .
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
          |    FILTER(?seqnum = 10)
          |
          |}""".stripMargin
      verifyQueryResult(query, "PageWithSeqnum10OnlySeqnuminAnswer.jsonld")
    },
    test(
      "perform a Gravsearch query for the pages of a book whose seqnum is lower than or equals 10 (with type inference)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |    FILTER(?seqnum <= 10)
          |
          |} ORDER BY ?seqnum
          |""".stripMargin
      verifyQueryResult(query, "PagesOfLatinNarrenschiffWithSeqnumLowerEquals10.jsonld")
    },
    test(
      "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum (with type inference)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |} ORDER BY ?seqnum
          |""".stripMargin
      verifyQueryResult(query, "PagesOfNarrenschiffOrderedBySeqnum.jsonld")
    },
    test(
      "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum and get the next OFFSET (with type inference)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |} ORDER BY ?seqnum
          |OFFSET 1
          |""".stripMargin
      verifyQueryResult(query, "PagesOfNarrenschiffOrderedBySeqnumNextOffset.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have been published on the first of March 1497 (Julian Calendar) (2) (with type inference)",
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
          |    ?book incunabula:pubdate ?pubdate .
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |
          |    FILTER(?pubdate = "JULIAN:1497-03-01"^^knora-api:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksPublishedOnDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have not been published on the first of March 1497 (Julian Calendar) (with type inference)",
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
          |    ?book incunabula:pubdate ?pubdate .
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |
          |    FILTER(?pubdate != "JULIAN:1497-03-01"^^knora-api:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksNotPublishedOnDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have not been published on the first of March 1497 (Julian Calendar) 2 (with type inference)",
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
          |    ?book incunabula:pubdate ?pubdate .
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |
          |    FILTER(?pubdate < "JULIAN:1497-03-01"^^knora-api:Date || ?pubdate > "JULIAN:1497-03-01"^^knora-api:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksNotPublishedOnDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have not been published on the first of March 1497 (Julian Calendar) 2 (with type inference)",
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
          |    ?book incunabula:pubdate ?pubdate .
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |
          |    FILTER(?pubdate < "JULIAN:1497-03-01"^^knora-api:Date || ?pubdate > "JULIAN:1497-03-01"^^knora-api:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksNotPublishedOnDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have been published 1497 or later (Julian Calendar) (with type inference)",
    ) {
      val query =
        """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(?pubdate >= "JULIAN:1497"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      verifyQueryResult(query, "BooksPublishedAfterOrOnDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have been published after 1497 (Julian Calendar) (with type inference)",
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
          |    ?book incunabula:pubdate ?pubdate .
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |
          |    FILTER(?pubdate > "JULIAN:1497"^^knora-api:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksPublishedAfterDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have been published 1497 or before (Julian Calendar) (with type inference)",
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
          |    ?book incunabula:pubdate ?pubdate .
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |
          |    FILTER(?pubdate <= "JULIAN:1497"^^knora-api:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksPublishedBeforeOrOnDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have been published after 1486 and before 1491 (Julian Calendar) (with type inference)",
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
          |    ?book incunabula:pubdate ?pubdate .
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |
          |    FILTER(?pubdate > "JULIAN:1486"^^knora-api:Date && ?pubdate < "JULIAN:1491"^^knora-api:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksPublishedBetweenDates.jsonld")
    },
    test("get the regions belonging to a page (with type inference)") {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?region knora-api:isMainResource true .
          |
          |    ?region knora-api:isRegionOf <http://rdfh.ch/0803/9d626dc76c03> .
          |
          |    ?region knora-api:hasGeometry ?geom .
          |
          |    ?region knora-api:hasComment ?comment .
          |
          |    ?region knora-api:hasColor ?color .
          |} WHERE {
          |
          |    ?region a knora-api:Region .
          |
          |    ?region knora-api:isRegionOf <http://rdfh.ch/0803/9d626dc76c03> .
          |
          |    ?region knora-api:hasGeometry ?geom .
          |
          |    ?region knora-api:hasComment ?comment .
          |
          |    ?region knora-api:hasColor ?color .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "RegionsForPage.jsonld")
    },
    test(
      "get a book a page points to and include the page in the results (all properties present in WHERE clause) (with type inference)",
    ) {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:isPartOf ?book .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:seqnum ?seqnum .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:hasStillImageFile ?file .
          |
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:isPartOf ?book .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:seqnum ?seqnum .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:hasStillImageFile ?file .
          |
          |} OFFSET 0
          |""".stripMargin
      verifyQueryResult(query, "bookWithIncomingPagesWithAllRequestedProps.jsonld")
    },
  )
}
