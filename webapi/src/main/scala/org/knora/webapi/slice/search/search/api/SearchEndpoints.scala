/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.search.api

import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.*
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.BadRequestException
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.rdf.JsonLD
import org.knora.webapi.messages.util.rdf.RdfFormat
import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.SecuredEndpointAndZioHandler
import org.knora.webapi.slice.search.search.api.ApiV2.Headers
import org.knora.webapi.slice.search.search.api.ApiV2.QueryParams
import org.knora.webapi.slice.search.search.api.ApiV2.defaultApiV2Schema
import org.knora.webapi.slice.search.search.api.ApiV2Codecs.apiV2SchemaRendering

final case class SearchEndpoints(baseEndpoints: BaseEndpoints) {

  private val tags       = List("v2", "search")
  private val searchBase = "v2" / "searchextended"

  val postGravsearch = baseEndpoints.securedEndpoint.post
    .in(searchBase / "gravsearch")
    .in(stringBody)
    .in(apiV2SchemaRendering)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Search for resources using a Gravsearch query.")
}

object SearchEndpoints {
  val layer = ZLayer.derive[SearchEndpoints]
}

final case class SearchEndpointsHandler(
  searchEndpoints: SearchEndpoints,
  searchResponderV2: SearchResponderV2,
  renderer: KnoraResponseRenderer
) {

  val postGravsearch =
    SecuredEndpointAndZioHandler[(String, SchemaRendering[ApiV2Schema, Rendering]), (String, MediaType)](
      searchEndpoints.postGravsearch,
      (user: UserADM) => { case (query: String, s: SchemaRendering[ApiV2Schema, Rendering]) =>
        searchResponderV2.gravsearchV2(query, s, user).flatMap(renderer.render(_, JsonLD, s))
      }
    )
}

object SearchEndpointsHandler {
  val layer = ZLayer.derive[SearchEndpointsHandler]
}

final class KnoraResponseRenderer(appConfig: AppConfig) {
  def render(
    response: KnoraResponseV2,
    format: RdfFormat,
    rendering: SchemaRendering[ApiV2Schema, Rendering]
  ): Task[(String, MediaType)] =
    ZIO
      .attempt(response.format(format, rendering.schema, rendering.rendering, appConfig))
      .map((_, format.mediaType))
}
object KnoraResponseRenderer {

  val layer = ZLayer.derive[KnoraResponseRenderer]
}

object ApiV2 {

  /**
   * The names of the supported Http Headers.
   */
  object Headers {

    /**
     * The name of the HTTP header in which an ontology schema can be requested.
     */
    val xKnoraAcceptSchemaHeader: String = "x-knora-accept-schema"

    /**
     * The name of the HTTP header that can be used to request hierarchical or flat JSON-LD.
     */
    val xKnoraJsonLdRendering: String = "x-knora-json-ld-rendering"

    /**
     * The name of the HTTP header that can be used to specify how markup should be returned with
     * text values.
     */
    val xKnoraAcceptMarkup: String = "x-knora-accept-markup"

    /**
     * The name of the HTTP header in which results from a project can be requested.
     */
    val xKnoraAcceptProject: String = "x-knora-accept-project"
  }

  object QueryParams {

    /**
     * The name of the URL parameter in which an ontology schema can be requested.
     */
    val schema: String = "schema"

    /**
     * The name of the URL parameter that can be used to specify how markup should be returned
     * with text values.
     */
    val markup: String = "markup"
  }

  val defaultApiV2Schema: ApiV2Schema = ApiV2Complex
}

object ApiV2Codecs {

  private def codecFromStringCodec[A](f: String => Either[String, A], g: A => String): PlainCodec[A] =
    Codec.string.mapDecode(f(_).fold(e => DecodeResult.Error(e, BadRequestException(e)), DecodeResult.Value(_)))(g)

  private implicit val apiV2SchemaCodec: PlainCodec[ApiV2Schema] = codecFromStringCodec(ApiV2Schema.from, _.name)
  private implicit val apiV2SchemaListCodec: Codec[List[String], Option[ApiV2Schema], CodecFormat.TextPlain] =
    Codec.listHeadOption(apiV2SchemaCodec)

  private val apiV2SchemaHeader = header[Option[ApiV2Schema]](Headers.xKnoraAcceptSchemaHeader)
    .description(s"""The ontology schema to be used for the request.
                    |If not specified, the default schema $defaultApiV2Schema  will be used.""".stripMargin)
  private val apiV2SchemaQuery = query[Option[ApiV2Schema]](QueryParams.schema)
    .description(s"""The ontology schema to be used for the request. 
                    |If not specified, the default schema $defaultApiV2Schema will be used.""".stripMargin)

  private val apiV2Schema: EndpointInput[ApiV2Schema] =
    apiV2SchemaHeader
      .and(apiV2SchemaQuery)
      .map(_ match {
        case (Some(fromHeader), _) => fromHeader
        case (_, Some(fromQuery))  => fromQuery
        case _                     => defaultApiV2Schema
      })(s => (Some(s), Some(s)))

  private implicit val jsonLdRenderingCodec: PlainCodec[JsonLdRendering] =
    codecFromStringCodec(JsonLdRendering.from, _.name)
  private implicit val jsonLdRenderingListCode: Codec[List[String], Option[JsonLdRendering], CodecFormat.TextPlain] =
    Codec.listHeadOption(jsonLdRenderingCodec)

  private val jsonLdRenderingHeader = header[Option[JsonLdRendering]](Headers.xKnoraJsonLdRendering)
    .description(s"""The JSON-LD rendering to be used for the request (flat or hierarchical). 
                    |If not specified, hierarchical JSON-LD will be used.""".stripMargin)

  private implicit val markupRenderingCodec: PlainCodec[MarkupRendering] =
    codecFromStringCodec(MarkupRendering.from, _.name)
  private implicit val markupRenderingListCode: Codec[List[String], Option[MarkupRendering], CodecFormat.TextPlain] =
    Codec.listHeadOption(markupRenderingCodec)

  private val markupRenderingHeader = header[Option[MarkupRendering]](Headers.xKnoraAcceptMarkup)
    .description(s"""The markup rendering to be used for the request (XML or standoff).""".stripMargin)
  private val markupRenderingQuery = query[Option[MarkupRendering]](QueryParams.markup)
    .description(s"""The markup rendering to be used for the request (XML or standoff).""".stripMargin)
  private val markupRendering: EndpointInput[Option[MarkupRendering]] =
    markupRenderingQuery
      .and(markupRenderingHeader)
      .map(_ match {
        case (fromQuery: Some[MarkupRendering], _)  => fromQuery
        case (_, fromHeader: Some[MarkupRendering]) => fromHeader
        case _                                      => None
      })(s => (s, s))

  private val apiV2Rendering: EndpointInput[Set[Rendering]] =
    jsonLdRenderingHeader
      .and(markupRendering)
      .map(tuple => Set(tuple._1, tuple._2).flatten)((s: Set[Rendering]) =>
        (
          s.collectFirst { case jsonLd: JsonLdRendering => jsonLd },
          s.collectFirst { case standoff: MarkupRendering => standoff }
        )
      )

  val apiV2SchemaRendering: EndpointInput[SchemaRendering[ApiV2Schema, Rendering]] =
    apiV2Schema.and(apiV2Rendering).map(t => SchemaRendering(t._1, t._2))(s => (s.schema, s.rendering))
}