/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer

import java.nio.charset.StandardCharsets

import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.store.triplestore.errors.SparqlPassthroughOverloadedException
import org.knora.webapi.store.triplestore.errors.SparqlPassthroughTimedOutException
import org.knora.webapi.store.triplestore.errors.SparqlRequestTooLargeException
import org.knora.webapi.store.triplestore.errors.SparqlResponseTooLargeException
import org.knora.webapi.store.triplestore.errors.SparqlStoreUnavailableException
import org.knora.webapi.store.triplestore.errors.SparqlUpstreamRejectedException

/**
 * The admin SPARQL passthrough surface: a SPARQL 1.1 Protocol query endpoint that forwards a SystemAdmin's query text
 * to the triplestore untouched and relays the store's response verbatim.
 *
 * Two shapes here differ from the rest of the admin API, both deliberately.
 *
 * The **success channel carries the store's outcome** as `(Content-Type, body, status)`. A store `400` for malformed
 * SPARQL, a `406`, a `200` -- from this endpoint's point of view all are successes, relayed byte for byte, so
 * dsp-api's JSON error envelope never fires for them. Declaring a `Content-Type` header output suppresses the body
 * codec's own media type, which is what lets the store's negotiated type through; the status output comes last,
 * matching the other endpoints that return a runtime status.
 *
 * The **error channel is extended per endpoint** with `errorOutVariantsPrepend`. Adding these variants to
 * `BaseEndpoints.errorOutputs` instead would be an API-wide information leak: a typed variant serializes the
 * exception's message verbatim, so every endpoint -- including unauthenticated public ones -- would start returning
 * these bodies. All six outcomes are enumerated here, because an outcome without a variant falls through to the
 * shared default variant and is reported as a generic `500`, which is precisely the bug this arrangement prevents.
 */
final class SparqlPassthroughEndpoints(baseEndpoints: BaseEndpoints) {

  val postAdminSparqlQuery = baseEndpoints.bearerSecuredEndpoint.post
    .in("admin" / "sparql" / "query")
    .in(SparqlPassthroughEndpoints.queryBody)
    .in(
      header[Option[String]](HeaderNames.Accept)
        .description("Forwarded to the store unchanged. If absent, the store's default serialization is returned."),
    )
    .in(
      queryParams.description(
        "The SPARQL protocol dataset parameters default-graph-uri and named-graph-uri are relayed to the store " +
          "unchanged; any other parameter is ignored. Sending the query itself as a query-string parameter is " +
          "rejected: it must be sent in the request body.",
      ),
    )
    .out(header[Option[String]](HeaderNames.ContentType))
    .out(SparqlPassthroughEndpoints.relayedBody)
    .out(statusCode)
    .errorOutVariantsPrepend(
      SparqlPassthroughEndpoints.storeUnavailableVariant,
      SparqlPassthroughEndpoints.requestTooLargeVariant,
      SparqlPassthroughEndpoints.responseTooLargeVariant,
      SparqlPassthroughEndpoints.upstreamRejectedVariant,
      SparqlPassthroughEndpoints.overloadedVariant,
      SparqlPassthroughEndpoints.timedOutVariant,
    )
    .description(
      "Run a read-only SPARQL 1.1 query against the triplestore and receive the store's response unchanged. " +
        "Requires SystemAdmin permissions and a bearer token; HTTP basic and session cookies are not accepted. " +
        "Only registered when the deployment sets allow-sparql-passthrough. The query is forwarded without being " +
        "parsed, validated or rewritten, and the store's status, Content-Type and body are relayed verbatim - so a " +
        "malformed query yields the store's own error response, not this API's error envelope. Send the query as " +
        "an application/sparql-query body or as the query field of an application/x-www-form-urlencoded body. " +
        "Guardrails apply: a store-side execution timeout, a request-body size cap, a response size ceiling, and a " +
        "backstop on concurrent calls.",
    )
}

object SparqlPassthroughEndpoints {

  val layer = ZLayer.derive[SparqlPassthroughEndpoints]

