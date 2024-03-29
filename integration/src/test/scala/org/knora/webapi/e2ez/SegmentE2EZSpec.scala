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

object KnoraBaseJsonModels {

  final case class InternalIri(`@id`: String)
  object InternalIri { implicit val codec: JsonCodec[InternalIri] = DeriveJsonCodec.gen[InternalIri] }

  final case class AnyUri(
    `@value`: String,
    `@type`: String = "xsd:anyURI",
  )
  object AnyUri { implicit val codec: JsonCodec[AnyUri] = DeriveJsonCodec.gen[AnyUri] }

  final case class DateTimeStamp(
    `@value`: String,
    `@type`: String = "xsd:dateTimeStamp",
  )
  object DateTimeStamp { implicit val codec: JsonCodec[DateTimeStamp] = DeriveJsonCodec.gen[DateTimeStamp] }

  final case class Decimal(
    `@value`: String,
    `@type`: String = "xsd:decimal",
  )
  object Decimal { implicit val codec: JsonCodec[Decimal] = DeriveJsonCodec.gen[Decimal] }

  final case class Link(
    `@id`: String,
    `@type`: String,
    `rdfs:label`: String,
    `knora-api:attachedToProject`: InternalIri,
    `knora-api:attachedToUser`: InternalIri,
    `knora-api:hasPermissions`: String,
    `knora-api:userHasPermission`: String,
    `knora-api:arkUrl`: AnyUri,
    `knora-api:versionArkUrl`: AnyUri,
  )
  object Link { implicit val codec: JsonCodec[Link] = DeriveJsonCodec.gen[Link] }

  final case class IntervalValue(
    `@id`: String,
    `@type`: String = "knora-api:IntervalValue",
    `knora-api:valueHasUUID`: String,
    `knora-api:intervalValueHasStart`: Decimal,
    `knora-api:intervalValueHasEnd`: Decimal,
    `knora-api:attachedToUser`: InternalIri,
    `knora-api:valueCreationDate`: DateTimeStamp,
    `knora-api:hasPermissions`: String,
    `knora-api:userHasPermission`: String,
    `knora-api:arkUrl`: AnyUri,
    `knora-api:versionArkUrl`: AnyUri,
  )
  object IntervalValue { implicit val codec: JsonCodec[IntervalValue] = DeriveJsonCodec.gen[IntervalValue] }

  final case class LinkValue(
    `@id`: String,
    `@type`: String = "knora-api:LinkValue",
    `knora-api:valueHasUUID`: String,
    `knora-api:linkValueHasTarget`: InternalIri,
    `knora-api:attachedToUser`: InternalIri,
    `knora-api:valueCreationDate`: DateTimeStamp,
    `knora-api:hasPermissions`: String,
    `knora-api:userHasPermission`: String,
    `knora-api:arkUrl`: AnyUri,
    `knora-api:versionArkUrl`: AnyUri,
  )
  object LinkValue { implicit val codec: JsonCodec[LinkValue] = DeriveJsonCodec.gen[LinkValue] }

  final case class TextValue(
    `@id`: String,
    `@type`: String = "knora-api:TextValue",
    `knora-api:valueHasUUID`: String,
    `knora-api:valueAsString`: String, // LATER: could be XML, HTML, etc.
    `knora-api:attachedToUser`: InternalIri,
    `knora-api:valueCreationDate`: DateTimeStamp,
    `knora-api:hasPermissions`: String,
    `knora-api:userHasPermission`: String,
    `knora-api:arkUrl`: AnyUri,
    `knora-api:versionArkUrl`: AnyUri,
  )
  object TextValue { implicit val codec: JsonCodec[TextValue] = DeriveJsonCodec.gen[TextValue] }

  final case class ResourcePreviewResponse(
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

  final case class VideoSegmentResourceResponse(
    `@id`: String,
    `@type`: String,
    `rdfs:label`: String,
    // `knora-api:isVideoSegmentOf`: AnyUri, // TODO: Why is this not set? Should it be?
    `knora-api:isVideoSegmentOfValue`: LinkValue,
    `knora-api:hasSegmentBounds`: IntervalValue,
    `knora-api:hasComment`: TextValue,
    `knora-api:attachedToProject`: InternalIri,
    `knora-api:attachedToUser`: InternalIri,
    `knora-api:hasPermissions`: String,
    `knora-api:userHasPermission`: String,
    `knora-api:arkUrl`: AnyUri,
    `knora-api:versionArkUrl`: AnyUri,
    `@context`: Map[String, String],
  )
  object VideoSegmentResourceResponse {
    implicit val codec: JsonCodec[VideoSegmentResourceResponse] = DeriveJsonCodec.gen[VideoSegmentResourceResponse]
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
          response    <- ZIO.fromEither(responseStr.fromJson[KnoraBaseJsonModels.ResourcePreviewResponse])
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
          token       <- getToken("root@example.com", "test")
          responseStr <- sendGetRequestStringOrFail(s"/v2/resources/${urlEncode(videoSegmentIri)}", Some(token))
          response    <- ZIO.fromEither(responseStr.fromJson[KnoraBaseJsonModels.VideoSegmentResourceResponse])
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
    suite("Segments using subcalsses of knora-base classes")(
    )

  private val audioSegmentWithoutSubclasses =
    suite("Create an Audio Segment using knora-base classes directly")(
    )

  private val audioSegmentWithSubclasses =
    suite("Segments using subcalsses of knora-base classes")(
    )

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
