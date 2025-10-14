/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3

import zio.*

import org.knora.webapi.slice.security.Authenticator

object ApiV3Module {

  type Dependencies = Authenticator
  type Provided     = ApiV3ServerEndpoints

  val layer: URLayer[Dependencies, ApiV3ServerEndpoints] =
    V3BaseEndpoint.layer >>> ApiV3ServerEndpoints.layer
}

object ApiV3 {
  val basePath = "v3"
}
