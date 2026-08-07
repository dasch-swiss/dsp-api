/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.errors

import sttp.model.StatusCode
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

/**
 * The failures the admin SPARQL passthrough can produce on its own behalf.
 *
 * These are deliberately separate from [[TriplestoreException]] and its subtypes, and deliberately **storeless**:
 * every `message` here is a fixed string that a client is allowed to read, and it is also what the API returns
 * verbatim in the response body. Reusing [[TriplestoreConnectionException]] would embed the internal store URI in
 * the message and therefore hand it to the caller, so it must not be reused on this path.
 *
 * The same rule extends to the exception chain: no `cause` is attached, because an sttp transport exception's own
 * message carries the target URI, and the OpenTelemetry Java agent records `exception.message` /
 * `exception.stacktrace` for exceptions crossing instrumented boundaries. The underlying error and the store URI
 * belong in the server-side log line, which the implementation writes separately.
 *
 * Note that a SPARQL error *from* the store is not one of these: a malformed query, a `406`, or the store's own
 * timeout response all travel back as ordinary success values so they can be relayed verbatim.
 *
 * `outcome` is the stable token recorded as the `outcome` field of the per-call log entry. `statusCode` is the status
 * the API answers with, and it lives here rather than in the endpoint so that the two cannot be declared apart: the
 * endpoint builds its error variants from these values, and a test holds that variant list against this hierarchy.
 */
sealed abstract class SparqlPassthroughException(message: String) extends Exception(message) {
  def outcome: String
  def statusCode: StatusCode
}

/** The triplestore could not be reached, or the connection failed before any response arrived. Maps to `503`. */
final case class SparqlStoreUnavailableException(message: String) extends SparqlPassthroughException(message) {
  override val outcome: String        = "store-unavailable"
  override val statusCode: StatusCode = StatusCode.ServiceUnavailable
}
object SparqlStoreUnavailableException {
  val make: SparqlStoreUnavailableException = SparqlStoreUnavailableException("The triplestore is unavailable.")

  implicit val codec: JsonCodec[SparqlStoreUnavailableException] =
    DeriveJsonCodec.gen[SparqlStoreUnavailableException]
}

/** The submitted SPARQL text exceeded the configured request-body ceiling. Maps to `413`. */
final case class SparqlRequestTooLargeException(message: String) extends SparqlPassthroughException(message) {
  override val outcome: String        = "request-cap-exceeded"
  override val statusCode: StatusCode = StatusCode.PayloadTooLarge
}
object SparqlRequestTooLargeException {
  def make(maxBytes: Int): SparqlRequestTooLargeException =
    SparqlRequestTooLargeException(s"The SPARQL request body exceeds the configured limit of $maxBytes bytes.")

  implicit val codec: JsonCodec[SparqlRequestTooLargeException] =
    DeriveJsonCodec.gen[SparqlRequestTooLargeException]
}

/**
 * The store's response exceeded the configured response ceiling. Maps to a `500`-class status and explicitly **not**
 * to `413`, which describes a too-large request: no standard status denotes a too-large response.
 */
final case class SparqlResponseTooLargeException(message: String) extends SparqlPassthroughException(message) {
  override val outcome: String        = "response-cap-exceeded"
  override val statusCode: StatusCode = StatusCode.InternalServerError
}
object SparqlResponseTooLargeException {
  def make(maxBytes: Int): SparqlResponseTooLargeException =
    SparqlResponseTooLargeException(
      s"The response from the triplestore exceeds the configured limit of $maxBytes bytes. " +
        "Narrow the query, or page it with LIMIT and OFFSET.",
    )

  implicit val codec: JsonCodec[SparqlResponseTooLargeException] =
    DeriveJsonCodec.gen[SparqlResponseTooLargeException]
}

/**
 * The store rejected the API's own credentials (`401`, `403` or `407`). Maps to a `502`-class status with this
 * scrubbed message rather than relaying the store's response, because
 * relaying it would present a server misconfiguration -- typically a rotated database password -- as the caller's
 * own authentication failure and invite credential-confusion retries.
 */
final case class SparqlUpstreamRejectedException(message: String) extends SparqlPassthroughException(message) {
  override val outcome: String        = "upstream-rejected"
  override val statusCode: StatusCode = StatusCode.BadGateway
}
object SparqlUpstreamRejectedException {
  val make: SparqlUpstreamRejectedException = SparqlUpstreamRejectedException(
    "The API could not authenticate with the triplestore. This is a server-side configuration problem, " +
      "not a problem with the credentials you supplied.",
  )

  implicit val codec: JsonCodec[SparqlUpstreamRejectedException] =
    DeriveJsonCodec.gen[SparqlUpstreamRejectedException]
}

/**
 * The surface-wide backstop on calls in flight was saturated. Maps to `503`, and the request is rejected rather than
 * queued: queueing would keep heap bounded but let fibers and connections accumulate instead.
 */
final case class SparqlPassthroughOverloadedException(message: String) extends SparqlPassthroughException(message) {
  override val outcome: String        = "overloaded"
  override val statusCode: StatusCode = StatusCode.ServiceUnavailable
}
object SparqlPassthroughOverloadedException {
  def make(maxConcurrentCalls: Int): SparqlPassthroughOverloadedException =
    SparqlPassthroughOverloadedException(
      s"Too many SPARQL passthrough requests in flight; at most $maxConcurrentCalls run concurrently. Retry later.",
    )

  implicit val codec: JsonCodec[SparqlPassthroughOverloadedException] =
    DeriveJsonCodec.gen[SparqlPassthroughOverloadedException]
}

/**
 * The call exceeded the API-side deadline. Maps to `504`.
 *
 * This is distinct from the store cancelling an over-time query at its own engine: that produces the store's own
 * timeout response, which is relayed verbatim. This failure is the outer bound that releases the fiber, the
 * connection and the response buffer when the store neither answers nor fails -- for instance when it drips bytes
 * slowly enough that no single read times out.
 */
final case class SparqlPassthroughTimedOutException(message: String) extends SparqlPassthroughException(message) {
  override val outcome: String        = "timed-out"
  override val statusCode: StatusCode = StatusCode.GatewayTimeout
}
object SparqlPassthroughTimedOutException {
  def make(seconds: Long): SparqlPassthroughTimedOutException =
    SparqlPassthroughTimedOutException(
      s"The SPARQL passthrough request exceeded the configured time limit of $seconds seconds.",
    )

  implicit val codec: JsonCodec[SparqlPassthroughTimedOutException] =
    DeriveJsonCodec.gen[SparqlPassthroughTimedOutException]
}
