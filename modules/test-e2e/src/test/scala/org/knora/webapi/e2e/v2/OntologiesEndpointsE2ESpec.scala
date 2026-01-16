/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import sttp.client4.UriContext
import zio.*
import zio.json.*
import zio.test.*

import java.time.Instant
import scala.language.implicitConversions
import scala.util.Random

import dsp.constants.SalsahGui
import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import dsp.valueobjects.LangString
import org.knora.webapi.*
import org.knora.webapi.e2e.v2.ontology.InputOntologyParsingModeV2.TestResponseParsingModeV2
import org.knora.webapi.e2e.v2.ontology.InputOntologyV2
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.OntologyConstants.Owl
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.OntologyConstants.Xsd
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.models.*
import org.knora.webapi.sharedtestdata.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.common.domain.LanguageCode.EN
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.*

object OntologiesEndpointsE2ESpec extends E2EZSpec { self =>

  override val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/project_ontologies/example-box.ttl",
      name = "http://www.knora.org/ontology/shared/example-box",
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/minimal-onto.ttl",
      name = "http://www.knora.org/ontology/0001/minimal",
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest",
    ),
    RdfDataObject(path = "test_data/project_data/freetest-data.ttl", name = "http://www.knora.org/data/0001/freetest"),
    RdfDataObject(
      path = "test_data/project_ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything",
    ),
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
  )

  private case class LastModRef(private var value: Instant) {
    def updateFrom(d: JsonLDDocument): (Instant, Instant) =
      val old = this.value
      this.value = d.body.requireDatatypeValueInObject(
        KA.LastModificationDate,
        Xsd.DateTimeStamp.toSmartIri,
        (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
      )
      (old, this.value)

    override def toString: String = value.toString
  }
  private object LastModRef {
    given Conversion[LastModRef, Instant]        = _.value
    def make: LastModRef                         = LastModRef(Instant.now)
    def unsafeFrom(dateTime: String): LastModRef = LastModRef(Instant.parse(dateTime))
  }

  private val anythingOntoLocalhostIri = SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost
  private val hasOtherNothingIri       = anythingOntoLocalhostIri + "#hasOtherNothing"
  private val hasOtherNothingValueIri  = anythingOntoLocalhostIri + "#hasOtherNothingValue"

  private val fooIri         = new MutableTestIri
  private val barIri         = new MutableTestIri
  private val fooLastModDate = LastModRef.make
  private val barLastModDate = LastModRef.make

  private val anythingLastModDate = LastModRef.unsafeFrom("2017-12-19T15:23:42.166Z")
  private val freetestLastModDate = LastModRef.unsafeFrom("2012-12-12T12:12:12.12Z")

  private val uselessIri         = new MutableTestIri
  private val uselessLastModDate = LastModRef.make

  private def getPropertyIrisFromResourceClassResponse(jsonLd: JsonLDDocument): Set[SmartIri] = {
    val classDef = jsonLd.body
      .getRequiredArray("@graph")
      .fold(msg => throw BadRequestException(msg), identity)
      .value
      .head
      .asInstanceOf[JsonLDObject]

    classDef
      .value(Rdfs.SubClassOf)
      .asInstanceOf[JsonLDArray]
      .value
      .collect {
        case obj: JsonLDObject if !obj.isIri =>
          obj.requireIriInObject(Owl.OnProperty, sf.toSmartIriWithErr)
      }
      .toSet
  }

  override val e2eSpec = suite("The Ontologies v2 Endpoint")(
    test("not allow the user to request the knora-base ontology") {
      val ontologyIri = "http://api.knora.org/ontology/knora-base/v2"
      TestApiClient
        .getJsonLd(uri"/v2/ontologies/allentities/$ontologyIri")
        .flatMap(_.assert400)
        .as(assertCompletes)
    },
    test("not allow the user to request the knora-admin ontology") {
      val ontologyIri = "http://api.knora.org/ontology/knora-admin/v2"
      TestApiClient
        .getJsonLd(uri"/v2/ontologies/allentities/$ontologyIri")
        .flatMap(_.assert400)
        .as(assertCompletes)
    },
    test("create an empty ontology called 'foo' with a project code") {
      val label  = "The foo ontology"
      val params =
        s"""{
           |    "knora-api:ontologyName": "foo",
           |    "knora-api:attachedToProject": {
           |      "@id": "$anythingProjectIri"
           |    },
           |    "rdfs:label": "$label",
           |    "@context": {
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
           |    }
           |}""".stripMargin

      for {
        jsonLd     <- TestApiClient.postJsonLdDocument(uri"/v2/ontologies", params, anythingAdminUser).flatMap(_.assert200)
        metadata    = jsonLd.body
        ontologyIri = metadata.value("@id").asInstanceOf[JsonLDString].value
        _           = fooIri.set(ontologyIri)
        _           = self.fooLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        ontologyIri == "http://0.0.0.0:3333/ontology/0001/foo/v2",
        metadata.value(Rdfs.Label) == JsonLDString(label),
      )
    },
    test("create an empty ontology called 'bar' with a comment") {
      val label   = "The bar ontology"
      val comment = "some comment"
      val params  =
        s"""{
           |    "knora-api:ontologyName": "bar",
           |    "knora-api:attachedToProject": {
           |      "@id": "$anythingProjectIri"
           |    },
           |    "rdfs:label": "$label",
           |    "rdfs:comment": "$comment",
           |    "@context": {
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
           |    }
           |}""".stripMargin

      for {
        jsonLd     <- TestApiClient.postJsonLdDocument(uri"/v2/ontologies", params, anythingAdminUser).flatMap(_.assert200)
        metadata    = jsonLd.body
        ontologyIri = metadata.value("@id").asInstanceOf[JsonLDString].value
        _           = self.barIri.set(ontologyIri)
        _           = self.barLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        ontologyIri == "http://0.0.0.0:3333/ontology/0001/bar/v2",
        metadata.value(Rdfs.Comment) == JsonLDString(Iri.fromSparqlEncodedString(comment)),
      )
    },
    test("create an empty ontology called 'test' with a comment that has a special character") {
      val label   = "The test ontology"
      val comment = "some \\\"test\\\" comment"
      val params  =
        s"""{
           |    "knora-api:ontologyName": "test",
           |    "knora-api:attachedToProject": {
           |      "@id": "$anythingProjectIri"
           |    },
           |    "rdfs:label": "$label",
           |    "rdfs:comment": "$comment",
           |    "@context": {
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
           |    }
           |}""".stripMargin
      for {
        jsonLd     <- TestApiClient.postJsonLdDocument(uri"/v2/ontologies", params, anythingAdminUser).flatMap(_.assert200)
        metadata    = jsonLd.body
        ontologyIri = metadata.value("@id").asInstanceOf[JsonLDString].value
      } yield assertTrue(
        ontologyIri == "http://0.0.0.0:3333/ontology/0001/test/v2",
        metadata.value(Rdfs.Comment) == JsonLDString(Iri.fromSparqlEncodedString(comment)),
      )
    },
    test("change the metadata of 'foo'") {
      val newLabel   = "The modified foo ontology"
      val newComment = "new comment"
      val params     =
        s"""{
           |  "@id": "${fooIri.get}",
           |  "rdfs:label": "$newLabel",
           |  "rdfs:comment": "$newComment",
           |  "knora-api:lastModificationDate": {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$fooLastModDate"
           |  },
           |  "@context": {
           |    "xsd" :  "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
           |  }
           |}""".stripMargin

      for {
        jsonLd <-
          TestApiClient.putJsonLdDocument(uri"/v2/ontologies/metadata", params, anythingAdminUser).flatMap(_.assert200)
        metadata                      = jsonLd.body
        ontologyIri                   = metadata.value("@id").asInstanceOf[JsonLDString].value
        (oldLastModDate, lastModDate) = self.fooLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        ontologyIri == fooIri.get,
        metadata.value(Rdfs.Label) == JsonLDString(newLabel),
        metadata.value(Rdfs.Comment) == JsonLDString(newComment),
        lastModDate.isAfter(oldLastModDate),
      )
    },
    test("change the metadata of 'bar' ontology giving a comment containing a special character") {
      val newComment = "a \\\"new\\\" comment"
      val params     =
        s"""{
           |  "@id": "${barIri.get}",
           |  "rdfs:comment": "$newComment",
           |  "knora-api:lastModificationDate": {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$barLastModDate"
           |  },
           |  "@context": {
           |    "xsd" :  "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
           |  }
           |}""".stripMargin

      for {
        jsonLd <-
          TestApiClient.putJsonLdDocument(uri"/v2/ontologies/metadata", params, anythingAdminUser).flatMap(_.assert200)
        metadata                      = jsonLd.body
        ontologyIri                   = metadata.value("@id").asInstanceOf[JsonLDString].value
        (oldLastModDate, lastModDate) = self.barLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        ontologyIri == barIri.get,
        metadata.value(Rdfs.Comment) == JsonLDString(Iri.fromSparqlEncodedString(newComment)),
        lastModDate.isAfter(oldLastModDate),
      )
    },
    test("delete the comment from 'foo'") {
      for {
        jsonLd <- TestApiClient
                    .deleteJsonLdDocument(
                      uri"/v2/ontologies/comment/$fooIri?lastModificationDate=${self.fooLastModDate}",
                      anythingAdminUser,
                    )
                    .flatMap(_.assert200)
        metadata                 = jsonLd.body
        ontologyIri              = metadata.value("@id").asInstanceOf[JsonLDString].value
        (oldLastMod, newLastMod) = self.fooLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        ontologyIri == fooIri.get,
        metadata.value(Rdfs.Label) == JsonLDString("The modified foo ontology"),
        !metadata.value.contains(Rdfs.Comment),
        newLastMod.isAfter(oldLastMod),
      )
    },
    test("determine that an ontology can be deleted") {
      TestApiClient
        .getJsonLdDocument(uri"/v2/ontologies/candeleteontology/$fooIri", anythingAdminUser)
        .flatMap(_.assert200)
        .map(jsonLd => assertTrue(jsonLd.body.value(KA.CanDo).asInstanceOf[JsonLDBoolean].value))
    },
    test("delete the 'foo' ontology") {
      TestApiClient
        .deleteJsonLdDocument(
          uri"/v2/ontologies/$fooIri?lastModificationDate=${self.fooLastModDate}",
          anythingAdminUser,
        )
        .flatMap(_.assert200)
        .as(assertCompletes)
    },
    test("create a property anything:hasName as a subproperty of knora-api:hasValue and schema:name") {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |      "@id" : "anything:hasName",
           |      "@type" : "owl:ObjectProperty",
           |      "knora-api:subjectType" : {
           |        "@id" : "anything:Thing"
           |      },
           |      "knora-api:objectType" : {
           |        "@id" : "knora-api:TextValue"
           |      },
           |      "rdfs:comment" : [ {
           |        "@language" : "en",
           |        "@value" : "The name of a Thing"
           |      }, {
           |        "@language" : "de",
           |        "@value" : "Der Name eines Dinges"
           |      } ],
           |      "rdfs:label" : [ {
           |        "@language" : "en",
           |        "@value" : "has name"
           |      }, {
           |        "@language" : "de",
           |        "@value" : "hat Namen"
           |      } ],
           |      "rdfs:subPropertyOf" : [ {
           |        "@id" : "knora-api:hasValue"
           |      }, {
           |        "@id" : "http://schema.org/name"
           |      } ],
           |      "salsah-gui:guiElement" : {
           |        "@id" : "salsah-gui:SimpleText"
           |      },
           |      "salsah-gui:guiAttribute" : [ "size=80", "maxlength=100" ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
      for {
        jsonLd <- TestApiClient
                    .postJsonLdDocument(uri"/v2/ontologies/properties", params, anythingAdminUser)
                    .flatMap(_.assert200)
        (oldLastMod, newLastMod) = self.anythingLastModDate.updateFrom(jsonLd)
        responseAsInput          = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
      } yield assertTrue(responseAsInput.properties == paramsAsInput.properties, newLastMod.isAfter(oldLastMod))
    },
    test("change the rdfs:label of a property") {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty",
           |    "rdfs:label" : [ {
           |      "@language" : "en",
           |      "@value" : "has name"
           |    }, {
           |      "@language" : "fr",
           |      "@value" : "a nom"
           |    }, {
           |      "@language" : "de",
           |      "@value" : "hat Namen"
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      val paramsAsInput = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
      for {
        jsonLd <- TestApiClient
                    .putJsonLdDocument(uri"/v2/ontologies/properties", params, anythingAdminUser)
                    .flatMap(_.assert200)
        (oldLastMod, newLastMod) = self.anythingLastModDate.updateFrom(jsonLd)
        responseAsInput          = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
      } yield assertTrue(
        responseAsInput.properties.head._2
          .predicates(Rdfs.Label.toSmartIri)
          .objects
          .toSet == paramsAsInput.properties.head._2.predicates(Rdfs.Label.toSmartIri).objects.toSet,
        newLastMod.isAfter(oldLastMod),
      )
    },
    test("change the rdfs:comment of a property") {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty",
           |    "rdfs:comment" : [ {
           |      "@language" : "en",
           |      "@value" : "The name of a Thing"
           |    }, {
           |      "@language" : "fr",
           |      "@value" : "Le nom d'une chose"
           |    }, {
           |      "@language" : "de",
           |      "@value" : "Der Name eines Dinges"
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      val paramsAsInput = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
      for {
        jsonLd <- TestApiClient
                    .putJsonLdDocument(uri"/v2/ontologies/properties", params, anythingAdminUser)
                    .flatMap(_.assert200)
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, TestResponseParsingModeV2).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        responseAsInput.properties.head._2
          .predicates(Rdfs.Comment.toSmartIri)
          .objects
          .toSet == paramsAsInput.properties.head._2.predicates(Rdfs.Comment.toSmartIri).objects.toSet,
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("add an rdfs:comment to a link property that has no rdfs:comment") {
      val params =
        s"""{
           |    "@id": "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |    "@type": "owl:Ontology",
           |    "knora-api:lastModificationDate": {
           |        "@type": "xsd:dateTimeStamp",
           |        "@value": "$anythingLastModDate"
           |    },
           |    "@graph": [
           |        {
           |            "@id": "anything:hasBlueThing",
           |            "@type": "owl:ObjectProperty",
           |            "rdfs:comment": [
           |                {
           |                    "@language": "en",
           |                    "@value": "asdas asd as dasdasdas"
           |                }
           |            ]
           |        }
           |    ],
           |    "@context": {
           |        "anything": "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#",
           |        "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "owl": "http://www.w3.org/2002/07/owl#",
           |        "xsd": "http://www.w3.org/2001/XMLSchema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
           |        "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#"
           |    }
           |}""".stripMargin

      val paramsAsInput = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
      for {
        jsonLd <- TestApiClient
                    .putJsonLdDocument(uri"/v2/ontologies/properties", params, anythingAdminUser)
                    .flatMap(_.assert200)
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        responseAsInput.properties.head._2.predicates(Rdfs.Comment.toSmartIri).objects ==
          paramsAsInput.properties.head._2.predicates(Rdfs.Comment.toSmartIri).objects,
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("delete the rdfs:comment of a property") {
      val property = "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasPropertyWithComment2"
      for {
        jsonLd <-
          TestApiClient
            .deleteJsonLdDocument(
              uri"/v2/ontologies/properties/comment/$property?lastModificationDate=$freetestLastModDate",
              anythingAdminUser,
            )
            .flatMap(_.assert200)
        (oldFreetestLastModDate, newFreetestLastModDate) = self.freetestLastModDate.updateFrom(jsonLd)
        expectedResponse                                 =
          s"""{
             |   "knora-api:lastModificationDate": {
             |       "@value": "$freetestLastModDate",
             |       "@type": "xsd:dateTimeStamp"
             |   },
             |   "rdfs:label": "freetest",
             |   "@graph": [
             |      {
             |         "rdfs:label": [
             |            {
             |               "@value": "Property mit einem Kommentar 2",
             |               "@language": "de"
             |            },
             |            {
             |               "@value": "Property with a comment 2",
             |               "@language": "en"
             |            }
             |         ],
             |         "rdfs:subPropertyOf": {
             |            "@id": "knora-api:hasValue"
             |         },
             |         "@type": "owl:ObjectProperty",
             |         "knora-api:objectType": {
             |            "@id": "knora-api:TextValue"
             |         },
             |         "salsah-gui:guiElement": {
             |            "@id": "salsah-gui:SimpleText"
             |         },
             |         "@id": "freetest:hasPropertyWithComment2"
             |      }
             |   ],
             |   "knora-api:attachedToProject": {
             |      "@id": "http://rdfh.ch/projects/0001"
             |   },
             |   "@type": "owl:Ontology",
             |   "@id": "http://0.0.0.0:3333/ontology/0001/freetest/v2",
             |   "@context": {
             |      "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |      "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
             |      "freetest": "http://0.0.0.0:3333/ontology/0001/freetest/v2#",
             |      "owl": "http://www.w3.org/2002/07/owl#",
             |      "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#",
             |      "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
             |      "xsd": "http://www.w3.org/2001/XMLSchema#"
             |   }
             |}""".stripMargin
        expectedResponseToCompare = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(expectedResponse)).unescape
        responseFromJsonLD        = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
      } yield assertTrue(
        newFreetestLastModDate.isAfter(oldFreetestLastModDate),
        responseFromJsonLD.properties.head._2.predicates.toSet == expectedResponseToCompare.properties.head._2.predicates.toSet,
      )
    },
    test("delete the rdfs:comment of a class") {
      val classIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#BookWithComment2"
      for {
        jsonLd <- TestApiClient
                    .deleteJsonLdDocument(
                      uri"/v2/ontologies/classes/comment/$classIri?lastModificationDate=$freetestLastModDate",
                      anythingAdminUser,
                    )
                    .flatMap(_.assert200)
        (oldFreetestLastModDate, newFreetestLastModDate) = self.freetestLastModDate.updateFrom(jsonLd)
        expectedResponse                                 =
          s"""{
             |   "knora-api:lastModificationDate": {
             |       "@value": "$freetestLastModDate",
             |       "@type": "xsd:dateTimeStamp"
             |   },
             |   "rdfs:label": "freetest",
             |   "@graph": [
             |      {
             |         "rdfs:label": [
             |            {
             |               "@value": "Buch 2 mit Kommentar",
             |               "@language": "de"
             |            },
             |            {
             |               "@value": "Book 2 with comment",
             |               "@language": "en"
             |            }
             |         ],
             |         "rdfs:subClassOf": [
             |            {
             |               "@id": "knora-api:Resource"
             |            }
             |         ],
             |         "@type": "owl:Class",
             |         "@id": "freetest:BookWithComment2"
             |      }
             |   ],
             |   "knora-api:attachedToProject": {
             |      "@id": "http://rdfh.ch/projects/0001"
             |   },
             |   "@type": "owl:Ontology",
             |   "@id": "http://0.0.0.0:3333/ontology/0001/freetest/v2",
             |   "@context": {
             |      "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |      "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
             |      "freetest": "http://0.0.0.0:3333/ontology/0001/freetest/v2#",
             |      "owl": "http://www.w3.org/2002/07/owl#",
             |      "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#",
             |      "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
             |      "xsd": "http://www.w3.org/2001/XMLSchema#"
             |   }
             |}""".stripMargin
        expectedResponseToCompare = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(expectedResponse)).unescape
        responseFromJsonLD        = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
      } yield assertTrue(
        newFreetestLastModDate.isAfter(oldFreetestLastModDate),
        responseFromJsonLD.classes.head._2.predicates.toSet == expectedResponseToCompare.classes.head._2.predicates.toSet,
      )
    },
    test("change the salsah-gui:guiElement and salsah-gui:guiAttribute of a property") {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty",
           |    "salsah-gui:guiElement" : {
           |      "@id" : "salsah-gui:Textarea"
           |    },
           |    "salsah-gui:guiAttribute" : [ "cols=80", "rows=24" ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      val paramsAsInput = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
      for {
        jsonLd <- TestApiClient
                    .putJsonLdDocument(uri"/v2/ontologies/properties/guielement", params, anythingAdminUser)
                    .flatMap(_.assert200)
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        responseAsInput.properties.head._2.predicates(SalsahGui.External.GuiElementProp.toSmartIri).objects.toSet ==
          paramsAsInput.properties.head._2.predicates(SalsahGui.External.GuiElementProp.toSmartIri).objects.toSet,
        responseAsInput.properties.head._2.predicates(SalsahGui.External.GuiAttribute.toSmartIri).objects.toSet ==
          paramsAsInput.properties.head._2.predicates(SalsahGui.External.GuiAttribute.toSmartIri).objects.toSet,
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test(
      "not change the salsah-gui:guiElement and salsah-gui:guiAttribute of a property if their combination is invalid",
    ) {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty",
           |    "salsah-gui:guiElement" : {
           |      "@id" : "salsah-gui:List"
           |    },
           |    "salsah-gui:guiAttribute" : [ "cols=80", "rows=24" ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin
      TestApiClient
        .putJsonLdDocument(uri"/v2/ontologies/properties/guielement", params, anythingAdminUser)
        .flatMap(_.assert400)
        .as(assertCompletes)
    },
    test("remove the salsah-gui:guiElement and salsah-gui:guiAttribute from a property") {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty"
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      for {
        jsonLd <- TestApiClient
                    .putJsonLdDocument(uri"/v2/ontologies/properties/guielement", params, anythingAdminUser)
                    .flatMap(_.assert200)
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        !responseAsInput.properties.head._2.predicates.contains(SalsahGui.External.GuiElementProp.toSmartIri),
        !responseAsInput.properties.head._2.predicates.contains(SalsahGui.External.GuiAttribute.toSmartIri),
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test(
      "create a class anything:WildThing that is a subclass of anything:Thing, with a direct cardinality for anything:hasName",
    ) {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
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
           |    "rdfs:subClassOf" : [
           |      {
           |        "@id": "anything:Thing"
           |      },
           |      {
           |        "@type": "http://www.w3.org/2002/07/owl#Restriction",
           |        "owl:maxCardinality": 1,
           |        "owl:onProperty": {
           |          "@id": "anything:hasName"
           |        },
           |        "salsah-gui:guiOrder": 1
           |      }
           |    ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
      for {
        jsonLd <-
          TestApiClient.postJsonLdDocument(uri"/v2/ontologies/classes", params, anythingAdminUser).flatMap(_.assert200)
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        responseAsInput.classes == paramsAsInput.classes,
        // Check that cardinalities were inherited from anything:Thing.
        getPropertyIrisFromResourceClassResponse(jsonLd).contains(
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri,
        ),
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("create a class anything:Nothing with no properties") {
      val params =
        s"""
           |{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "nothing"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "Represents nothing"
           |    },
           |    "rdfs:subClassOf" : {
           |      "@id" : "knora-api:Resource"
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}
           """.stripMargin

      val paramsAsInput = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
      for {
        jsonLd <-
          TestApiClient.postJsonLdDocument(uri"/v2/ontologies/classes", params, anythingAdminUser).flatMap(_.assert200)
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        responseAsInput.classes == paramsAsInput.classes,
        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(jsonLd).contains(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
        ),
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("change the labels of a class") {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : [ {
           |      "@language" : "en",
           |      "@value" : "nothing"
           |    }, {
           |      "@language" : "fr",
           |      "@value" : "rien"
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
      for {
        jsonLd <-
          TestApiClient.putJsonLdDocument(uri"/v2/ontologies/classes", params, anythingAdminUser).flatMap(_.assert200)
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        responseAsInput.classes.head._2.predicates(Rdfs.Label.toSmartIri).objects ==
          paramsAsInput.classes.head._2.predicates.head._2.objects,
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("change the comments of a class") {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:comment" : [ {
           |      "@language" : "en",
           |      "@value" : "Represents nothing"
           |    }, {
           |      "@language" : "fr",
           |      "@value" : "ne représente rien"
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
      for {
        jsonLd <-
          TestApiClient.putJsonLdDocument(uri"/v2/ontologies/classes", params, anythingAdminUser).flatMap(_.assert200)
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        responseAsInput.classes.head._2.predicates(Rdfs.Comment.toSmartIri).objects ==
          paramsAsInput.classes.head._2.predicates.head._2.objects,
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("create a property anything:hasOtherNothing with knora-api:objectType anything:Nothing") {
      val params =
        s"""
           |{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasOtherNothing",
           |    "@type" : "owl:ObjectProperty",
           |    "knora-api:subjectType" : {
           |      "@id" : "anything:Nothing"
           |    },
           |    "knora-api:objectType" : {
           |      "@id" : "anything:Nothing"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "Refers to the other Nothing of a Nothing"
           |    },
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "has nothingness"
           |    },
           |    "rdfs:subPropertyOf" : {
           |      "@id" : "knora-api:hasLinkTo"
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}
                """.stripMargin

      for {
        jsonLd <- TestApiClient
                    .postJsonLdDocument(uri"/v2/ontologies/properties", params, anythingAdminUser)
                    .flatMap(_.assert200)
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, TestResponseParsingModeV2).unescape
        paramsAsInput                    = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        responseAsInput.properties == paramsAsInput.properties,
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("add a cardinality for the property anything:hasOtherNothing to the class anything:Nothing") {
      val params =
        s"""
           |{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:hasOtherNothing"
           |      }
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}
                """.stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      val paramsWithAddedLinkValueCardinality = paramsAsInput.copy(
        classes = paramsAsInput.classes.map { case (classIri, classDef) =>
          val hasOtherNothingValueCardinality =
            "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothingValue".toSmartIri ->
              classDef.directCardinalities("http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothing".toSmartIri)

          classIri -> classDef.copy(
            directCardinalities = classDef.directCardinalities + hasOtherNothingValueCardinality,
          )
        },
      )

      for {
        jsonLd <- TestApiClient
                    .postJsonLdDocument(uri"/v2/ontologies/cardinalities", params, anythingAdminUser)
                    .flatMap(_.assert200)
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        responseAsInput.classes.head._2.directCardinalities ==
          paramsWithAddedLinkValueCardinality.classes.head._2.directCardinalities,
        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(jsonLd).contains(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
        ),
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("add all IRIs to newly created link value property again") {
      val iri          = SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost
      val expectedIris = Set(
        Rdfs.Comment,
        Rdfs.Label,
        Rdfs.SubPropertyOf,
        KA.IsEditable,
        KA.IsResourceProperty,
        KA.IsLinkValueProperty,
        KA.ObjectType,
        KA.SubjectType,
        "@id",
        "@type",
      )
      for {
        jsonLd              <- TestApiClient.getJsonLdDocument(uri"/v2/ontologies/allentities/$iri").flatMap(_.assert200)
        graph                = jsonLd.body.getRequiredArray("@graph").fold(e => throw BadRequestException(e), identity).value
        hasOtherNothingValue = graph
                                 .filter(
                                   _.asInstanceOf[JsonLDObject]
                                     .value("@id")
                                     .asInstanceOf[JsonLDString]
                                     .value == "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothingValue",
                                 )
                                 .head
                                 .asInstanceOf[JsonLDObject]
        iris = hasOtherNothingValue.value.keySet

        isEditable =
          hasOtherNothingValue.getRequiredBoolean(KA.IsEditable).fold(msg => throw BadRequestException(msg), identity)
      } yield assertTrue(iris == expectedIris, isEditable)
    },
    test("update the property label and check if the property is still correctly marked as `isEditable: true`") {
      // update label and comment of a property
      val newLabel = "updated label"
      val params   =
        s"""
           |{
           |  "@id" : "$anythingOntoLocalhostIri",
           |  "@type": "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph": [
           |    {
           |      "@id": "$hasOtherNothingIri",
           |      "@type" : "owl:ObjectProperty",
           |      "rdfs:label": {
           |        "@language": "en",
           |        "@value": "$newLabel"
           |      }
           |    }
           |  ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
           |  }
           |}""".stripMargin

      for {
        jsonLd <- TestApiClient
                    .putJsonLdDocument(uri"/v2/ontologies/properties", params, anythingAdminUser)
                    .flatMap(_.assert200)
        updatedOntologyIri  = jsonLd.body.value("@id").asInstanceOf[JsonLDString].value
        graph               = jsonLd.body.getRequiredArray("@graph").fold(e => throw BadRequestException(e), identity).value
        property            = graph.head.asInstanceOf[JsonLDObject]
        returnedPropertyIri = property.getRequiredString("@id").fold(msg => throw BadRequestException(msg), identity)
        returnedLabel       = property
                          .getRequiredObject(Rdfs.Label)
                          .flatMap(_.getRequiredString("@value"))
                          .fold(msg => throw BadRequestException(msg), identity)
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)

        // load back the ontology to verify that the updated property still is editable
        ontologyIri = SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost
        jsonLd     <- TestApiClient.getJsonLdDocument(uri"/v2/ontologies/allentities/$ontologyIri").flatMap(_.assert200)
        graph       = jsonLd.body
                  .getRequiredArray("@graph")
                  .fold(e => throw BadRequestException(e), identity)
                  .value
                  .map(_.asInstanceOf[JsonLDObject])
        nothingValue = graph
                         .filter(
                           _.getRequiredString("@id")
                             .fold(msg => throw BadRequestException(msg), identity) == hasOtherNothingValueIri,
                         )
                         .head
        isEditableMaybe = nothingValue.getBoolean(KA.IsEditable).fold(msg => throw BadRequestException(msg), identity)
      } yield assertTrue(
        updatedOntologyIri == anythingOntoLocalhostIri,
        returnedPropertyIri == hasOtherNothingIri,
        returnedLabel == newLabel,
        newLastModDate.isAfter(oldLastModDate),
        isEditableMaybe.contains(true),
      )
    },
    test("update the property comment and check if the property is still correctly marked as `isEditable: true`") {
      // update label and comment of a property
      val newComment = "updated comment"
      val params     =
        s"""
           |{
           |  "@id" : "$anythingOntoLocalhostIri",
           |  "@type": "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph": [
           |    {
           |      "@id": "$hasOtherNothingIri",
           |      "@type" : "owl:ObjectProperty",
           |      "rdfs:comment": {
           |        "@language": "en",
           |        "@value": "$newComment"
           |      }
           |    }
           |  ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
           |  }
           |}""".stripMargin

      for {
        jsonLd <- TestApiClient
                    .putJsonLdDocument(uri"/v2/ontologies/properties", params, anythingAdminUser)
                    .flatMap(_.assert200)
        updatedOntologyIri  = jsonLd.body.value("@id").asInstanceOf[JsonLDString].value
        graph               = jsonLd.body.getRequiredArray("@graph").fold(e => throw BadRequestException(e), identity).value
        property            = graph.head.asInstanceOf[JsonLDObject]
        returnedPropertyIri = property.getRequiredString("@id").fold(msg => throw BadRequestException(msg), identity)
        returnedComment     = property
                            .getRequiredObject(Rdfs.Comment)
                            .flatMap(_.getRequiredString("@value"))
                            .fold(msg => throw BadRequestException(msg), identity)
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)

        // load back the ontology to verify that the updated property still is editable
        ontologyIri = SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost
        jsonLd     <- TestApiClient.getJsonLdDocument(uri"/v2/ontologies/allentities/$ontologyIri").flatMap(_.assert200)
        graph       = jsonLd.body
                  .getRequiredArray("@graph")
                  .fold(e => throw BadRequestException(e), identity)
                  .value
                  .map(_.asInstanceOf[JsonLDObject])
        nothingValue = graph
                         .filter(
                           _.getRequiredString("@id")
                             .fold(msg => throw BadRequestException(msg), identity) == hasOtherNothingValueIri,
                         )
                         .head
        isEditableMaybe = nothingValue.getBoolean(KA.IsEditable).fold(msg => throw BadRequestException(msg), identity)
      } yield assertTrue(
        updatedOntologyIri == anythingOntoLocalhostIri,
        returnedPropertyIri == hasOtherNothingIri,
        returnedComment == newComment,
        newLastModDate.isAfter(oldLastModDate),
        isEditableMaybe.contains(true),
      )
    },
    test("delete the property comment and check if the property is still correctly marked as `isEditable: true`") {
      // update label and comment of a property
      val propertyIri = hasOtherNothingIri
      for {
        jsonLd <- TestApiClient
                    .deleteJsonLdDocument(
                      uri"/v2/ontologies/properties/comment/$propertyIri?lastModificationDate=$anythingLastModDate",
                      anythingAdminUser,
                    )
                    .flatMap(_.assert200)
        updatedOntologyIri               = jsonLd.body.value("@id").asInstanceOf[JsonLDString].value
        graph                            = jsonLd.body.getRequiredArray("@graph").fold(e => throw BadRequestException(e), identity).value
        property                         = graph.head.asInstanceOf[JsonLDObject]
        returnedPropertyIri              = property.getRequiredString("@id").fold(msg => throw BadRequestException(msg), identity)
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
        // load back the ontology to verify that the updated property still is editable
        ontologyIri = SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost
        jsonLd     <- TestApiClient.getJsonLdDocument(uri"/v2/ontologies/allentities/$ontologyIri").flatMap(_.assert200)
        graph       = jsonLd.body
                  .getRequiredArray("@graph")
                  .fold(e => throw BadRequestException(e), identity)
                  .value
                  .map(_.asInstanceOf[JsonLDObject])
        nothingValue = graph
                         .filter(
                           _.getRequiredString("@id")
                             .fold(msg => throw BadRequestException(msg), identity) == hasOtherNothingValueIri,
                         )
                         .head
        isEditableMaybe = nothingValue.getBoolean(KA.IsEditable).fold(msg => throw BadRequestException(msg), identity)
      } yield assertTrue(
        updatedOntologyIri == anythingOntoLocalhostIri,
        returnedPropertyIri == hasOtherNothingIri,
        !property.value.contains(Rdfs.Comment),
        newLastModDate.isAfter(oldLastModDate),
        isEditableMaybe.contains(true),
      )
    },
    test("update the property guiElement and check if the property is still correctly marked as `isEditable: true`") {
      // update label and comment of a property
      val params =
        s"""
           |{
           |  "@id" : "$anythingOntoLocalhostIri",
           |  "@type": "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph": [
           |    {
           |      "@id": "$hasOtherNothingIri",
           |      "@type" : "owl:ObjectProperty",
           |      "salsah-gui:guiElement": {
           |        "@id": "salsah-gui:Searchbox"
           |      }
           |    }
           |  ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
           |  }
           |}""".stripMargin

      for {
        jsonLd <- TestApiClient
                    .putJsonLdDocument(uri"/v2/ontologies/properties/guielement", params, anythingAdminUser)
                    .flatMap(_.assert200)
        updatedOntologyIri               = jsonLd.body.value("@id").asInstanceOf[JsonLDString].value
        graph                            = jsonLd.body.getRequiredArray("@graph").fold(e => throw BadRequestException(e), identity).value
        property                         = graph.head.asInstanceOf[JsonLDObject]
        returnedPropertyIri              = property.getRequiredString("@id").fold(msg => throw BadRequestException(msg), identity)
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
        // load back the ontology to verify that the updated property still is editable
        ontologyIri = SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost
        jsonLd     <- TestApiClient.getJsonLdDocument(uri"/v2/ontologies/allentities/$ontologyIri").flatMap(_.assert200)
        graph       = jsonLd.body
                  .getRequiredArray("@graph")
                  .fold(e => throw BadRequestException(e), identity)
                  .value
                  .map(_.asInstanceOf[JsonLDObject])
        nothingValue = graph
                         .filter(
                           _.getRequiredString("@id")
                             .fold(msg => throw BadRequestException(msg), identity) == hasOtherNothingValueIri,
                         )
                         .head
        isEditableMaybe = nothingValue.getBoolean(KA.IsEditable).fold(msg => throw BadRequestException(msg), identity)
      } yield assertTrue(
        updatedOntologyIri == anythingOntoLocalhostIri,
        returnedPropertyIri == hasOtherNothingIri,
        newLastModDate.isAfter(oldLastModDate),
        isEditableMaybe.contains(true),
      )
    },
    test("remove the cardinality for the property anything:hasOtherNothing from the class anything:Nothing") {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class"
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      for {
        jsonLd <- TestApiClient
                    .putJsonLdDocument(uri"/v2/ontologies/cardinalities", params, anythingAdminUser)
                    .flatMap(_.assert200)
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        // Check that cardinalities were inherited from knora-api:Resource.
        responseAsInput.classes.head._2.directCardinalities.isEmpty,
        getPropertyIrisFromResourceClassResponse(jsonLd).contains(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
        ),
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("determine that a property can be deleted") {
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothing"
      TestApiClient
        .getJsonLdDocument(uri"/v2/ontologies/candeleteproperty/$propertyIri", anythingAdminUser)
        .flatMap(_.assert200)
        .map(jsonLd => assertTrue(jsonLd.body.value(KA.CanDo).asInstanceOf[JsonLDBoolean].value))
    },
    test("delete the property anything:hasOtherNothing") {
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothing"
      for {
        jsonLd <- TestApiClient
                    .deleteJsonLdDocument(
                      uri"/v2/ontologies/properties/$propertyIri?lastModificationDate=$anythingLastModDate",
                      anythingAdminUser,
                    )
                    .flatMap(_.assert200)
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        jsonLd.body.requireStringWithValidation("@id", sf.toSmartIriWithErr) ==
          "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri,
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("create a property anything:hasNothingness with knora-api:subjectType anything:Nothing") {
      val params =
        s"""{
           |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasNothingness",
           |    "@type" : "owl:ObjectProperty",
           |    "knora-api:subjectType" : {
           |      "@id" : "anything:Nothing"
           |    },
           |    "knora-api:objectType" : {
           |      "@id" : "knora-api:BooleanValue"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "Indicates whether a Nothing has nothingness"
           |    },
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "has nothingness"
           |    },
           |    "rdfs:subPropertyOf" : {
           |      "@id" : "knora-api:hasValue"
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
      for {
        jsonLd <- TestApiClient
                    .postJsonLdDocument(uri"/v2/ontologies/properties", params, anythingAdminUser)
                    .flatMap(_.assert200)
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        responseAsInput.properties == paramsAsInput.properties,
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("add a cardinality for the property anything:hasNothingness to the class anything:Nothing") {
      val params =
        s"""{
           |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:hasNothingness"
           |      }
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val paramsAsInput = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
      for {
        jsonLd <- TestApiClient
                    .postJsonLdDocument(uri"/v2/ontologies/cardinalities", params, anythingAdminUser)
                    .flatMap(_.assert200)
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        responseAsInput.classes.head._2.directCardinalities == paramsAsInput.classes.head._2.directCardinalities,
        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(jsonLd).contains(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
        ),
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("change the GUI order of the cardinality on anything:hasNothingness in the class anything:Nothing") {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality": 1,
           |      "owl:onProperty": {
           |        "@id" : "anything:hasNothingness"
           |      },
           |      "salsah-gui:guiOrder": 2
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      for {
        jsonLd <-
          TestApiClient.putJsonLdDocument(uri"/v2/ontologies/guiorder", params, anythingAdminUser).flatMap(_.assert200)
        paramsAsInput                    = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        responseAsInput.classes.head._2.directCardinalities == paramsAsInput.classes.head._2.directCardinalities,
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("create a property anything:hasEmptiness with knora-api:subjectType anything:Nothing") {
      val params =
        s"""{
           |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasEmptiness",
           |    "@type" : "owl:ObjectProperty",
           |    "knora-api:subjectType" : {
           |      "@id" : "anything:Nothing"
           |    },
           |    "knora-api:objectType" : {
           |      "@id" : "knora-api:BooleanValue"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "Indicates whether a Nothing has emptiness"
           |    },
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "has emptiness"
           |    },
           |    "rdfs:subPropertyOf" : {
           |      "@id" : "knora-api:hasValue"
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val paramsAsInput = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
      for {
        jsonLd <- TestApiClient
                    .postJsonLdDocument(uri"/v2/ontologies/properties", params, anythingAdminUser)
                    .flatMap(_.assert200)
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        responseAsInput.properties == paramsAsInput.properties,
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("determine that a class's cardinalities can be changed") {
      val classIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Nothing"
      TestApiClient
        .getJsonLdDocument(uri"/v2/ontologies/canreplacecardinalities/$classIri", anythingAdminUser)
        .flatMap(_.assert200)
        .map(jsonLd => assertTrue(jsonLd.body.value(KA.CanDo).asInstanceOf[JsonLDBoolean].value))
    },
    test(
      "change the cardinalities of the class anything:Nothing, replacing anything:hasNothingness with anything:hasEmptiness",
    ) {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality": 1,
           |      "owl:onProperty": {
           |        "@id" : "anything:hasEmptiness"
           |      }
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      val paramsAsInput = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape
      for {
        jsonLd <- TestApiClient
                    .putJsonLdDocument(uri"/v2/ontologies/cardinalities", params, anythingAdminUser)
                    .flatMap(_.assert200)
        responseAsInput                  = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        responseAsInput.classes.head._2.directCardinalities == paramsAsInput.classes.head._2.directCardinalities,
        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(jsonLd).contains(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
        ),
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("delete the property anything:hasNothingness") {
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasNothingness"
      for {
        jsonLd <- TestApiClient
                    .deleteJsonLdDocument(
                      uri"/v2/ontologies/properties/$propertyIri?lastModificationDate=$anythingLastModDate",
                      anythingAdminUser,
                    )
                    .flatMap(_.assert200)
        (oldLastModDate, newLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        jsonLd.body.requireStringWithValidation("@id", sf.toSmartIriWithErr) ==
          "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri,
        newLastModDate.isAfter(oldLastModDate),
      )
    },
    test("remove all cardinalities from the class anything:Nothing") {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class"
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      for {
        jsonLd <- TestApiClient
                    .putJsonLdDocument(uri"/v2/ontologies/cardinalities", params, anythingAdminUser)
                    .flatMap(_.assert200)
        responseAsInput                          = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (oldLastModDate, newAnythingLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        responseAsInput.classes.head._2.directCardinalities.isEmpty,
        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(jsonLd).contains(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
        ),
        newAnythingLastModDate.isAfter(oldLastModDate),
      )
    },
    test("delete the property anything:hasEmptiness") {
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasEmptiness"
      for {
        jsonLd <- TestApiClient
                    .deleteJsonLdDocument(
                      uri"/v2/ontologies/properties/$propertyIri?lastModificationDate=$anythingLastModDate",
                      anythingAdminUser,
                    )
                    .flatMap(_.assert200)
        (oldLastModDate, newAnythingLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        jsonLd.body.requireStringWithValidation("@id", sf.toSmartIriWithErr) ==
          "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri,
        newAnythingLastModDate.isAfter(oldLastModDate),
      )
    },
    test("determine that a class can be deleted") {
      val classIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Nothing"
      TestApiClient
        .getJsonLdDocument(uri"/v2/ontologies/candeleteclass/$classIri", anythingAdminUser)
        .flatMap(_.assert200)
        .map(jsonLd => assertTrue(jsonLd.body.value(KA.CanDo).asInstanceOf[JsonLDBoolean].value))
    },
    test("delete the class anything:Nothing") {
      val classIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Nothing"
      for {
        jsonLd <- TestApiClient
                    .deleteJsonLdDocument(
                      uri"/v2/ontologies/classes/$classIri?lastModificationDate=$anythingLastModDate",
                      anythingAdminUser,
                    )
                    .flatMap(_.assert200)
        (oldLastModDate, newAnythingLastModDate) = self.anythingLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        jsonLd.body.requireStringWithValidation(
          "@id",
          sf.toSmartIriWithErr,
        ) == "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri,
        newAnythingLastModDate.isAfter(oldLastModDate),
      )
    },
    test("create a shared ontology and put a property in it") {
      val label                      = "The useless ontology"
      val createOntologyJson: String =
        s"""
           |{
           |    "knora-api:ontologyName": "useless",
           |    "knora-api:attachedToProject": {
           |      "@id": "${KnoraAdmin.DefaultSharedOntologiesProject}"
           |    },
           |    "knora-api:isShared": true,
           |    "rdfs:label": "$label",
           |    "@context": {
           |        "rdfs": "${Rdfs.RdfsPrefixExpansion}",
           |        "knora-api": "${KA.KnoraApiV2PrefixExpansion}"
           |    }
           |}""".stripMargin

      for {
        // Create the shared ontology
        jsonLd <-
          TestApiClient.postJsonLdDocument(uri"/v2/ontologies", createOntologyJson, superUser).flatMap(_.assert200)
        (oldLastModDate, newLastModDate) = self.uselessLastModDate.updateFrom(jsonLd)
        metadata                         = jsonLd.body
        ontologyIri                      = metadata.value("@id").asInstanceOf[JsonLDString].value
        _                                = self.uselessIri.set(ontologyIri)

        // Create a property in the shared ontology
        createPropertyJson =
          s"""
             |{
             |  "@id" : "${uselessIri.get}",
             |  "@type" : "owl:Ontology",
             |  "knora-api:lastModificationDate" : {
             |    "@type" : "xsd:dateTimeStamp",
             |    "@value" : "$uselessLastModDate"
             |  },
             |  "@graph" : [ {
             |    "@id" : "useless:hasSharedName",
             |    "@type" : "owl:ObjectProperty",
             |    "knora-api:objectType" : {
             |      "@id" : "knora-api:TextValue"
             |    },
             |    "rdfs:comment" : {
             |      "@language" : "en",
             |      "@value" : "Represents a name"
             |    },
             |    "rdfs:label" : {
             |      "@language" : "en",
             |      "@value" : "has shared name"
             |    },
             |    "rdfs:subPropertyOf" : {
             |      "@id" : "knora-api:hasValue"
             |    }
             |  } ],
             |  "@context" : {
             |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "owl" : "http://www.w3.org/2002/07/owl#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
             |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
             |    "useless" : "${uselessIri.get}#"
             |  }
             |}""".stripMargin
        paramsAsInput = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(createPropertyJson)).unescape
        jsonLd       <- TestApiClient
                    .postJsonLdDocument(uri"/v2/ontologies/properties", createPropertyJson, superUser)
                    .flatMap(_.assert200)
        responseAsInput                                           = InputOntologyV2.fromJsonLD(jsonLd, parsingMode = TestResponseParsingModeV2).unescape
        (lastModDateBeforeCreateProp, lastModDateAfterCreateProp) = self.uselessLastModDate.updateFrom(jsonLd)
      } yield assertTrue(
        ontologyIri == "http://api.knora.org/ontology/shared/useless/v2",
        metadata.value(Rdfs.Label) == JsonLDString(label),
        newLastModDate.isAfter(oldLastModDate),
        responseAsInput.properties == paramsAsInput.properties,
        lastModDateAfterCreateProp.isAfter(lastModDateBeforeCreateProp),
      )
    },
    test("create a class with several cardinalities, then remove one of the cardinalities") {
      // Create a class with no cardinalities.
      val createClassRequestJson =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:TestClass",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "test class"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "A test class"
           |    },
           |    "rdfs:subClassOf" : [
           |      {
           |        "@id": "knora-api:Resource"
           |      }
           |    ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
      for {
        _ <- TestApiClient
               .postJsonLdDocument(uri"/v2/ontologies/classes", createClassRequestJson, anythingAdminUser)
               .flatMap(_.assert200)
               .tap(jsonLd => ZIO.attempt(self.anythingLastModDate.updateFrom(jsonLd)))

        // Create a text property.
        createTestTextPropRequestJson =
          s"""{
             |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
             |  "@type" : "owl:Ontology",
             |  "knora-api:lastModificationDate" : {
             |    "@type" : "xsd:dateTimeStamp",
             |    "@value" : "$anythingLastModDate"
             |  },
             |  "@graph" : [ {
             |      "@id" : "anything:testTextProp",
             |      "@type" : "owl:ObjectProperty",
             |      "knora-api:subjectType" : {
             |        "@id" : "anything:TestClass"
             |      },
             |      "knora-api:objectType" : {
             |        "@id" : "knora-api:TextValue"
             |      },
             |      "rdfs:comment" : {
             |        "@language" : "en",
             |        "@value" : "A test text property"
             |      },
             |      "rdfs:label" : {
             |        "@language" : "en",
             |        "@value" : "test text property"
             |      },
             |      "rdfs:subPropertyOf" : {
             |        "@id" : "knora-api:hasValue"
             |      }
             |  } ],
             |  "@context" : {
             |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
             |    "owl" : "http://www.w3.org/2002/07/owl#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
             |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
             |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
             |  }
             |}""".stripMargin
        _ <- TestApiClient
               .postJsonLdDocument(uri"/v2/ontologies/properties", createTestTextPropRequestJson, anythingAdminUser)
               .flatMap(_.assert200)
               .tap(jsonLd => ZIO.attempt(self.anythingLastModDate.updateFrom(jsonLd)))

        // Create an integer property.
        createTestIntegerPropRequestJson =
          s"""{
             |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
             |  "@type" : "owl:Ontology",
             |  "knora-api:lastModificationDate" : {
             |    "@type" : "xsd:dateTimeStamp",
             |    "@value" : "$anythingLastModDate"
             |  },
             |  "@graph" : [ {
             |      "@id" : "anything:testIntProp",
             |      "@type" : "owl:ObjectProperty",
             |      "knora-api:subjectType" : {
             |        "@id" : "anything:TestClass"
             |      },
             |      "knora-api:objectType" : {
             |        "@id" : "knora-api:IntValue"
             |      },
             |      "rdfs:comment" : {
             |        "@language" : "en",
             |        "@value" : "A test int property"
             |      },
             |      "rdfs:label" : {
             |        "@language" : "en",
             |        "@value" : "test int property"
             |      },
             |      "rdfs:subPropertyOf" : {
             |        "@id" : "knora-api:hasValue"
             |      }
             |  } ],
             |  "@context" : {
             |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
             |    "owl" : "http://www.w3.org/2002/07/owl#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
             |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
             |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
             |  }
             |}""".stripMargin
        _ <- TestApiClient
               .postJsonLdDocument(uri"/v2/ontologies/properties", createTestIntegerPropRequestJson, anythingAdminUser)
               .flatMap(_.assert200)
               .tap(jsonLd => ZIO.attempt(self.anythingLastModDate.updateFrom(jsonLd)))

        // Create a link property.
        createTestLinkPropRequestJson =
          s"""{
             |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
             |  "@type" : "owl:Ontology",
             |  "knora-api:lastModificationDate" : {
             |    "@type" : "xsd:dateTimeStamp",
             |    "@value" : "$anythingLastModDate"
             |  },
             |  "@graph" : [ {
             |      "@id" : "anything:testLinkProp",
             |      "@type" : "owl:ObjectProperty",
             |      "knora-api:subjectType" : {
             |        "@id" : "anything:TestClass"
             |      },
             |      "knora-api:objectType" : {
             |        "@id" : "anything:TestClass"
             |      },
             |      "rdfs:comment" : {
             |        "@language" : "en",
             |        "@value" : "A test link property"
             |      },
             |      "rdfs:label" : {
             |        "@language" : "en",
             |        "@value" : "test link property"
             |      },
             |      "rdfs:subPropertyOf" : {
             |        "@id" : "knora-api:hasLinkTo"
             |      }
             |  } ],
             |  "@context" : {
             |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
             |    "owl" : "http://www.w3.org/2002/07/owl#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
             |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
             |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
             |  }
             |}""".stripMargin
        _ <- TestApiClient
               .postJsonLdDocument(uri"/v2/ontologies/properties", createTestLinkPropRequestJson, anythingAdminUser)
               .flatMap(_.assert200)
               .tap(jsonLd => ZIO.attempt(self.anythingLastModDate.updateFrom(jsonLd)))

        // Add cardinalities to the class.
        addCardinalitiesRequestJson =
          s"""
             |{
             |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
             |  "@type" : "owl:Ontology",
             |  "knora-api:lastModificationDate" : {
             |    "@type" : "xsd:dateTimeStamp",
             |    "@value" : "$anythingLastModDate"
             |  },
             |  "@graph" : [ {
             |    "@id" : "anything:TestClass",
             |    "@type" : "owl:Class",
             |    "rdfs:subClassOf" : [ {
             |      "@type": "owl:Restriction",
             |      "owl:maxCardinality" : 1,
             |      "owl:onProperty" : {
             |        "@id" : "anything:testTextProp"
             |      }
             |    },
             |    {
             |      "@type": "owl:Restriction",
             |      "owl:maxCardinality" : 1,
             |      "owl:onProperty" : {
             |        "@id" : "anything:testIntProp"
             |      }
             |    },
             |    {
             |      "@type": "owl:Restriction",
             |      "owl:maxCardinality" : 1,
             |      "owl:onProperty" : {
             |        "@id" : "anything:testLinkProp"
             |      }
             |    } ]
             |  } ],
             |  "@context" : {
             |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "owl" : "http://www.w3.org/2002/07/owl#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
             |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
             |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
             |  }
             |}""".stripMargin
        _ <- TestApiClient
               .postJsonLdDocument(uri"/v2/ontologies/cardinalities", addCardinalitiesRequestJson, anythingAdminUser)
               .flatMap(_.assert200)
               .tap(jsonLd => ZIO.attempt(self.anythingLastModDate.updateFrom(jsonLd)))

        // Remove the link value cardinality from to the class.
        params =
          s"""
             |{
             |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
             |  "@type" : "owl:Ontology",
             |  "knora-api:lastModificationDate" : {
             |    "@type" : "xsd:dateTimeStamp",
             |    "@value" : "$anythingLastModDate"
             |  },
             |  "@graph" : [ {
             |    "@id" : "anything:TestClass",
             |    "@type" : "owl:Class",
             |    "rdfs:subClassOf" : [ {
             |      "@type": "owl:Restriction",
             |      "owl:maxCardinality" : 1,
             |      "owl:onProperty" : {
             |        "@id" : "anything:testTextProp"
             |      }
             |    },
             |    {
             |      "@type": "owl:Restriction",
             |      "owl:maxCardinality" : 1,
             |      "owl:onProperty" : {
             |        "@id" : "anything:testIntProp"
             |      }
             |    } ]
             |  } ],
             |  "@context" : {
             |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "owl" : "http://www.w3.org/2002/07/owl#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
             |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
             |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
             |  }
             |}""".stripMargin
        _ <- TestApiClient
               .putJsonLdDocument(uri"/v2/ontologies/cardinalities", params, anythingAdminUser)
               .flatMap(_.assert200)
               .tap(jsonLd => ZIO.attempt(self.anythingLastModDate.updateFrom(jsonLd)))
      } yield assertCompletes
    },
    test(
      "create a class with two cardinalities, use one in data, and allow only removal of the cardinality for the property not used in data",
    ) {
      // Create a class with no cardinalities.
      val label   = LangString.make(EN, "A Blue Free Test class").fold(e => throw e.head, v => v)
      val comment = Some(
        LangString
          .make(EN, "A Blue Free Test class used for testing cardinalities")
          .fold(e => throw e.head, v => v),
      )
      val createClassRequestJson = CreateClassRequest
        .make(
          ontologyName = "freetest",
          lastModificationDate = freetestLastModDate,
          className = "BlueFreeTestClass",
          label = label,
          comment = comment,
        )
        .value
      for {
        _ <- TestApiClient
               .postJsonLdDocument(uri"/v2/ontologies/classes", createClassRequestJson, anythingAdminUser)
               .flatMap(_.assert200)
               .tap(jsonLd => ZIO.attempt(self.freetestLastModDate.updateFrom(jsonLd)))

        // Create a text property.
        label1                        = LangString.make(EN, "blue test text property").fold(e => throw e.head, v => v)
        comment1                      = Some(LangString.make(EN, "A blue test text property").fold(e => throw e.head, v => v))
        createTestTextPropRequestJson =
          CreatePropertyRequest
            .make(
              ontologyName = "freetest",
              lastModificationDate = freetestLastModDate,
              propertyName = "hasBlueTestTextProp",
              subjectClassName = Some("BlueFreeTestClass"),
              propertyType = PropertyValueType.TextValue,
              label = label1,
              comment = comment1,
            )
            .value
        _ <- TestApiClient
               .postJsonLdDocument(uri"/v2/ontologies/properties", createTestTextPropRequestJson, anythingAdminUser)
               .flatMap(_.assert200)
               .tap(jsonLd => ZIO.attempt(self.freetestLastModDate.updateFrom(jsonLd)))

        // Create an integer property.
        label2                           = LangString.make(EN, "blue test integer property").fold(e => throw e.head, v => v)
        comment2                         = LangString.make(EN, "A blue test integer property").fold(e => throw e.head, v => v)
        createTestIntegerPropRequestJson = CreatePropertyRequest
                                             .make(
                                               ontologyName = "freetest",
                                               lastModificationDate = freetestLastModDate,
                                               propertyName = "hasBlueTestIntProp",
                                               subjectClassName = Some("BlueFreeTestClass"),
                                               propertyType = PropertyValueType.IntValue,
                                               label = label2,
                                               comment = Some(comment2),
                                             )
                                             .value
        _ <- TestApiClient
               .postJsonLdDocument(uri"/v2/ontologies/properties", createTestIntegerPropRequestJson, anythingAdminUser)
               .flatMap(_.assert200)
               .tap(jsonLd => ZIO.attempt(self.freetestLastModDate.updateFrom(jsonLd)))

        // Add cardinalities to the class.
        addCardinalitiesRequestJson = AddCardinalitiesRequest
                                        .make(
                                          ontologyName = "freetest",
                                          lastModificationDate = freetestLastModDate,
                                          className = "BlueFreeTestClass",
                                          restrictions = List(
                                            Restriction(
                                              CardinalityRestriction.MaxCardinalityOne,
                                              onProperty =
                                                Property(ontology = "freetest", property = "hasBlueTestTextProp"),
                                            ),
                                            Restriction(
                                              CardinalityRestriction.MaxCardinalityOne,
                                              onProperty =
                                                Property(ontology = "freetest", property = "hasBlueTestIntProp"),
                                            ),
                                          ),
                                        )
                                        .value

        jsonLd <-
          TestApiClient
            .postJsonLdDocument(uri"/v2/ontologies/cardinalities", addCardinalitiesRequestJson, anythingAdminUser)
            .flatMap(_.assert200)
        _ = self.freetestLastModDate.updateFrom(jsonLd)

        // Create a resource of #BlueTestClass using only #hasBlueTestIntProp
        resourceLabel = {
          val fuzz1 = Random.nextString(1000)
          val fuzz2 = List.fill(1000)(()).map(_ => Random.nextInt(128).toChar).mkString
          val fuzz3 = List.fill(1000)(()).map(_ => Random.nextPrintableChar()).mkString
          fuzz1 ++ fuzz2 ++ fuzz3
        }
        createResourceWithValues =
          s"""{
             |  "@type" : "freetest:BlueFreeTestClass",
             |  "freetest:hasBlueTestIntProp" : {
             |    "@type" : "knora-api:IntValue",
             |    "knora-api:hasPermissions" : "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher",
             |    "knora-api:intValueAsInt" : 5,
             |    "knora-api:valueHasComment" : "this is the number five"
             |  },
             |  "knora-api:attachedToProject" : {
             |    "@id" : "http://rdfh.ch/projects/0001"
             |  },
             |  "rdfs:label" : ${resourceLabel.toJson},
             |  "@context" : {
             |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
             |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
             |    "freetest" : "${SharedOntologyTestDataADM.FREETEST_ONTOLOGY_IRI_LocalHost}#"
             |  }
             |}""".stripMargin
        _ <- TestApiClient
               .postJsonLdDocument(uri"/v2/resources", createResourceWithValues, anythingAdminUser)
               .flatMap(_.assert200)

        // payload to test cardinality can't be deleted
        cardinalityCantBeDeletedPayload = AddCardinalitiesRequest.make(
                                            ontologyName = "freetest",
                                            lastModificationDate = freetestLastModDate,
                                            className = "FreeTest",
                                            restrictions = List(
                                              Restriction(
                                                CardinalityRestriction.MinCardinalityOne,
                                                onProperty = Property(ontology = "freetest", property = "hasText"),
                                              ),
                                            ),
                                          )

        // Expect cardinality can't be deleted - endpoint should return CanDo response with value false
        canDeleteCardinalitiesResponse <- TestApiClient
                                            .postJsonLdDocument(
                                              uri"/v2/ontologies/candeletecardinalities",
                                              cardinalityCantBeDeletedPayload.value,
                                              anythingAdminUser,
                                            )
                                            .flatMap(_.assert200)

        // Prepare the JsonLD payload to check if a cardinality can be deleted and then to also actually delete it.
        params =
          s"""
             |{
             |  "@id" : "${SharedOntologyTestDataADM.FREETEST_ONTOLOGY_IRI_LocalHost}",
             |  "@type" : "owl:Ontology",
             |  "knora-api:lastModificationDate" : {
             |    "@type" : "xsd:dateTimeStamp",
             |    "@value" : "$freetestLastModDate"
             |  },
             |  "@graph" : [ {
             |    "@id" : "freetest:BlueFreeTestClass",
             |    "@type" : "owl:Class",
             |    "rdfs:subClassOf" :  {
             |      "@type": "owl:Restriction",
             |      "owl:maxCardinality" : 1,
             |      "owl:onProperty" : {
             |        "@id" : "freetest:hasBlueTestTextProp"
             |      }
             |    }
             |  } ],
             |  "@context" : {
             |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "owl" : "http://www.w3.org/2002/07/owl#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
             |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
             |    "freetest" : "${SharedOntologyTestDataADM.FREETEST_ONTOLOGY_IRI_LocalHost}#"
             |  }
             |}""".stripMargin

        // Successfully check if the cardinality can be deleted
        canDeleteCardinalities2Response <-
          TestApiClient
            .postJsonLdDocument(uri"/v2/ontologies/candeletecardinalities", params, anythingAdminUser)
            .flatMap(_.assert200)

        // Successfully remove the (unused) text value cardinality from the class.
        patchResponse <- TestApiClient
                           .patchJsonLdDocument(uri"/v2/ontologies/cardinalities", params, anythingAdminUser)
                           .flatMap(_.assert200)
        _ = self.freetestLastModDate.updateFrom(patchResponse)
      } yield assertTrue(
        !canDeleteCardinalitiesResponse.body.value(KA.CanDo).asInstanceOf[JsonLDBoolean].value,
        canDeleteCardinalities2Response.body.value(KA.CanDo).asInstanceOf[JsonLDBoolean].value,
      )
    },
    test(
      "create two classes with the same property, use one in data, and allow removal of the cardinality for the property not used in data",
    ) {
      // Create TestClassOne with no cardinalities.
      val label   = LangString.make(EN, "Test class number one").fold(e => throw e.head, v => v)
      val comment = Some(
        LangString
          .make(EN, "A test class used for testing cardinalities")
          .fold(e => throw e.head, v => v),
      )
      val createClassRequestJsonOne = CreateClassRequest
        .make(
          ontologyName = "freetest",
          lastModificationDate = freetestLastModDate,
          className = "TestClassOne",
          label = label,
          comment = comment,
        )
        .value

      for {
        jsonLd <- TestApiClient
                    .postJsonLdDocument(uri"/v2/ontologies/classes", createClassRequestJsonOne, anythingAdminUser)
                    .flatMap(_.assert200)
        _ = self.freetestLastModDate.updateFrom(jsonLd)
        // Create TestClassTwo with no cardinalities
        label1   = LangString.make(EN, "Test class number two").fold(e => throw e.head, v => v)
        comment1 = Some(
                     LangString
                       .make(EN, "A test class used for testing cardinalities")
                       .fold(e => throw e.head, v => v),
                   )
        createClassRequestJsonTwo = CreateClassRequest
                                      .make(
                                        ontologyName = "freetest",
                                        lastModificationDate = freetestLastModDate,
                                        className = "TestClassTwo",
                                        label = label1,
                                        comment = comment1,
                                      )
                                      .value

        _ <- TestApiClient
               .postJsonLdDocument(uri"/v2/ontologies/classes", createClassRequestJsonTwo, anythingAdminUser)
               .flatMap(_.assert200)
               .tap(jsonLd => ZIO.attempt(self.freetestLastModDate.updateFrom(jsonLd)))

        // Create a text property hasTestTextProp.
        label2                = LangString.make(EN, "Test int property").fold(e => throw e.head, v => v)
        comment2              = Some(LangString.make(EN, "A test int property").fold(e => throw e.head, v => v))
        createPropRequestJson = CreatePropertyRequest
                                  .make(
                                    ontologyName = "freetest",
                                    lastModificationDate = freetestLastModDate,
                                    propertyName = "hasIntProp",
                                    subjectClassName = None,
                                    propertyType = PropertyValueType.IntValue,
                                    label = label2,
                                    comment = comment2,
                                  )
                                  .value

        _ <- TestApiClient
               .postJsonLdDocument(uri"/v2/ontologies/properties", createPropRequestJson, anythingAdminUser)
               .flatMap(_.assert200)
               .tap(jsonLd => ZIO.attempt(self.freetestLastModDate.updateFrom(jsonLd)))

        // Add cardinality hasIntProp to TestClassOne.
        addCardinalitiesRequestJsonOne = AddCardinalitiesRequest
                                           .make(
                                             ontologyName = "freetest",
                                             lastModificationDate = freetestLastModDate,
                                             className = "TestClassOne",
                                             restrictions = List(
                                               Restriction(
                                                 CardinalityRestriction.MaxCardinalityOne,
                                                 onProperty = Property(ontology = "freetest", property = "hasIntProp"),
                                               ),
                                             ),
                                           )
                                           .value

        _ <- TestApiClient
               .postJsonLdDocument(uri"/v2/ontologies/cardinalities", addCardinalitiesRequestJsonOne, anythingAdminUser)
               .flatMap(_.assert200)
               .tap(jsonLd => ZIO.attempt(self.freetestLastModDate.updateFrom(jsonLd)))

        // Add cardinality hasIntProp to TestClassTwo.
        addCardinalitiesRequestJsonTwo = AddCardinalitiesRequest
                                           .make(
                                             ontologyName = "freetest",
                                             lastModificationDate = freetestLastModDate,
                                             className = "TestClassTwo",
                                             restrictions = List(
                                               Restriction(
                                                 CardinalityRestriction.MaxCardinalityOne,
                                                 onProperty = Property(ontology = "freetest", property = "hasIntProp"),
                                               ),
                                             ),
                                           )
                                           .value

        _ <- TestApiClient
               .postJsonLdDocument(uri"/v2/ontologies/cardinalities", addCardinalitiesRequestJsonTwo, anythingAdminUser)
               .flatMap(_.assert200)
               .tap(jsonLd => ZIO.attempt(self.freetestLastModDate.updateFrom(jsonLd)))

        // Create a resource of #TestClassOne using #hasIntProp
        createResourceWithValues =
          s"""{
             |  "@type" : "freetest:TestClassOne",
             |  "freetest:hasIntProp" : {
             |    "@type" : "knora-api:IntValue",
             |    "knora-api:hasPermissions" : "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher",
             |    "knora-api:intValueAsInt" : 5,
             |    "knora-api:valueHasComment" : "this is the number five"
             |  },
             |  "knora-api:attachedToProject" : {
             |    "@id" : "http://rdfh.ch/projects/0001"
             |  },
             |  "rdfs:label" : "test class instance",
             |  "@context" : {
             |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
             |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
             |    "freetest" : "${SharedOntologyTestDataADM.FREETEST_ONTOLOGY_IRI_LocalHost}#"
             |  }
             |}""".stripMargin
        _ <- TestApiClient
               .postJsonLdDocument(uri"/v2/resources", createResourceWithValues, anythingAdminUser)
               .flatMap(_.assert200)

        // payload to ask if cardinality can be removed from TestClassTwo
        cardinalityCanBeDeletedPayload = AddCardinalitiesRequest
                                           .make(
                                             ontologyName = "freetest",
                                             lastModificationDate = freetestLastModDate,
                                             className = "TestClassTwo",
                                             restrictions = List(
                                               Restriction(
                                                 CardinalityRestriction.MaxCardinalityOne,
                                                 onProperty = Property(ontology = "freetest", property = "hasIntProp"),
                                               ),
                                             ),
                                           )
                                           .value

        // Expect cardinality can be deleted from TestClassTwo - CanDo response should return true
        canDeleteResponse <- TestApiClient
                               .postJsonLdDocument(
                                 uri"/v2/ontologies/candeletecardinalities",
                                 cardinalityCanBeDeletedPayload,
                                 anythingAdminUser,
                               )
                               .flatMap(_.assert200)
      } yield assertTrue(canDeleteResponse.body.value(KA.CanDo).asInstanceOf[JsonLDBoolean].value)
    },
    test("verify that link-property can not be deleted") {
      // payload representing a link-property to test that cardinality can't be deleted
      val cardinalityOnLinkPropertyWhichCantBeDeletedPayload = AddCardinalitiesRequest.make(
        ontologyName = "anything",
        lastModificationDate = anythingLastModDate,
        className = "Thing",
        restrictions = List(
          Restriction(
            CardinalityRestriction.MinCardinalityZero,
            onProperty = Property(ontology = "anything", property = "isPartOfOtherThing"),
          ),
        ),
      )

      TestApiClient
        .postJsonLdDocument(
          uri"/v2/ontologies/candeletecardinalities",
          cardinalityOnLinkPropertyWhichCantBeDeletedPayload.value,
          anythingAdminUser,
        )
        .flatMap(_.assert200)
        .map(jsonLd => assertTrue(!jsonLd.body.value(KA.CanDo).asInstanceOf[JsonLDBoolean].value))
    },
    test("verify that a class's cardinalities cannot be changed") {
      val classIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing"
      TestApiClient
        .getJsonLdDocument(uri"/v2/ontologies/canreplacecardinalities/$classIri", anythingAdminUser)
        .flatMap(_.assert200)
        .map(jsonLd => assertTrue(!jsonLd.body.value(KA.CanDo).asInstanceOf[JsonLDBoolean].value))
    },
    test("determine that a class cannot be deleted") {
      val thingIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing"
      TestApiClient
        .getJsonLdDocument(uri"/v2/ontologies/candeleteclass/$thingIri", anythingAdminUser)
        .flatMap(_.assert200)
        .map(jsonLd => assertTrue(!jsonLd.body.value(KA.CanDo).asInstanceOf[JsonLDBoolean].value))
    },
    test("determine that a property cannot be deleted") {
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger"
      TestApiClient
        .getJsonLdDocument(uri"/v2/ontologies/candeleteproperty/$propertyIri", anythingAdminUser)
        .flatMap(_.assert200)
        .map(jsonLd => assertTrue(!jsonLd.body.value(KA.CanDo).asInstanceOf[JsonLDBoolean].value))
    },
    test("determine that an ontology cannot be deleted") {
      val ontologyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2"
      TestApiClient
        .getJsonLdDocument(uri"/v2/ontologies/candeleteontology/$ontologyIri", anythingAdminUser)
        .flatMap(_.assert200)
        .map(jsonLd => assertTrue(!jsonLd.body.value(KA.CanDo).asInstanceOf[JsonLDBoolean].value))
    },
    test("create a class w/o comment") {
      val label   = LangString.make(EN, "Test label").fold(e => throw e.head, v => v)
      val request = CreateClassRequest
        .make(
          ontologyName = "freetest",
          lastModificationDate = freetestLastModDate,
          className = "testClass",
          label = label,
          comment = None,
        )
        .value
      TestApiClient
        .postJsonLdDocument(uri"/v2/ontologies/classes", request, anythingAdminUser)
        .flatMap(_.assert200)
        .tap(jsonLd => ZIO.attempt(self.freetestLastModDate.updateFrom(jsonLd)))
        .as(assertCompletes)
    },
    test("create a property w/o comment") {
      val label   = LangString.unsafeMake(EN, "Test label")
      val request = CreatePropertyRequest
        .make(
          ontologyName = "freetest",
          lastModificationDate = freetestLastModDate,
          propertyName = "testProperty",
          subjectClassName = None,
          propertyType = PropertyValueType.IntValue,
          label = label,
          comment = None,
        )
        .value
      TestApiClient
        .postJsonLdDocument(uri"/v2/ontologies/properties", request, anythingAdminUser)
        .flatMap(_.assert200)
        .tap(jsonLd => ZIO.attempt(self.freetestLastModDate.updateFrom(jsonLd)))
        .as(assertCompletes)
    },
    test("not create a property with invalid gui attribute") {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |      "@id" : "anything:hasPropertyWithWrongGuiAttribute",
           |      "@type" : "owl:ObjectProperty",
           |      "knora-api:subjectType" : {
           |        "@id" : "anything:Thing"
           |      },
           |      "knora-api:objectType" : {
           |        "@id" : "knora-api:TextValue"
           |      } ,
           |      "rdfs:label" : [ {
           |        "@language" : "en",
           |        "@value" : "has wrong GUI attribute"
           |      } ],
           |      "rdfs:subPropertyOf" : [ {
           |        "@id" : "knora-api:hasValue"
           |      } ],
           |      "salsah-gui:guiElement" : {
           |        "@id" : "salsah-gui:SimpleText"
           |      },
           |      "salsah-gui:guiAttribute" : [ "size=80", "maxilength=100" ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin
      TestApiClient
        .postJsonLdDocument(uri"/v2/ontologies/properties", params, anythingAdminUser)
        .flatMap(_.assert400)
        .as(assertCompletes)
    },
    test("not create a property with invalid gui attribute value") {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |      "@id" : "anything:hasPropertyWithWrongGuiAttribute",
           |      "@type" : "owl:ObjectProperty",
           |      "knora-api:subjectType" : {
           |        "@id" : "anything:Thing"
           |      },
           |      "knora-api:objectType" : {
           |        "@id" : "knora-api:TextValue"
           |      } ,
           |      "rdfs:label" : [ {
           |        "@language" : "en",
           |        "@value" : "has wrong GUI attribute"
           |      } ],
           |      "rdfs:subPropertyOf" : [ {
           |        "@id" : "knora-api:hasValue"
           |      } ],
           |      "salsah-gui:guiElement" : {
           |        "@id" : "salsah-gui:SimpleText"
           |      },
           |      "salsah-gui:guiAttribute" : [ "size=80", "maxlength=100.7" ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      TestApiClient
        .postJsonLdDocument(uri"/v2/ontologies/properties", params, anythingAdminUser)
        .flatMap(_.assert400)
        .as(assertCompletes)
    },
  )
}
