/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.model.Method

/**
 * An endpoint base that exposes nothing but the choice of HTTP method.
 *
 * A tapir endpoint without a method matches every method, so the bases hand out this wrapper instead of the endpoint
 * itself: an endpoint cannot be built without first choosing its method.
 */
final class RequiresMethod[E](base: E, withMethod: (E, Method) => E) {
  def get: E    = withMethod(base, Method.GET)
  def post: E   = withMethod(base, Method.POST)
  def put: E    = withMethod(base, Method.PUT)
  def delete: E = withMethod(base, Method.DELETE)
  def patch: E  = withMethod(base, Method.PATCH)
}
