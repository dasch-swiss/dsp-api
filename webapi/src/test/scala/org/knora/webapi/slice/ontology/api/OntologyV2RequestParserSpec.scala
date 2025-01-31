/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api
import zio.*
import zio.test.*
import zio.test.check

import java.time.Instant

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.LanguageCode
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ChangeOntologyMetadataRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.CreateClassRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.CreateOntologyRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages.PredicateInfoV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.JsonLdTestUtil.JsonLdTransformations
import org.knora.webapi.slice.common.jena.DatasetOps.*
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ZeroOrOne
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object OntologyV2RequestParserSpec extends ZIOSpecDefault {
  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val parser = ZIO.serviceWithZIO[OntologyV2RequestParser]
  private val user   = TestDataFactory.User.rootUser

  private val ontologyIri  = "http://0.0.0.0:3333/ontology/0001/anything/v2"
  private val lastModified = Instant.parse("2017-12-19T15:23:42.166Z")

  private val changeOntologyMetadataRequestV2Suite =
    suite("ChangeOntologyMetadataRequestV2")(
      test("should parse correct jsonLd") {
        val jsonLd: String =
          s"""
             |{
             |  "@id" : "$ontologyIri",
             |  "@type" : "owl:Ontology",
             |  "knora-api:lastModificationDate" : {
             |    "@type" : "xsd:dateTimeStamp",
             |    "@value" : "2017-12-19T15:23:42.166Z"
             |  },
             |  "rdfs:label" : {
             |    "@language" : "en",
             |    "@value" : "Some Label"
             |  },
             |  "rdfs:comment" : {
             |    "@language" : "en",
             |    "@value" : "Some Comment"
             |  },
             |  "@context" : {
             |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "owl" : "http://www.w3.org/2002/07/owl#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
             |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
             |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
             |  }
             |}
             |""".stripMargin

        check(JsonLdTransformations.allGen) { t =>
          for {
            uuid <- Random.nextUUID
            req  <- parser(_.changeOntologyMetadataRequestV2(t(jsonLd), uuid, user))
          } yield assertTrue(
            req == ChangeOntologyMetadataRequestV2(
              ontologyIri.toSmartIri,
              Some("Some Label"),
              Some("Some Comment"),
              lastModified,
              uuid,
              user,
            ),
          )
        }
      },
      test("should parse correct jsonLd without optional fields") {
        val instant = Instant.parse("2017-12-19T15:23:42.166Z")
        val jsonLd: String =
          s"""
             |{
             |  "@id" : "$ontologyIri",
             |  "@type" : "owl:Ontology",
             |  "knora-api:lastModificationDate" : {
             |    "@type" : "xsd:dateTimeStamp",
             |    "@value" : "2017-12-19T15:23:42.166Z"
             |  },
             |  "@context" : {
             |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "owl" : "http://www.w3.org/2002/07/owl#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
             |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
             |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
             |  }
             |}
             |""".stripMargin

        check(JsonLdTransformations.allGen) { t =>
          for {
            uuid <- Random.nextUUID
            req  <- parser(_.changeOntologyMetadataRequestV2(t(jsonLd), uuid, user))
          } yield assertTrue(
            req == ChangeOntologyMetadataRequestV2(
              ontologyIri.toSmartIri,
              None,
              None,
              instant,
              uuid,
              user,
            ),
          )
        }
      },
    )

  private val createOntologySuite = suite("CreateOntologyRequestV2")(test("should succeed") {
    val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
    val reqStr: String =
      s"""
         |{
         |  "knora-api:ontologyName": "useless",
         |  "knora-api:attachedToProject": {
         |    "@id": "$projectIri"
         |  },
         |  "knora-api:isShared": true,
         |  "rdfs:label": "Label",
         |  "rdfs:comment": "Comment",
         |  "@context": {
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
         |  }
         |}""".stripMargin
    for {
      uuid   <- Random.nextUUID
      actual <- parser(_.createOntologyRequestV2(reqStr, uuid, user))
    } yield assertTrue(
      actual == CreateOntologyRequestV2("useless", projectIri, true, "Label", Some("Comment"), uuid, user),
    )
  })

  private val classDef = ClassInfoContentV2(
    predicates = Map(
      "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
        predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
        objects = Seq(SmartIriLiteralV2("http://www.w3.org/2002/07/owl#Class".toSmartIri)),
      ),
      "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
        predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
        objects = Seq(
          StringLiteralV2.from("wild thing", LanguageCode.EN),
          StringLiteralV2.from("Wildes Ding", LanguageCode.DE),
        ),
      ),
      "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
        predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
        objects = Seq(
          StringLiteralV2.from("A thing that is wild", LanguageCode.EN),
          StringLiteralV2.from("Ein valides Ding", LanguageCode.DE),
        ),
      ),
    ),
    classIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#WildThing".toSmartIri,
    ontologySchema = ApiV2Complex,
    directCardinalities = Map(
      "http://0.0.0.0:3333/ontology/0001/anything/v2#hasName".toSmartIri -> KnoraCardinalityInfo(
        ZeroOrOne,
      ),
    ),
    subClassOf = Set("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri),
  )

  private val createClassRequest = suite("CreateClassRequest")(
    test("should parse correct jsonLd") {
      val jsonLd: String =
        s"""
           |{
           |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "2017-12-19T15:23:42.166Z"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:WildThing",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : [
           |      {
           |        "@language" : "en",
           |        "@value" : "wild thing"
           |      },
           |      {
           |        "@language" : "de",
           |        "@value" : "Wildes Ding"
           |      }
           |    ],
           |    "rdfs:comment" :  [
           |      {
           |        "@language" : "en",
           |        "@value" : "A thing that is wild"
           |      },
           |      {
           |        "@language" : "de",
           |        "@value" : "Ein valides Ding"
           |      }
           |    ],
           |    "rdfs:subClassOf" : [ {
           |      "@id" : "anything:Thing"
           |    }, {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality": 1,
           |      "owl:onProperty": {
           |        "@id" : "anything:hasName"
           |      }
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}
            """.stripMargin

      check(JsonLdTransformations.allGen) { t =>
        for {
          uuid <- Random.nextUUID
          req  <- parser(_.createClassRequestV2(t(jsonLd), uuid, user))
        } yield assertTrue(
          req == CreateClassRequestV2(classDef, lastModified, uuid, user),
        )
      }
    },
    test("reject a definition with an invalid class iri") {
      val jsonLd =
        s"""
           |{
           |  "@id" : "http://0.0.0.0:3333/ontology/0001/incunabula/v2",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "2017-12-19T15:23:42.166Z"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:WildThing",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "wild thing"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "A thing that is wild"
           |    },
           |    "rdfs:subClassOf" : [ {
           |      "@id" : "anything:Thing"
           |    }, {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality": 1,
           |      "owl:onProperty": {
           |        "@id" : "anything:hasName"
           |      }
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}
               """.stripMargin
      check(JsonLdTransformations.allGen) { t =>
        for {
          uuid <- Random.nextUUID
          exit <- parser(_.createClassRequestV2(t(jsonLd), uuid, user)).exit
        } yield assertTrue(
          exit == Exit.fail(
            "Ontology for class 'http://0.0.0.0:3333/ontology/0001/anything/v2#WildThing' does not match " +
              "ontology http://0.0.0.0:3333/ontology/0001/incunabula/v2",
          ),
        )
      }
    },
  )

  val spec =
    suite("OntologyV2RequestParser")(changeOntologyMetadataRequestV2Suite, createOntologySuite, createClassRequest)
      .provide(OntologyV2RequestParser.layer, IriConverter.layer, StringFormatter.test)
}
