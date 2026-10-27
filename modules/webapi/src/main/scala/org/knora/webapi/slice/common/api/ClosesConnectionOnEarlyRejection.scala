/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.tapir.*

/** The value of [[ClosesConnectionOnEarlyRejection.key]]; a dedicated type so the attribute key cannot collide. */
case object EarlyRejectionClosesConnection

/**
 * Marks an endpoint that may reject a call before reading a streamed request body (its security logic, or a decode
 * failure), so the server answers such a rejection with `Connection: close`. See `DspApiServer`.
 */
object ClosesConnectionOnEarlyRejection {

  val key: AttributeKey[EarlyRejectionClosesConnection.type] = AttributeKey[EarlyRejectionClosesConnection.type]

  /** True for an endpoint carrying [[key]]. */
  def isSet(ep: AnyEndpoint): Boolean = ep.attribute(key).isDefined
}
