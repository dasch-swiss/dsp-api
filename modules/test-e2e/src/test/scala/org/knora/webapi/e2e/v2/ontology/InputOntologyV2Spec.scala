/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2.ontology

import zio.ZIO
import zio.test.*
import zio.test.Assertion.*

import java.time.Instant

import dsp.errors.BadRequestException
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.e2e.v2.ontology
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologyMetadataV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages.PredicateInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.PropertyInfoContentV2
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ZeroOrOne

/**
 * Tests [[InputOntologyV2]].
 */
object InputOntologyV2Spec extends ZIOSpecDefault {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getInitializedTestInstance

  private val PropertyDef = InputOntologyV2(
    ontologyMetadata = OntologyMetadataV2(
      ontologyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri,
      label = None,
      lastModificationDate = Some(Instant.parse("2017-12-19T15:23:42.166Z")),
    ),
    properties = Map(
      "http://0.0.0.0:3333/ontology/0001/anything/v2#hasName".toSmartIri -> PropertyInfoContentV2(
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
      ),
    ),
  )

  private val ClassDef = InputOntologyV2(
    classes = Map(
      "http://0.0.0.0:3333/ontology/0001/anything/v2#WildThing".toSmartIri -> ClassInfoContentV2(
        predicates = Map(
          "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
            predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            objects = Seq(SmartIriLiteralV2("http://www.w3.org/2002/07/owl#Class".toSmartIri)),
          ),
          "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
            predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
            objects = Seq(StringLiteralV2.from("wild thing", Some("en"))),
          ),
          "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
            predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
            objects = Seq(StringLiteralV2.from("A thing that is wild", Some("en"))),
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
      ),
    ),
    ontologyMetadata = OntologyMetadataV2(
      ontologyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri,
      lastModificationDate = Some(Instant.parse("2017-12-19T15:23:42.166Z")),
    ),
    properties = Map(),
  )

  val spec = suite("InputOntologyV2")(
    test("parse a property definition") {

      val params =
        """
          |{
          |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
          |  "@type" : "owl:Ontology",
          |  "knora-api:lastModificationDate" : {
          |    "@type" : "xsd:dateTimeStamp",
          |    "@value" : "2017-12-19T15:23:42.166Z"
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

      val actual = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
      assertTrue(actual == PropertyDef)
    },
    test("parse a class definition") {
      val params =
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

      val actual = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
      assertTrue(actual == ClassDef)
    },
    test("reject an entity definition with an invalid IRI") {

      val params =
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

      for {
        exit <- ZIO.attempt(InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params))).exit
      } yield assert(exit)(failsWithA[BadRequestException])
    },
  )
}
