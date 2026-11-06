/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import sttp.tapir.AnyEndpoint
import sttp.tapir.AttributeKey

/** The value of [[ClosesOnUnreadBody.key]]; a dedicated type so the attribute key cannot collide. */
case object ClosesOnUnreadBodyRoute

/**
 * Marks an endpoint that streams a large request body, so the server can end the connection when it answers without
 * having read that body. Without it the next keep-alive request on the connection is silently dropped.
 *
 * The SPARQL passthrough carries its own marker, [[org.knora.webapi.slice.api.admin.SparqlPassthroughEndpoints.routeMarker]],
 * for the same hazard (an unread streamed body under `autoRead=false`). A route whose server logic can reject a
 * streamed body without reading it must carry one of the two; `StreamingBodyMarkerE2ESpec` enforces this.
 */
object ClosesOnUnreadBody {

  val key: AttributeKey[ClosesOnUnreadBodyRoute.type] = AttributeKey[ClosesOnUnreadBodyRoute.type]

  /** True for an endpoint carrying [[key]]. */
  def isMarked(ep: AnyEndpoint): Boolean = ep.attribute(key).isDefined
}
