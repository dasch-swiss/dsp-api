package org.knora.webapi.slice.search.search.api

import dsp.errors.BadRequestException
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.search.search.api.ApiV2.Headers.xKnoraAcceptSchemaHeader
import org.knora.webapi.slice.search.search.api.ApiV2.QueryParams.schemaQueryParam
import org.knora.webapi.slice.search.search.api.ApiV2.defaultApiV2Schema
import org.knora.webapi.slice.search.search.api.ApiV2Codecs.apiV2Schema
import org.knora.webapi.{ApiV2Complex, ApiV2Schema}
import sttp.tapir.*
import sttp.tapir.Codec.PlainCodec
import zio.ZLayer

final case class SearchEndpoints(baseEndpoints: BaseEndpoints) {

  private val tags       = List("v2", "search")
  private val searchBase = "v2" / "searchextended"

  val postGravsearch = baseEndpoints.withUserEndpoint.post
    .in(searchBase / "gravsearch")
    .in(apiV2Schema)
    .in(stringBody)
    .out(stringBody)
    .tags(tags)
    .description("Search for resources using a Gravsearch query.")
}

object SearchEndpoints {
  val layer = ZLayer.derive[SearchEndpoints]
}

object ApiV2 {

  object Headers {

    /**
     * The name of the HTTP header in which an ontology schema can be requested.
     */
    val xKnoraAcceptSchemaHeader: String = "x-knora-accept-schema"
  }
  object QueryParams {

    /**
     * The name of the URL parameter in which an ontology schema can be requested.
     */
    val schemaQueryParam: String = "schema"
  }

  val defaultApiV2Schema: ApiV2Schema = ApiV2Complex
}

object ApiV2Codecs {

  implicit val apiV2SchemaCodec: PlainCodec[ApiV2Schema] =
    Codec.string.mapDecode(s =>
      ApiV2Schema.from(s).fold(e => DecodeResult.Error(e, BadRequestException(e)), DecodeResult.Value(_))
    )(_.name)

  implicit val apiV2SchemaListCodec: Codec[List[String], Option[ApiV2Schema], CodecFormat.TextPlain] =
    Codec.listHeadOption(apiV2SchemaCodec)

  private val apiV2SchemaHeader = header[Option[ApiV2Schema]](xKnoraAcceptSchemaHeader)
    .description(s"""The ontology schema to be used for the request. 
                    |If not specified, the default schema $defaultApiV2Schema  will be used.""".stripMargin)

  private val apiV2SchemaQuery = query[Option[ApiV2Schema]](schemaQueryParam)
    .description(s"""The ontology schema to be used for the request. 
                    |If not specified, the default schema $defaultApiV2Schema will be used.""".stripMargin)

  val apiV2Schema: EndpointInput[ApiV2Schema] =
    apiV2SchemaHeader
      .and(apiV2SchemaQuery)
      .map { headerOrQuery =>
        headerOrQuery match {
          case (Some(fromHeader), _) => fromHeader
          case (_, Some(fromQuery))  => fromQuery
          case _                     => defaultApiV2Schema
        }
      }(s => (Some(s), Some(s)))

}
