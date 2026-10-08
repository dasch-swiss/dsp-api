/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import dsp.errors.InternalServerException

/**
 * Raised when a search exceeds its triplestore timeout: fulltext search (`/v2/search`, `/v2/search/count`) and
 * Gravsearch (`/v2/searchextended`, page and count). A store-layer
 * [[org.knora.webapi.store.triplestore.errors.TriplestoreTimeoutException]] reaches the client as a bare HTTP 500 via
 * the shared catch-all; those endpoints translate it into this search-specific type, which only they map to a 503
 * with a legible, hedged message. A timeout is not proof the query is too broad (a slow triplestore, GC pause or
 * contention produces the same symptom), so the message must hedge rather than blame.
 *
 * It carries exactly one `message: String` field and no `cause`: `DeriveJsonCodec` derives neither the zio-json
 * codec nor the tapir `Schema` for an `Option[Throwable]`, so the message-only shape is what makes `jsonBody`
 * derivation work.
 */
final case class SearchTimeoutException(message: String) extends InternalServerException(message)

object SearchTimeoutException {

  /** The hedged message for fulltext search. Does not assert the term is too broad -- see the class doc. */
  val defaultMessage: String =
    "This search could not be completed in time; it may be too broad. Try adding another word."

  /** The hedged message for Gravsearch, whose query may be narrowed by a term or by an additional restriction. */
  val gravsearchMessage: String =
    "This search could not be completed in time; it may be too broad. " +
      "Try narrowing it, for example with a more specific search term or an additional restriction."

  def apply(): SearchTimeoutException = SearchTimeoutException(defaultMessage)

  implicit val codec: JsonCodec[SearchTimeoutException] = DeriveJsonCodec.gen[SearchTimeoutException]
}
