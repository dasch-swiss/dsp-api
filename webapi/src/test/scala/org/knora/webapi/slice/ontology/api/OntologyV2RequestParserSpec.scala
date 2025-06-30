/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api
import eu.timepit.refined.types.string.NonEmptyString
import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.time.Instant

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.LanguageCode
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ChangeClassLabelsOrCommentsRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.CreateOntologyRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.CreatePropertyRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LabelOrComment
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages.PredicateInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.PropertyInfoContentV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.JsonLdTestUtil.JsonLdTransformations
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ZeroOrOne

object OntologyV2RequestParserSpec extends ZIOSpecDefault {
  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val parser = ZIO.serviceWithZIO[OntologyV2RequestParser]
  private val user   = TestDataFactory.User.rootUser

  private val ontologyIri  = OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
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
              ontologyIri,
              Some("Some Label"),
              Some(NonEmptyString.unsafeFrom("Some Comment")),
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
              ontologyIri,
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

  private val createOntologySuite = suite("CreateOntologyRequestV2")(
    test("should succeed") {
      val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
      val comment    = NonEmptyString.unsafeFrom("Comment")
      val reqStr: String =
        s"""
           |{
           |  "knora-api:ontologyName": "useless",
           |  "knora-api:attachedToProject": {
           |    "@id": "$projectIri"
           |  },
           |  "knora-api:isShared": true,
           |  "rdfs:label": "Label",
           |  "rdfs:comment": "${comment.value}",
           |  "@context": {
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
           |  }
           |}""".stripMargin
      for {
        uuid   <- Random.nextUUID
        actual <- parser(_.createOntologyRequestV2(reqStr, uuid, user))
      } yield assertTrue(
        actual == CreateOntologyRequestV2("useless", projectIri, true, "Label", Some(comment), uuid, user),
      )
    },
    test("should reject an empty comment") {
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
           |  "rdfs:comment": "",
           |  "@context": {
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
           |  }
           |}""".stripMargin
      for {
        uuid   <- Random.nextUUID
        either <- parser(_.createOntologyRequestV2(reqStr, uuid, user)).either
      } yield assertTrue(either == Left("Ontology comment may not be empty"))
    },
  )

  private val classDef = ClassInfoContentV2(
    predicates = Map(
      "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
        predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
        objects = Seq(SmartIriLiteralV2("http://www.w3.org/2002/07/owl#Class".toSmartIri)),
      ),
      "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
        predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
        objects = Seq(
          StringLiteralV2.from("An English label", LanguageCode.EN),
          StringLiteralV2.from("Ein deutsches Label", LanguageCode.DE),
        ),
      ),
      "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
        predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
        objects = Seq(
          StringLiteralV2.from("An English comment", LanguageCode.EN),
          StringLiteralV2.from("Ein deutscher Kommentar", LanguageCode.DE),
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
           |        "@value" : "An English label"
           |      },
           |      {
           |        "@language" : "de",
           |        "@value" : "Ein deutsches Label"
           |      }
           |    ],
           |    "rdfs:comment" :  [
           |      {
           |        "@language" : "en",
           |        "@value" : "An English comment"
           |      },
           |      {
           |        "@language" : "de",
           |        "@value" : "Ein deutscher Kommentar"
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
    test("should allow an external subclassOf IRI") {
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
           |    "rdfs:subClassOf" : { "@id" : "external:Ext" }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
           |    "external" : "http://example.com#"
           |  }
           |}
            """.stripMargin

      check(JsonLdTransformations.allGen) { t =>
        for {
          uuid <- Random.nextUUID
          req  <- parser(_.createClassRequestV2(t(jsonLd), uuid, user))
        } yield assert(req.classInfoContent.subClassOf.map(_.toIri))(hasSameElements(List("http://example.com#Ext")))
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
           |      "@value" : "An English Label"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "An English Comment"
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

  private val changeClassLabelOrCommentSuite = suite("ChangeClassLabelOrCommentRequestV2")(
    test("should update label") {
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
           |        "@value" : "An English label"
           |      },
           |      {
           |        "@language" : "de",
           |        "@value" : "Ein deutsches Label"
           |      }
           |    ]
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
          uuid   <- Random.nextUUID
          actual <- parser(_.changeClassLabelsOrCommentsRequestV2(t(jsonLd), uuid, user))
        } yield assertTrue(
          actual == ChangeClassLabelsOrCommentsRequestV2(
            ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#WildThing".toSmartIri),
            LabelOrComment.Label,
            Seq(
              StringLiteralV2.from("Ein deutsches Label", LanguageCode.DE),
              StringLiteralV2.from("An English label", LanguageCode.EN),
            ),
            lastModified,
            uuid,
            user,
          ),
        )
      }
    },
    test("should update comment") {
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
           |    "rdfs:comment" : [
           |      {
           |        "@language" : "en",
           |        "@value" : "An English comment"
           |      },
           |      {
           |        "@language" : "de",
           |        "@value" : "Ein deutscher Kommentar"
           |      }
           |    ]
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
          uuid   <- Random.nextUUID
          actual <- parser(_.changeClassLabelsOrCommentsRequestV2(t(jsonLd), uuid, user))
        } yield assertTrue(
          actual == ChangeClassLabelsOrCommentsRequestV2(
            ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#WildThing".toSmartIri),
            LabelOrComment.Comment,
            Seq(
              StringLiteralV2.from("Ein deutscher Kommentar", LanguageCode.DE),
              StringLiteralV2.from("An English comment", LanguageCode.EN),
            ),
            lastModified,
            uuid,
            user,
          ),
        )
      }
    },
  )

  private val propertySuite = suite("property requests")(
    suite("createProperty")(
      test("should parse a valid request") {
        val jsonLd =
          s"""
             |{
             |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
             |  "@type" : "owl:Ontology",
             |  "knora-api:lastModificationDate" : {
             |    "@type" : "xsd:dateTimeStamp",
             |    "@value" : "$lastModified"
             |  },
             |  "@graph" : [ {
             |    "@id" : "anything:hasName",
             |    "@type" : "owl:ObjectProperty",
             |    "knora-api:subjectType" : {
             |      "@id" : "anything:Thing"
             |    },
             |    "knora-api:objectType" : {
             |      "@id" : "knora-api:TextValue"
             |    },
             |    "rdfs:comment" : [ {
             |      "@language" : "en",
             |      "@value" : "The name of a 'Thing'"
             |    }, {
             |      "@language" : "de",
             |      "@value" : "Der Name eines Dinges"
             |    } ],
             |    "rdfs:label" : [ {
             |      "@language" : "en",
             |      "@value" : "has name"
             |    }, {
             |      "@language" : "de",
             |      "@value" : "hat Namen"
             |    } ],
             |    "rdfs:subPropertyOf" : [ {
             |      "@id" : "knora-api:hasValue"
             |    }, {
             |      "@id" : "http://schema.org/name"
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

        val propertyDef = PropertyInfoContentV2(
          propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasName".toSmartIri,
          predicates = Map(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Seq(SmartIriLiteralV2("http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri)),
            ),
            "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri,
              objects = Seq(SmartIriLiteralV2("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri)),
            ),
            "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri,
              objects = Seq(SmartIriLiteralV2("http://api.knora.org/ontology/knora-api/v2#TextValue".toSmartIri)),
            ),
            "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
              objects = Seq(
                StringLiteralV2.from("has name", Some("en")),
                StringLiteralV2.from("hat Namen", Some("de")),
              ),
            ),
            "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
              objects = Seq(
                StringLiteralV2.from("The name of a 'Thing'", Some("en")),
                StringLiteralV2.from("Der Name eines Dinges", Some("de")),
              ),
            ),
          ),
          subPropertyOf = Set(
            "http://api.knora.org/ontology/knora-api/v2#hasValue".toSmartIri,
            "http://schema.org/name".toSmartIri,
          ),
          ontologySchema = ApiV2Complex,
        )
        check(JsonLdTransformations.allGen) { t =>
          for {
            apiRequestId <- Random.nextUUID
            actual       <- parser(_.createPropertyRequestV2(t(jsonLd), apiRequestId, user))
          } yield assertTrue(actual == CreatePropertyRequestV2(propertyDef, lastModified, apiRequestId, user))
        }
      },
    ),
  )

  val spec =
    suite("OntologyV2RequestParser")(
      changeOntologyMetadataRequestV2Suite,
      createOntologySuite,
      createClassRequest,
      changeClassLabelOrCommentSuite,
      propertySuite,
    ).provide(OntologyV2RequestParser.layer, IriConverter.layer, StringFormatter.test)
}
