/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import zio.ZLayer

object InfrastructureModule {

  type Dependencies =
    // format: off
    Any
    // format: on

  type Provided =
    // format: off
    CacheManager
    // format: on

  val layer = ZLayer.makeSome[Dependencies, Provided](
    CacheManager.layer,
  )
}
