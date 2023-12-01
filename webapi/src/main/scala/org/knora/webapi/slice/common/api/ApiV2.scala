/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.tapir.Codec
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecFormat
import sttp.tapir.DecodeResult
import sttp.tapir.EndpointIO
import sttp.tapir.EndpointInput
import sttp.tapir.Validator
import sttp.tapir.header
import sttp.tapir.query

import dsp.errors.BadRequestException
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.ApiV2Schema
import org.knora.webapi.JsonLdRendering
import org.knora.webapi.MarkupRendering
import org.knora.webapi.Rendering
import org.knora.webapi.messages.util.rdf.JsonLD
import org.knora.webapi.messages.util.rdf.RdfFormat
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions

object ApiV2 {

  /**
   * The names of the supported Http Headers.
   */
  object Headers {

    /**
     * The name of the HTTP header that can be used to specify how markup should be returned with
     * text values.
     */
    val xKnoraAcceptMarkup: String = "x-knora-accept-markup"

    /**
     * The name of the HTTP header in which results from a project can be requested.
     */
    val xKnoraAcceptProject: String = "x-knora-accept-project"

    /**
     * The name of the HTTP header in which an ontology schema can be requested.
     */
    val xKnoraAcceptSchemaHeader: String = "x-knora-accept-schema"

    /**
     * The name of the HTTP header that can be used to request hierarchical or flat JSON-LD.
     */
    val xKnoraJsonLdRendering: String = "x-knora-json-ld-rendering"
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

  object Inputs {
    import Codecs.{apiV2SchemaListCodec, jsonLdRenderingListCodec, markupRenderingListCode}

    // ApiV2Schema inputs
    val defaultApiV2Schema: ApiV2Schema = ApiV2Complex
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

    // MarkupRendering inputs
    private val markupRenderingHeader: EndpointIO.Header[Option[MarkupRendering]] =
      header[Option[MarkupRendering]](Headers.xKnoraAcceptMarkup)
        .description(s"""The markup rendering to be used for the request (XML or standoff).""".stripMargin)
    private val markupRenderingQuery: EndpointInput.Query[Option[MarkupRendering]] =
      query[Option[MarkupRendering]](QueryParams.markup)
        .description(s"""The markup rendering to be used for the request (XML or standoff).""".stripMargin)
    private val markupRendering: EndpointInput[Option[MarkupRendering]] = markupRenderingQuery
      .and(markupRenderingHeader)
      .map(_ match {
        case (fromQuery: Some[MarkupRendering], _)  => fromQuery
        case (_, fromHeader: Some[MarkupRendering]) => fromHeader
        case _                                      => None
      })(s => (s, s))

    // JsonLdRendering inputs
    private val jsonLdRenderingHeader =
      header[Option[JsonLdRendering]](Headers.xKnoraJsonLdRendering)
        .description(s"""The JSON-LD rendering to be used for the request (flat or hierarchical).
                        |If not specified, hierarchical JSON-LD will be used.""".stripMargin)

    // Rendering inputs (JsonLdRendering and MarkupRendering)
    private val renderingSet: EndpointInput[Set[Rendering]] = jsonLdRenderingHeader
      .and(markupRendering)
      .map { case (jsonLd: Option[JsonLdRendering], markup: Option[MarkupRendering]) => Set(jsonLd, markup).flatten } {
        (s: Set[Rendering]) =>
          (
            s.collectFirst { case jsonLd: JsonLdRendering => jsonLd },
            s.collectFirst { case markup: MarkupRendering => markup }
          )
      }

    // RdfFormat input
    val defaultRdfFormat: RdfFormat = JsonLD
    private val rdfFormat: EndpointIO.Header[RdfFormat] = header[Option[MediaType]](HeaderNames.Accept)
      .description(
        s"""The RDF format to be used for the request. Valid values are: ${RdfFormat.values}
           |If not specified or unknown, the fallback RDF format $defaultRdfFormat will be used.""".stripMargin
      )
      .mapDecode(s => DecodeResult.Value(s.map(RdfFormat.from).getOrElse(defaultRdfFormat)))(it => Some(it.mediaType))
      .validate(Validator.pass[RdfFormat])

    // FormatOptions input
    val formatOptions: EndpointInput[FormatOptions] = rdfFormat
      .and(apiV2Schema)
      .and(renderingSet)
      .map { case (format, schema, rendering) => FormatOptions(format, schema, rendering) }(s =>
        (s.rdfFormat, s.schema, s.rendering)
      )
  }

  private object Codecs {
    private def codecFromStringCodec[A](f: String => Either[String, A], g: A => String): PlainCodec[A] =
      Codec.string.mapDecode(f(_).fold(e => DecodeResult.Error(e, BadRequestException(e)), DecodeResult.Value(_)))(g)

    // Codec for ApiV2Schema
    implicit val apiV2SchemaListCodec: Codec[List[String], Option[ApiV2Schema], CodecFormat.TextPlain] =
      Codec.listHeadOption(codecFromStringCodec(ApiV2Schema.from, _.name))

    // Codecs for Rendering (JsonLdRendering and MarkupRendering)
    implicit val jsonLdRenderingListCodec: Codec[List[String], Option[JsonLdRendering], CodecFormat.TextPlain] =
      Codec.listHeadOption(codecFromStringCodec(JsonLdRendering.from, _.name))
    implicit val markupRenderingListCode: Codec[List[String], Option[MarkupRendering], CodecFormat.TextPlain] =
      Codec.listHeadOption(codecFromStringCodec(MarkupRendering.from, _.name))
  }
}
