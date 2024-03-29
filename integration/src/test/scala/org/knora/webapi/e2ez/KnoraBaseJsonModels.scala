package org.knora.webapi.e2ez

import zio.json._

import KnoraBaseJsonModels.ValuePrimitives._
import KnoraBaseJsonModels.ValueObjects._

object KnoraBaseJsonModels {

  object ValuePrimitives {

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

  }

  object ValueObjects {

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

  }

  object ResourceResponses {

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

    final case class AudioSegmentResourceResponse(
      `@id`: String,
      `@type`: String,
      `rdfs:label`: String,
      // `knora-api:isAudioSegmentOf`: AnyUri, // TODO: Why is this not set? Should it be?
      `knora-api:isAudioSegmentOfValue`: LinkValue,
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
    object AudioSegmentResourceResponse {
      implicit val codec: JsonCodec[AudioSegmentResourceResponse] = DeriveJsonCodec.gen[AudioSegmentResourceResponse]
    }
  }
}
