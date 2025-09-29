/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2ez

import sttp.client4.*
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2ez.KnoraBaseJsonModels.ResourceResponses.AudioSegmentResourceResponse
import org.knora.webapi.e2ez.KnoraBaseJsonModels.ResourceResponses.ResourcePreviewResponse
import org.knora.webapi.e2ez.KnoraBaseJsonModels.ResourceResponses.VideoSegmentResourceResponse
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.anythingRdfTestdata
import org.knora.webapi.sharedtestdata.SharedTestDataADM.rootUser
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.testservices.TestOntologyApiClient
object SegmentE2EZSpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = anythingRdfTestdata

  private val videoSegmentWithoutSubclasses =
    suiteAll("Create a Video Segment using knora-base classes directly") {
      var videoSegmentIri: String = ""

      test("Create an instance of `knora-base:VideoSegment`") {
        val createPayload =
          """|{
             |  "@type": "knora-api:VideoSegment",
             |  "knora-api:hasComment": {
             |    "@type": "knora-api:TextValue",
             |    "knora-api:valueAsString": "This is a test video segment."
             |  },
             |  "knora-api:hasSegmentBounds": {
             |    "@type": "knora-api:IntervalValue",
             |    "knora-api:intervalValueHasStart" : {
             |      "@type" : "xsd:decimal",
             |      "@value" : "1.0"
             |    },
             |    "knora-api:intervalValueHasEnd" : {
             |      "@type" : "xsd:decimal",
             |      "@value" : "3.0"
             |    }
             |  },
             |  "knora-api:isVideoSegmentOfValue": {
             |    "@type": "knora-api:LinkValue",
             |    "knora-api:linkValueHasTargetIri": {
             |      "@id": "http://rdfh.ch/0001/zUelKon-SdmuL9iiHMgnGw"
             |    }
             |  },
             |  "rdfs:label": "Test Video Segment",
             |  "knora-api:attachedToProject": {
             |    "@id": "http://rdfh.ch/projects/0001"
             |  },
             |  "@context": {
             |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
             |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
             |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
             |  }
             |}
             |""".stripMargin.fromJson[Json].fold(err => throw new RuntimeException(err), identity)
        for {
          response <- TestApiClient
                        .postJson[ResourcePreviewResponse, Json](uri"/v2/resources", createPayload, rootUser)
                        .flatMap(_.assert200)
          _ = videoSegmentIri = response.`@id`
        } yield assertTrue(
          response.`@type` == "knora-api:VideoSegment",
          response.`rdfs:label` == "Test Video Segment",
          response.`knora-api:attachedToProject`.`@id` == "http://rdfh.ch/projects/0001",
          response.`knora-api:attachedToUser`.`@id` == "http://rdfh.ch/users/root",
        )
      }

      test("Get the created instance of `knora-base:VideoSegment`") {
        for {
          response <- TestApiClient
                        .getJson[VideoSegmentResourceResponse](uri"/v2/resources/$videoSegmentIri", rootUser)
                        .flatMap(_.assert200)
        } yield assertTrue(
          response.`@type` == "knora-api:VideoSegment",
          response.`rdfs:label` == "Test Video Segment",
          response.`knora-api:attachedToProject`.`@id` == "http://rdfh.ch/projects/0001",
          response.`knora-api:attachedToUser`.`@id` == "http://rdfh.ch/users/root",
          response.`knora-api:hasComment`.`knora-api:valueAsString` == "This is a test video segment.",
          BigDecimal(response.`knora-api:hasSegmentBounds`.`knora-api:intervalValueHasStart`.`@value`) == 1.0,
          BigDecimal(response.`knora-api:hasSegmentBounds`.`knora-api:intervalValueHasEnd`.`@value`) == 3.0,
          response.`knora-api:isVideoSegmentOfValue`.`knora-api:linkValueHasTarget`.`@id` == "http://rdfh.ch/0001/zUelKon-SdmuL9iiHMgnGw",
        )
      }
    }

  private val videoSegmentWithSubclasses =
    suiteAll("Create a Video Segment using subclasses of knora-base classes") {
      var videoSegmentIri: String = ""

      test("Define a subclass of `knora-api:VideoSegment` in the anything ontology") {
        def createPayload(lmd: String) =
          s"""|{
              |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
              |  "@type" : "owl:Ontology",
              |  "knora-api:lastModificationDate" : {
              |    "@type" : "xsd:dateTimeStamp",
              |    "@value" : "$lmd"
              |  },
              |  "@graph" : [
              |    {
              |      "@id": "anything:VideoSegment",
              |      "@type": "owl:Class",
              |      "rdfs:label": {
              |        "@language": "en",
              |        "@value": "Subclass of knora-api:VideoSegment"
              |      },
              |      "rdfs:comment": {
              |        "@language": "en",
              |        "@value": "For testing purposes"
              |      },
              |      "rdfs:subClassOf": [
              |        {
              |          "@id": "knora-api:VideoSegment"
              |        }
              |      ]
              |    }
              |  ],
              |  "@context" : {
              |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
              |    "owl" : "http://www.w3.org/2002/07/owl#",
              |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
              |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
              |    "anything" :  "http://0.0.0.0:3333/ontology/0001/anything/v2#"
              |  }
              |}""".stripMargin
        for {
          lmd <- TestOntologyApiClient.getLastModificationDate("http://0.0.0.0:3333/ontology/0001/anything/v2")
          res <- TestApiClient
                   .postJsonLd(uri"/v2/ontologies/classes", createPayload(lmd), rootUser)
                   .flatMap(_.assert200)
        } yield assertTrue(res.contains("anything:VideoSegment"))
      }

      test("Create an instance of `anything:VideoSegment`") {
        val createPayload =
          """|{
             |  "@type": "anything:VideoSegment",
             |  "knora-api:hasComment": {
             |    "@type": "knora-api:TextValue",
             |    "knora-api:valueAsString": "This is a test video segment."
             |  },
             |  "knora-api:hasSegmentBounds": {
             |    "@type": "knora-api:IntervalValue",
             |    "knora-api:intervalValueHasStart" : {
             |      "@type" : "xsd:decimal",
             |      "@value" : "1.0"
             |    },
             |    "knora-api:intervalValueHasEnd" : {
             |      "@type" : "xsd:decimal",
             |      "@value" : "3.0"
             |    }
             |  },
             |  "knora-api:isVideoSegmentOfValue": {
             |    "@type": "knora-api:LinkValue",
             |    "knora-api:linkValueHasTargetIri": {
             |      "@id": "http://rdfh.ch/0001/zUelKon-SdmuL9iiHMgnGw"
             |    }
             |  },
             |  "rdfs:label": "Test Video Segment Subclass",
             |  "knora-api:attachedToProject": {
             |    "@id": "http://rdfh.ch/projects/0001"
             |  },
             |  "@context": {
             |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
             |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
             |    "anything" :  "http://0.0.0.0:3333/ontology/0001/anything/v2#"
             |  }
             |}""".stripMargin.fromJson[Json].fold(err => throw new RuntimeException(err), identity)
        for {
          response <- TestApiClient
                        .postJson[ResourcePreviewResponse, Json](uri"/v2/resources", createPayload, rootUser)
                        .flatMap(_.assert200)
          _ = videoSegmentIri = response.`@id`
        } yield assertTrue(
          response.`@type` == "anything:VideoSegment",
          response.`rdfs:label` == "Test Video Segment Subclass",
          response.`knora-api:attachedToProject`.`@id` == "http://rdfh.ch/projects/0001",
          response.`knora-api:attachedToUser`.`@id` == "http://rdfh.ch/users/root",
        )
      }

      test("Get the created instance of `anything:VideoSegment`") {
        for {
          response <- TestApiClient
                        .getJson[VideoSegmentResourceResponse](uri"/v2/resources/$videoSegmentIri", rootUser)
                        .flatMap(_.assert200)
        } yield assertTrue(
          response.`@type` == "anything:VideoSegment",
          response.`rdfs:label` == "Test Video Segment Subclass",
          response.`knora-api:attachedToProject`.`@id` == "http://rdfh.ch/projects/0001",
          response.`knora-api:attachedToUser`.`@id` == "http://rdfh.ch/users/root",
          response.`knora-api:hasComment`.`knora-api:valueAsString` == "This is a test video segment.",
          BigDecimal(response.`knora-api:hasSegmentBounds`.`knora-api:intervalValueHasStart`.`@value`) == 1.0,
          BigDecimal(response.`knora-api:hasSegmentBounds`.`knora-api:intervalValueHasEnd`.`@value`) == 3.0,
          response.`knora-api:isVideoSegmentOfValue`.`knora-api:linkValueHasTarget`.`@id` == "http://rdfh.ch/0001/zUelKon-SdmuL9iiHMgnGw",
        )
      }

    }

  private val audioSegmentWithoutSubclasses =
    suiteAll("Create an Audio Segment using knora-base classes directly") {
      var audioSegmentIri: String = ""

      test("Create an instance of `knora-base:AudioSegment`") {
        val createPayload =
          """|{
             |  "@type": "knora-api:AudioSegment",
             |  "knora-api:hasComment": {
             |    "@type": "knora-api:TextValue",
             |    "knora-api:valueAsString": "This is a test audio segment."
             |  },
             |  "knora-api:hasSegmentBounds": {
             |    "@type": "knora-api:IntervalValue",
             |    "knora-api:intervalValueHasStart" : {
             |      "@type" : "xsd:decimal",
             |      "@value" : "1.0"
             |    },
             |    "knora-api:intervalValueHasEnd" : {
             |      "@type" : "xsd:decimal",
             |      "@value" : "3.0"
             |    }
             |  },
             |  "knora-api:isAudioSegmentOfValue": {
             |    "@type": "knora-api:LinkValue",
             |    "knora-api:linkValueHasTargetIri": {
             |      "@id": "http://rdfh.ch/0001/gT2msR9wS-CeLWXaj3N2aA"
             |    }
             |  },
             |  "rdfs:label": "Test Audio Segment",
             |  "knora-api:attachedToProject": {
             |    "@id": "http://rdfh.ch/projects/0001"
             |  },
             |  "@context": {
             |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
             |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
             |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
             |  }
             |}
             |""".stripMargin.fromJson[Json].fold(err => throw new RuntimeException(err), identity)
        for {
          response <- TestApiClient
                        .postJson[ResourcePreviewResponse, Json](uri"/v2/resources", createPayload, rootUser)
                        .flatMap(_.assert200)
          _ = audioSegmentIri = response.`@id`
        } yield assertTrue(
          response.`@type` == "knora-api:AudioSegment",
          response.`rdfs:label` == "Test Audio Segment",
          response.`knora-api:attachedToProject`.`@id` == "http://rdfh.ch/projects/0001",
          response.`knora-api:attachedToUser`.`@id` == "http://rdfh.ch/users/root",
        )
      }

      test("Get the created instance of `knora-base:AudioSegment`") {
        for {
          response <- TestApiClient
                        .getJson[AudioSegmentResourceResponse](uri"/v2/resources/$audioSegmentIri", rootUser)
                        .flatMap(_.assert200)
        } yield assertTrue(
          response.`@type` == "knora-api:AudioSegment",
          response.`rdfs:label` == "Test Audio Segment",
          response.`knora-api:attachedToProject`.`@id` == "http://rdfh.ch/projects/0001",
          response.`knora-api:attachedToUser`.`@id` == "http://rdfh.ch/users/root",
          response.`knora-api:hasComment`.`knora-api:valueAsString` == "This is a test audio segment.",
          BigDecimal(response.`knora-api:hasSegmentBounds`.`knora-api:intervalValueHasStart`.`@value`) == 1.0,
          BigDecimal(response.`knora-api:hasSegmentBounds`.`knora-api:intervalValueHasEnd`.`@value`) == 3.0,
          response.`knora-api:isAudioSegmentOfValue`.`knora-api:linkValueHasTarget`.`@id` == "http://rdfh.ch/0001/gT2msR9wS-CeLWXaj3N2aA",
        )
      }
    }

  private val audioSegmentWithSubclasses =
    suiteAll("Create an Audio Segment using subclasses of knora-base classes") {
      var audioSegmentIri: String = ""

      test("Define a subclass of `knora-api:AudioSegment` in the anything ontology") {
        def createPayload(lmd: String) =
          s"""|{
              |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
              |  "@type" : "owl:Ontology",
              |  "knora-api:lastModificationDate" : {
              |    "@type" : "xsd:dateTimeStamp",
              |    "@value" : "$lmd"
              |  },
              |  "@graph" : [
              |    {
              |      "@id": "anything:AudioSegment",
              |      "@type": "owl:Class",
              |      "rdfs:label": {
              |        "@language": "en",
              |        "@value": "Subclass of knora-api:AudioSegment"
              |      },
              |      "rdfs:comment": {
              |        "@language": "en",
              |        "@value": "For testing purposes"
              |      },
              |      "rdfs:subClassOf": [
              |        {
              |          "@id": "knora-api:AudioSegment"
              |        }
              |      ]
              |    }
              |  ],
              |  "@context" : {
              |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
              |    "owl" : "http://www.w3.org/2002/07/owl#",
              |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
              |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
              |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
              |  }
              |}""".stripMargin
        for {
          lmd <- TestOntologyApiClient.getLastModificationDate("http://0.0.0.0:3333/ontology/0001/anything/v2")
          res <- TestApiClient
                   .postJsonLd(uri"/v2/ontologies/classes", createPayload(lmd), rootUser)
                   .flatMap(_.assert200)
        } yield assertTrue(res.contains("anything:AudioSegment"))
      }

      test("Create an instance of `anything:AudioSegment`") {
        val createPayload =
          """|{
             |  "@type": "anything:AudioSegment",
             |  "knora-api:hasComment": {
             |    "@type": "knora-api:TextValue",
             |    "knora-api:valueAsString": "This is a test audio segment."
             |  },
             |  "knora-api:hasSegmentBounds": {
             |    "@type": "knora-api:IntervalValue",
             |    "knora-api:intervalValueHasStart" : {
             |      "@type" : "xsd:decimal",
             |      "@value" : "1.0"
             |    },
             |    "knora-api:intervalValueHasEnd" : {
             |      "@type" : "xsd:decimal",
             |      "@value" : "3.0"
             |    }
             |  },
             |  "knora-api:isAudioSegmentOfValue": {
             |    "@type": "knora-api:LinkValue",
             |    "knora-api:linkValueHasTargetIri": {
             |      "@id": "http://rdfh.ch/0001/gT2msR9wS-CeLWXaj3N2aA"
             |    }
             |  },
             |  "rdfs:label": "Test Audio Segment Subclass",
             |  "knora-api:attachedToProject": {
             |    "@id": "http://rdfh.ch/projects/0001"
             |  },
             |  "@context": {
             |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
             |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
             |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
             |  }
             |}""".stripMargin.fromJson[Json].fold(err => throw new RuntimeException(err), identity)
        for {
          response <- TestApiClient
                        .postJson[ResourcePreviewResponse, Json](uri"/v2/resources", createPayload, rootUser)
                        .flatMap(_.assert200)
          _ = audioSegmentIri = response.`@id`
        } yield assertTrue(
          response.`@type` == "anything:AudioSegment",
          response.`rdfs:label` == "Test Audio Segment Subclass",
          response.`knora-api:attachedToProject`.`@id` == "http://rdfh.ch/projects/0001",
          response.`knora-api:attachedToUser`.`@id` == "http://rdfh.ch/users/root",
        )
      }

      test("Get the created instance of `knora-base:AudioSegment`") {
        for {
          response <- TestApiClient
                        .getJson[AudioSegmentResourceResponse](uri"/v2/resources/$audioSegmentIri", rootUser)
                        .flatMap(_.assert200)
        } yield assertTrue(
          response.`@type` == "anything:AudioSegment",
          response.`rdfs:label` == "Test Audio Segment Subclass",
          response.`knora-api:attachedToProject`.`@id` == "http://rdfh.ch/projects/0001",
          response.`knora-api:attachedToUser`.`@id` == "http://rdfh.ch/users/root",
          response.`knora-api:hasComment`.`knora-api:valueAsString` == "This is a test audio segment.",
          BigDecimal(response.`knora-api:hasSegmentBounds`.`knora-api:intervalValueHasStart`.`@value`) == 1.0,
          BigDecimal(response.`knora-api:hasSegmentBounds`.`knora-api:intervalValueHasEnd`.`@value`) == 3.0,
          response.`knora-api:isAudioSegmentOfValue`.`knora-api:linkValueHasTarget`.`@id` == "http://rdfh.ch/0001/gT2msR9wS-CeLWXaj3N2aA",
        )
      }
    }

  override def e2eSpec: Spec[env, Any] =
    suite("SegmentE2EZSpec")(
      suite("Video Segments")(
        videoSegmentWithoutSubclasses,
        videoSegmentWithSubclasses,
      ),
      suite("Audio Segments")(
        audioSegmentWithoutSubclasses,
        audioSegmentWithSubclasses,
      ),
    )
}