  /**
   * The route's path template, as the framework renders it.
   *
   * The server needs this to recognise a rejected passthrough request at its security-failure hook, which is the only
   * place a `401` on this route is observable -- the request never reaches the service. It is asserted against the
   * endpoint's own rendered template by test, so the two cannot drift apart silently.
   */
  val pathTemplate: String = "/admin/sparql/query"

  /** The media type of the SPARQL 1.1 Protocol direct query form; tapir has no built-in format for it. */
  private final case class SparqlQuery() extends CodecFormat {
    override val mediaType: MediaType = MediaType("application", "sparql-query")
  }

  /**
   * Declares the relayed body as `*&#47;*`, which is load-bearing rather than lazy.
   *
   * The framework negotiates the request's `Accept` header against the media types an endpoint's output declares, and
   * answers `406` itself when they do not intersect -- before the server logic runs at all. Declaring a concrete type
   * such as `application/octet-stream` would therefore make the framework reject `Accept: text/csv` and
   * `Accept: text/turtle` with an empty `406`, so a caller could never ask the store for any format but one, and the
   * store's own `406` (which REQ-1.2 requires be relayed) would be unreachable. A wildcard matches every `Accept`, so
   * negotiation is left entirely to the store, which is the whole point of a passthrough.
   *
   * It does not weaken the response: the actual `Content-Type` sent is the store's, relayed through the header output,
   * which suppresses this codec's own media type.
   */
  private val relayedBody: EndpointIO.Body[Array[Byte], Array[Byte]] =
    EndpointIO.Body(RawBodyType.ByteArrayBody, Codec.byteArray.format(AnyMediaType()), EndpointIO.Info.empty)

  private final case class AnyMediaType() extends CodecFormat {
    override val mediaType: MediaType = MediaType("*", "*")
  }

  /**
   * The two request forms REQ-1.4 requires, selected on the request's `Content-Type`.
   *
   * `oneOfBody` needs every variant to decode to the same type, so the form-encoded variant maps its decoded field
   * map down to the query string rather than staying a `Map`. An unmatched `Content-Type` is reported by the
   * framework as a `415` that does not pass through this endpoint's error output; a request with no `Content-Type`
   * at all is treated the same way, since neither variant can claim it.
   */
  private val queryBody: EndpointIO.OneOfBody[String, String] = oneOfBody(
    stringBodyAnyFormat(Codec.string.format(SparqlQuery()), StandardCharsets.UTF_8),
    stringBodyAnyFormat(formQueryCodec, StandardCharsets.UTF_8),
  )

  private def formQueryCodec: Codec[String, String, CodecFormat.XWwwFormUrlencoded] =
    Codec.formMapUtf8.mapDecode(fields =>
      fields.get("query").fold[DecodeResult[String]](DecodeResult.Missing)(DecodeResult.Value(_)),
    )(query => Map("query" -> query))

  private val storeUnavailableVariant = oneOfVariant(
    statusCode(StatusCode.ServiceUnavailable).and(jsonBody[SparqlStoreUnavailableException]),
  )

  private val requestTooLargeVariant = oneOfVariant(
    statusCode(StatusCode.PayloadTooLarge).and(jsonBody[SparqlRequestTooLargeException]),
  )

  /**
   * A too-large *response* is a `500`-class outcome with its own body, explicitly not a `413`: `413` describes the
   * request, and no standard status denotes a response that would have been too large.
   */
  private val responseTooLargeVariant = oneOfVariant(
    statusCode(StatusCode.InternalServerError).and(jsonBody[SparqlResponseTooLargeException]),
  )

  private val upstreamRejectedVariant = oneOfVariant(
    statusCode(StatusCode.BadGateway).and(jsonBody[SparqlUpstreamRejectedException]),
  )

  private val overloadedVariant = oneOfVariant(
    statusCode(StatusCode.ServiceUnavailable).and(jsonBody[SparqlPassthroughOverloadedException]),
  )

  private val timedOutVariant = oneOfVariant(
    statusCode(StatusCode.GatewayTimeout).and(jsonBody[SparqlPassthroughTimedOutException]),
  )
}
