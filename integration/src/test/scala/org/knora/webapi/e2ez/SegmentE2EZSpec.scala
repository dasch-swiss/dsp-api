/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2ez

import zio._
import zio.json._
import zio.test._

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject

object ApiModels {

  final case class InternalIri(`@id`: String)
  object InternalIri { implicit val codec: JsonCodec[InternalIri] = DeriveJsonCodec.gen[InternalIri] }

  final case class AnyUri(
    `@value`: String,
    `@type`: String = "xsd:anyURI",
  )
  object AnyUri { implicit val codec: JsonCodec[AnyUri] = DeriveJsonCodec.gen[AnyUri] }

  case class ResourcePreviewResponse(
    `@id`: String,
    `@type`: String,
    `rdfs:label`: String,
    `knora-api:attachedToProject`: InternalIri,
    `knora-api:attachedToUser`: InternalIri,
    `knora-api:hasPermissions`: String,
    `knora-api:userHasPermission`: String,
    `knora-api:arkUrl`: AnyUri,
    `knora-api:versionArkUrl`: AnyUri,
    `@context`: Map[String, String],
  )
  object ResourcePreviewResponse {
    implicit val codec: JsonCodec[ResourcePreviewResponse] = DeriveJsonCodec.gen[ResourcePreviewResponse]
  }
}

object SegmentE2EZSpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

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
             |""".stripMargin
        for {
          token       <- getToken("root@example.com", "test")
          responseStr <- sendPostRequestStringOrFail("/v2/resources", createPayload, Some(token))
          response    <- ZIO.fromEither(responseStr.fromJson[ApiModels.ResourcePreviewResponse])
          _            = videoSegmentIri = response.`@id`
        } yield assertTrue(
          response.`@type` == "knora-api:VideoSegment",
          response.`rdfs:label` == "Test Video Segment",
          response.`knora-api:attachedToProject`.`@id` == "http://rdfh.ch/projects/0001",
          response.`knora-api:attachedToUser`.`@id` == "http://rdfh.ch/users/root",
        )
      }

      test("Get the created instance of `knora-base:VideoSegment`") {
        for {

          token <- getToken("root@example.com", "test")
          _     <- sendGetRequestStringOrFail(s"/v2/resources/${urlEncode(videoSegmentIri)}", Some(token)).debug
        } yield assertTrue(true)
      }
    }

  private val withSubclasses =
    suite("Segments using subcalsses of knora-base classes")(
    )

  override def e2eSpec: Spec[env, Any] =
    suite("SegmentE2EZSpec")(
      videoSegmentWithoutSubclasses,
      withSubclasses,
    )
}
