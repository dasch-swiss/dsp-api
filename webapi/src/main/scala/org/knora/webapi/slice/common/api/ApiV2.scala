package org.knora.webapi.slice.common.api

import sttp.model.HeaderNames
import sttp.tapir.Codec
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecFormat
import sttp.tapir.DecodeResult
import sttp.tapir.EndpointIO
import sttp.tapir.EndpointInput
import sttp.tapir.header
import sttp.tapir.query

import dsp.errors.BadRequestException
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.ApiV2Schema
import org.knora.webapi.JsonLdRendering
import org.knora.webapi.MarkupRendering
import org.knora.webapi.Rendering
import org.knora.webapi.SchemaRendering
import org.knora.webapi.*
import org.knora.webapi.messages.util.rdf.RdfFormat

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

  object Codecs {

    private def codecFromStringCodec[A](f: String => Either[String, A], g: A => String): PlainCodec[A] =
      Codec.string.mapDecode(f(_).fold(e => DecodeResult.Error(e, BadRequestException(e)), DecodeResult.Value(_)))(g)

    private implicit val apiV2SchemaListCodec: Codec[List[String], Option[ApiV2Schema], CodecFormat.TextPlain] =
      Codec.listHeadOption(codecFromStringCodec(ApiV2Schema.from, _.name))
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

    private implicit val jsonLdRenderingListCode: Codec[List[String], Option[JsonLdRendering], CodecFormat.TextPlain] =
      Codec.listHeadOption(codecFromStringCodec(JsonLdRendering.from, _.name))
    private val jsonLdRenderingHeader =
      header[Option[JsonLdRendering]](Headers.xKnoraJsonLdRendering)
        .description(s"""The JSON-LD rendering to be used for the request (flat or hierarchical).
                        |If not specified, hierarchical JSON-LD will be used.""".stripMargin)

    private implicit val markupRenderingListCode: Codec[List[String], Option[MarkupRendering], CodecFormat.TextPlain] =
      Codec.listHeadOption(codecFromStringCodec(MarkupRendering.from, _.name))
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

    private val apiV2Rendering: EndpointInput[Set[Rendering]] = jsonLdRenderingHeader
      .and(markupRendering)
      .map { case (jsonLd: Option[JsonLdRendering], markup: Option[MarkupRendering]) => Set(jsonLd, markup).flatten } {
        (s: Set[Rendering]) =>
          (
            s.collectFirst { case jsonLd: JsonLdRendering => jsonLd },
            s.collectFirst { case markup: MarkupRendering => markup }
          )
      }

    val apiV2SchemaRendering: EndpointInput[SchemaRendering] = apiV2Schema
      .and(apiV2Rendering)
      .map { case (schema, rendering) => SchemaRendering(schema, rendering) }(s => (s.schema, s.rendering))

    private implicit val rdfFormatCodec: Codec[String, RdfFormat, CodecFormat.TextPlain] =
      Codec.mediaType.mapDecode(mt =>
        RdfFormat.from(mt).fold(e => DecodeResult.Error(e, BadRequestException(e)), DecodeResult.Value(_))
      )(_.mediaType)
    private implicit val rdfFormatListCodec: Codec[List[String], RdfFormat, CodecFormat.TextPlain] =
      Codec.listHead(rdfFormatCodec)
    val rdfFormat: EndpointIO.Header[RdfFormat] = header[RdfFormat](HeaderNames.Accept)
      .description(
        s"""The MediaType for the RDF format to be used for the request (JSON-LD, Turtle, RDF/XML, TriG, N-Quads).""".stripMargin
      )
  }
  val defaultApiV2Schema: ApiV2Schema = ApiV2Complex
}
