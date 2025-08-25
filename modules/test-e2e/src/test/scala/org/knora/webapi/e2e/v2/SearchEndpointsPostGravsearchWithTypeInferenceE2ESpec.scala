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
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.testservices.RequestsUpdates.addSimpleSchemaHeader
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
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
        response <- postGravsearchQuery(query)
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
    test(
      "get a book a page points to and only include the page's partOf link in the results (none of the other properties) (with type inference)",
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
      verifyQueryResult(query, "bookWithIncomingPagesOnlyLink.jsonld")
    },
    test("get incoming links pointing to an incunbaula:book, excluding isPartOf (with type inference)") {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |
          |     ?incomingRes knora-api:isMainResource true .
          |
          |     ?incomingRes ?incomingProp <http://rdfh.ch/0803/8be1b7cf7103> .
          |
          |} WHERE {
          |
          |     ?incomingRes ?incomingProp <http://rdfh.ch/0803/8be1b7cf7103> .
          |
          |     <http://rdfh.ch/0803/8be1b7cf7103> a incunabula:book .
          |
          |
          |     FILTER NOT EXISTS {
          |         ?incomingRes knora-api:isPartOf <http://rdfh.ch/0803/8be1b7cf7103> .
          |     }
          |
          |} OFFSET 0
          |""".stripMargin
      verifyQueryResult(query, "IncomingLinksForBook.jsonld")
    },
    test("search for an anything:Thing that has a decimal value of 2.1 2 (with type inference)") {
      val query =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasDecimal ?decimal .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |
          |     ?thing anything:hasDecimal ?decimal .
          |
          |     FILTER(?decimal = "2.1"^^xsd:decimal)
          |}
          |""".stripMargin
      verifyQueryResult(query, "ThingEqualsDecimal.jsonld", anythingUser1)
    },
    test("search for an anything:Thing that has a decimal value bigger than 2.0 (with type inference)") {
      val query =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasDecimal ?decimal .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |
          |     ?thing anything:hasDecimal ?decimal .
          |
          |     FILTER(?decimal > "2"^^xsd:decimal)
          |}
          |""".stripMargin
      verifyQueryResult(query, "ThingBiggerThanDecimal.jsonld", anythingUser1)
    },
    test("search for an anything:Thing that has a decimal value smaller than 3.0 (with type inference)") {
      val query =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasDecimal ?decimal .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |
          |     ?thing anything:hasDecimal ?decimal .
          |
          |     FILTER(?decimal < "3"^^xsd:decimal)
          |}
          |""".stripMargin
      verifyQueryResult(query, "ThingSmallerThanDecimal.jsonld", anythingUser1)
    },
    test("search for an anything:Thing that has a Boolean value that is true 2 (with type inference)") {
      val query =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |
          |     FILTER(?boolean = true)
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "ThingWithBoolean.jsonld", anythingUser1)
    },
    test("search for an anything:Thing that may have a Boolean value that is true (with type inference)") {
      // set OFFSET to 1 to get "Testding for extended search"
      val query =
        """PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |     ?thing a knora-api:Resource .
          |
          |     OPTIONAL {
          |         ?thing anything:hasBoolean ?boolean .
          |         FILTER(?boolean = true)
          |     }
          |
          |     MINUS {
          |         ?thing anything:hasInteger ?intVal .
          |         FILTER(?intVal = 123454321 || ?intVal = 999999999)
          |     }
          |} OFFSET 1""".stripMargin
      verifyQueryResult(query, "ThingWithBooleanOptionalOffset1.jsonld", anythingUser1)
    },
    test(
      "search for an anything:Thing that either has a Boolean value that is true or a decimal value that equals 2.1 (or both) (with type inference)",
    ) {
      val query =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |
          |     ?thing anything:hasDecimal ?decimal .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |     ?thing a knora-api:Resource .
          |
          |     {
          |         ?thing anything:hasBoolean ?boolean .
          |
          |         FILTER(?boolean = true)
          |     } UNION {
          |         ?thing anything:hasDecimal ?decimal .
          |
          |         FILTER(?decimal = "2.1"^^xsd:decimal)
          |     }
          |
          |} OFFSET 0
          |""".stripMargin
      verifyQueryResult(query, "ThingWithBooleanOrDecimal.jsonld", anythingUser1)
    },
    test("search for a book whose title contains 'Zeit' using the regex function (with type inference)") {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |
          |   ?mainRes knora-api:isMainResource true .
          |
          |   ?mainRes incunabula:title ?propVal0 .
          |
          |} WHERE {
          |
          |   ?mainRes a incunabula:book .
          |
          |   ?mainRes incunabula:title ?propVal0 .
          |
          |   FILTER regex(?propVal0, "Zeit", "i")
          |
          |}""".stripMargin
      verifyQueryResult(query, "BooksWithTitleContainingZeit.jsonld", anythingUser1)
    },
    test("search for a book whose title contains 'Zeitglöcklein' using the match function (with type inference)") {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |
          |   ?mainRes knora-api:isMainResource true .
          |
          |   ?mainRes incunabula:title ?propVal0 .
          |
          |} WHERE {
          |
          |   ?mainRes a incunabula:book .
          |
          |   ?mainRes incunabula:title ?propVal0 .
          |
          |   FILTER knora-api:matchText(?propVal0, "Zeitglöcklein")
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "BooksWithTitleContainingZeitgloecklein.jsonld", anythingUser1)
    },
    test(
      "search for a book whose title contains 'Zeitglöcklein' and 'Lebens' using the match function (with type inference)",
    ) {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |
          |    ?mainRes knora-api:isMainResource true .
          |
          |    ?mainRes incunabula:title ?propVal0 .
          |
          |} WHERE {
          |
          |    ?mainRes a incunabula:book .
          |
          |    ?mainRes incunabula:title ?propVal0 .
          |
          |    FILTER knora-api:matchText(?propVal0, "Zeitglöcklein AND Lebens")
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "BooksWithTitleContainingZeitgloecklein.jsonld", anythingUser1)
    },
    test("search for 'Zeitglöcklein des Lebens' using dcterms:title (with type inference)") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX dcterms: <http://purl.org/dc/terms/>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book dcterms:title ?title .
          |
          |} WHERE {
          |    ?book a knora-api:Resource .
          |
          |    ?book dcterms:title ?title .
          |
          |    FILTER(?title = 'Zeitglöcklein des Lebens und Leidens Christi')
          |
          |} OFFSET 0
          |""".stripMargin
      verifyQueryResult(query, "BooksWithTitleContainingZeitgloecklein.jsonld", anythingUser1)
    },
    test("search for an anything:Thing with a list value (with type inference)") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |
          |    ?thing anything:hasListItem ?listItem .
          |
          |} WHERE {
          |    ?thing a anything:Thing .
          |
          |    ?thing anything:hasListItem ?listItem .
          |
          |} OFFSET 0
          |""".stripMargin
      verifyQueryResult(query, "ThingWithListValue.jsonld", anythingUser1)
    },
    test("search for a text using the lang function (with type inference)") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasText ?text .
          |} WHERE {
          |     ?thing a anything:Thing .
          |
          |     ?thing anything:hasText ?text .
          |
          |     FILTER(lang(?text) = "fr")
          |}
          |""".stripMargin
      verifyQueryResult(query, "LanguageFulltextSearch.jsonld", anythingUser1)
    },
    test("search for a specific text using the lang function (with type inference)") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasText ?text .
          |} WHERE {
          |     ?thing a anything:Thing .
          |
          |     ?thing anything:hasText ?text .
          |
          |     FILTER(lang(?text) = "fr" && ?text = "Bonjour")
          |}
          |""".stripMargin
      verifyQueryResult(query, "LanguageFulltextSearch.jsonld", anythingUser1)
    },
    test("do a Gravsearch query for link objects that link to an incunabula book (with type inference)") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |     ?linkObj knora-api:isMainResource true .
          |
          |     ?linkObj knora-api:hasLinkTo ?book .
          |
          |} WHERE {
          |     ?linkObj a knora-api:LinkObj .
          |
          |     ?linkObj knora-api:hasLinkTo ?book .
          |
          |     ?book a incunabula:book .
          |
          |     ?book incunabula:title ?title .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "LinkObjectsToBooks.jsonld", anythingUser1)
    },
    test(
      "do a Gravsearch query for a letter that links to a specific person via two possible properties (with type inference)",
    ) {
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
          |    ?letter a beol:letter .
          |
          |    ?letter beol:creationDate ?date .
          |
          |    # testperson2
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "letterWithAuthor.jsonld", anythingUser1)
    },
    test("do a Gravsearch query for a letter that links to a person with a specified name (with type inference)") {
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
          |    ?letter a beol:letter .
          |
          |    ?letter beol:creationDate ?date .
          |
          |    ?letter ?linkingProp1 ?person1 .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |    ?person1 beol:hasFamilyName ?name .
          |
          |    FILTER(?name = "Meier")
          |
          |
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "letterWithPersonWithName.jsonld", anythingUser1)
    },
    test(
      "do a Gravsearch query for a letter that links to another person with a specified name (with type inference)",
    ) {
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
          |    ?letter a beol:letter .
          |
          |    ?letter beol:creationDate ?date .
          |
          |    ?letter ?linkingProp1 ?person1 .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |    ?person1 beol:hasFamilyName ?name .
          |
          |    FILTER(?name = "Muster")
          |
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "letterWithPersonWithName2.jsonld")
    },
    test("run a Gravsearch query that searches for a person using foaf classes and propertie (with type inference)") {
      val query =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX foaf: <http://xmlns.com/foaf/0.1/>
          |
          |CONSTRUCT {
          |    ?person knora-api:isMainResource true .
          |
          |    ?person foaf:familyName ?familyName .
          |
          |    ?person foaf:givenName ?givenName .
          |
          |} WHERE {
          |    ?person a knora-api:Resource .
          |    ?person a foaf:Person .
          |
          |    ?person foaf:familyName ?familyName .
          |
          |    ?familyName a xsd:string .
          |
          |    ?person foaf:givenName ?givenName .
          |
          |    ?givenName a xsd:string .
          |
          |    FILTER(?familyName = "Meier")
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "foafPerson.jsonld", anythingUser1)
    },
    test("run a Gravsearch query that searches for a single resource specified by its IRI (with type inference)") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true ;
          |         anything:hasText ?text ;
          |         anything:hasInteger ?integer .
          |
          |} WHERE {
          |     BIND(<http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw> AS ?thing)
          |
          |     ?thing a anything:Thing .
          |     ?thing anything:hasText ?text .
          |     ?thing anything:hasInteger ?integer .
          |}
          |""".stripMargin
      verifyQueryResult(query, "ThingByIriWithRequestedValues.jsonld", anythingUser1)
    },
    test(
      "do a Gravsearch query for a letter and get information about the persons associated with it (with type inference)",
    ) {
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
          |    ?letter ?linkingProp1 ?person1 .
          |
          |    ?person1 beol:hasFamilyName ?familyName .
          |
          |
          |} WHERE {
          |    BIND(<http://rdfh.ch/0801/_B3lQa6tSymIq7_7SowBsA> AS ?letter)
          |    ?letter a beol:letter .
          |
          |    ?letter beol:creationDate ?date .
          |
          |    # testperson2
          |    ?letter ?linkingProp1 ?person1 .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |    ?person1 beol:hasFamilyName ?familyName .
          |
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "letterWithAuthorWithInformation.jsonld")
    },
    test(
      "do a Gravsearch query for the pages of a book whose seqnum is lower than or equals 10, with the book as the main resource (with type inference)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |    ?book incunabula:title ?title .
          |
          |    ?page knora-api:isPartOf ?book ;
          |        incunabula:seqnum ?seqnum .
          |} WHERE {
          |    BIND(<http://rdfh.ch/0803/b6b5ff1eb703> AS ?book)
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |
          |    FILTER(?seqnum <= 10)
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "incomingPagesForBook.jsonld", incunabulaMemberUser)
    },
    test(
      "reject a Gravsearch query containing a statement whose subject is not the main resource and whose object is used in ORDER BY (with type inference)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |    ?book incunabula:title ?title .
          |
          |    ?page knora-api:isPartOf ?book ;
          |        incunabula:seqnum ?seqnum .
          |} WHERE {
          |    BIND(<http://rdfh.ch/0803/b6b5ff1eb703> AS ?book)
          |
          |    ?book incunabula:title ?title .
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |
          |    FILTER(?seqnum <= 10)
          |
          |} ORDER BY ?seqnum
          |""".stripMargin
      postGravsearchQuery(query, Some(incunabulaMemberUser)).flatMap(_.assert400).as(assertCompletes)
    },
    test(
      "do a Gravsearch query for regions that belong to pages that are part of a book with the title 'Zeitglöcklein des Lebens und Leidens Christi (with type inference)'",
    ) {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?region knora-api:isMainResource true .
          |
          |    ?region knora-api:isRegionOf ?page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?book incunabula:title ?title .
          |
          |} WHERE {
          |	   ?region a knora-api:Region .
          |
          |	   ?region knora-api:isRegionOf ?page .
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "regionsOfZeitgloecklein.jsonld", incunabulaMemberUser)
    },
    test("do a Gravsearch query containing a UNION nested in an OPTIONAL (with type inference)") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX gravsearchtest1: <http://0.0.0.0:3333/ontology/0666/gravsearchtest1/simple/v2#>
          |
          |CONSTRUCT {
          |  ?Project knora-api:isMainResource true .
          |  ?isInProject gravsearchtest1:isInProject ?Project .
          |} WHERE {
          |  ?Project a gravsearchtest1:Project .
          |
          |  OPTIONAL {
          |    ?isInProject gravsearchtest1:isInProject ?Project .
          |    { ?isInProject a gravsearchtest1:BibliographicNotice . } UNION { ?isInProject a gravsearchtest1:Person . }
          |  }
          |}
          |""".stripMargin
      verifyQueryResult(query, "ProjectsWithOptionalPersonOrBiblio.jsonld")
    },
  )
}
