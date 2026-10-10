/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.tapir.*

/** The value of [[NegotiatesOwnAccept.key]]; a dedicated type so the attribute key cannot collide. */
case object OwnAcceptNegotiation

/**
 * Marks an endpoint that decides for itself what to answer for any `Accept` header, so the server's `406` check
 * against the endpoint's declared output media types is skipped for it.
 */
object NegotiatesOwnAccept {

  val key: AttributeKey[OwnAcceptNegotiation.type] = AttributeKey[OwnAcceptNegotiation.type]

  /** True for an endpoint carrying [[key]]. */
  def isSet(ep: AnyEndpoint): Boolean = ep.attribute(key).isDefined
}
