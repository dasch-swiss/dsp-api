/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import dsp.errors.InternalServerException

/**
 * Raised when a fulltext search (`/v2/search`, `/v2/search/count`) exceeds its triplestore timeout. A store-layer
 * [[dsp.errors.TriplestoreTimeoutException]] reaches the client as a bare HTTP 500 via the shared catch-all; the
 * fulltext path translates it into this search-specific type so the two search endpoints — and only those — can map
 * it to a 503 with a legible, hedged message (DEV-6864). A timeout is not proof the term is too broad (a slow
 * triplestore, GC pause or contention produces the same symptom), so the message must hedge rather than blame.
 *
 * It carries exactly one `message: String` field and no `cause`: `DeriveJsonCodec` derives neither the zio-json
 * codec nor the tapir `Schema` for an `Option[Throwable]`, so the message-only shape is what makes `jsonBody`
 * derivation work (Spike B).
 */
final case class SearchTimeoutException(message: String) extends InternalServerException(message)

object SearchTimeoutException {

  /** The hedged, user-facing message. Does not assert the term is too broad — see the class doc. */
  val defaultMessage: String =
    "This search could not be completed in time; it may be too broad. Try adding another word."

  def apply(): SearchTimeoutException = SearchTimeoutException(defaultMessage)

  implicit val codec: JsonCodec[SearchTimeoutException] = DeriveJsonCodec.gen[SearchTimeoutException]
}
