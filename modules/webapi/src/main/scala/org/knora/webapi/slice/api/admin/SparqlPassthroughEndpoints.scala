/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.model.Header
import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.tapir.*
import sttp.tapir.EndpointOutput.OneOfVariant
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.server.model.EndpointExtensions.RichServerEndpoint
import sttp.tapir.typelevel.ErasureSameAsType
import zio.ZLayer
import zio.json.JsonDecoder
import zio.json.JsonEncoder

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.store.triplestore.errors.SparqlPassthroughException
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
 * Three shapes here differ from the rest of the admin API, all deliberately.
 *
 * Authorization is part of the **security logic** rather than the server logic, via
 * [[BaseEndpoints.bearerSystemAdminEndpoint]]: the framework decodes the request body between the two, so authorizing
 * in the server logic would let any holder of a valid token drive body decoding and everything after it before being
 * refused. Be precise about what that does and does not bound, because operators size deployments from it: the
 * zio-http server runs `RequestStreaming.Hybrid` and aggregates request bodies up to
 * `DspApiServer.maxAggregatedRequestBodySize` (1 MiB) *before any tapir logic runs at all*, for every caller
 * including an unauthenticated one. That buffering is server-wide, pre-existing, and unchanged by this endpoint.
 * What is SystemAdmin-only is decode and everything downstream of it.
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
 * these bodies. The variants are built from [[SparqlPassthroughEndpoints.errorVariants]], which pairs each with a
 * representative of the failure it covers so a test can hold the list against the sealed
 * [[SparqlPassthroughException]] hierarchy: an outcome without a variant falls through to the shared default variant
 * and is reported as a generic `500`, which is precisely the bug this arrangement prevents.
 */
final class SparqlPassthroughEndpoints(baseEndpoints: BaseEndpoints, appConfig: AppConfig) {

  val postAdminSparqlQuery = baseEndpoints
    .bearerSystemAdminEndpoint(SparqlPassthroughEndpoints.logRejected)
    .post
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
    // The relayed Content-Type is the store's and is not validated here, so tell the browser not to second-guess it.
    // Not reachable as an XSS pivot today -- the route is bearer-only, so nothing a browser navigates to can carry
    // credentials to it -- but it is one header on the one route in this API that returns a body it did not compose.
    .out(header(SparqlPassthroughEndpoints.noSniff))
    .out(SparqlPassthroughEndpoints.relayedBody)
    .out(statusCode)
    .errorOutVariantsPrepend(
      SparqlPassthroughEndpoints.errorVariants.head._2,
      SparqlPassthroughEndpoints.errorVariants.tail.map(_._2)*,
    )
    // The framework's own bound on the request body: it limits the body *stream*, so an over-cap request is refused
    // after at most this many bytes have been read instead of after the whole body has been materialised. Because
    // the check sits in body decoding it runs after the security logic above, so only an authorized SystemAdmin
    // gets this far.
    //
    // Its relation to the server's 1 MiB aggregation threshold is what an operator has to know, and it is not the
    // obvious one. Setting this cap *below* 1 MiB does not reduce actual peak buffering: zio-http has already read
    // and buffered up to 1 MiB before decoding starts, so the lower cap only changes the status the caller gets.
    // Setting it *above* 1 MiB does raise peak buffering, and with no concurrency bound on it: the surface's
    // concurrency backstop lives in the server logic, downstream of body decoding, so N simultaneous callers can
    // each be holding a body of this size before any of them is counted.
    .maxRequestBodyLength(appConfig.triplestore.sparqlPassthrough.maxRequestBodyBytes.toLong)
    // Lets the server's interceptors recognise this route -- to exempt it from tapir's Accept negotiation, and to
    // attribute a request its security or decode logic rejected -- without matching on its path or method.
    .attribute(SparqlPassthroughEndpoints.routeMarker, SparqlPassthroughRoute)
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

/** The value of [[SparqlPassthroughEndpoints.routeMarker]]; a dedicated type so the attribute key cannot collide. */
case object SparqlPassthroughRoute

object SparqlPassthroughEndpoints {

  val layer = ZLayer.derive[SparqlPassthroughEndpoints]

  /**
   * Marks the passthrough route, so a server-side interceptor can recognise it from the endpoint it was handed.
   *
   * The alternative -- comparing the rendered path template -- was method-blind (a future `GET` on the same path would
   * silently inherit the exemptions) and broke on a rename unless a separate constant was kept in step. This is
   * neither, and it costs a map lookup rather than rebuilding the template on every request.
   */
  val routeMarker: AttributeKey[SparqlPassthroughRoute.type] = AttributeKey[SparqlPassthroughRoute.type]

  /** True for the one endpoint carrying [[routeMarker]]. */
  def isPassthroughRoute(ep: AnyEndpoint): Boolean = ep.attribute(routeMarker).isDefined

  /**
   * Attributes a request the security logic turned away. Neither rejection reaches the rest service that does the
   * per-call logging -- security logic runs before server logic -- so this is the only place they are observable, and
   * the only place a `403` still has the identity that makes it an attribution.
   */
  private def logRejected(user: Option[User]) =
    SparqlPassthroughAudit(if (user.isDefined) "forbidden" else "unauthenticated", user).log

  /** `sttp.model.HeaderNames` has no constant for this one. */
  private[admin] val noSniff: Header = Header("X-Content-Type-Options", "nosniff")

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

  /**
   * Every failure this endpoint declares an error variant for, each paired with a representative instance.
   *
   * The representative serves two purposes: its `statusCode` is the one the variant maps to, so the status lives with
   * the failure rather than being restated here, and it lets a test check this list against the sealed
   * [[SparqlPassthroughException]] hierarchy -- which is what would catch a seventh subtype being added without a
   * variant, the case that otherwise falls through to a generic `500`.
   *
   * A too-large *response* deliberately maps to `500` rather than `413`: `413` describes the request, and no standard
   * status denotes a response that would have been too large. A store credential rejection maps to `502` with a
   * scrubbed body rather than relaying the store's own `401`, which would present a server misconfiguration as the
   * caller's authentication failure.
   */
  val errorVariants: List[(SparqlPassthroughException, OneOfVariant[? <: Throwable])] = List(
    variant(SparqlStoreUnavailableException.make),
    variant(SparqlRequestTooLargeException.make(0)),
    variant(SparqlResponseTooLargeException.make(0)),
    variant(SparqlUpstreamRejectedException.make),
    variant(SparqlPassthroughOverloadedException.make(0)),
    variant(SparqlPassthroughTimedOutException.make(0)),
  )

  private def variant[E <: SparqlPassthroughException: JsonEncoder: JsonDecoder: Schema: ClassTag: ErasureSameAsType](
    e: E,
  ): (SparqlPassthroughException, OneOfVariant[? <: Throwable]) =
    e -> oneOfVariant(statusCode(e.statusCode).and(jsonBody[E]))
}
