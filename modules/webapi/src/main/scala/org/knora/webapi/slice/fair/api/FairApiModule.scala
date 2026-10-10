/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.api

import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing

import org.knora.webapi.config.FairConfig
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.fair.domain.service.AppShellLive
import org.knora.webapi.slice.fair.domain.service.ResourceFactsBuilder
import org.knora.webapi.slice.fair.ports.FairAssets
import org.knora.webapi.slice.fair.ports.FairClasses
import org.knora.webapi.slice.fair.ports.FairProjects
import org.knora.webapi.slice.fair.ports.FairResources

/** The four port adapters are provided from outside, in `LayersLive`. */
object FairApiModule {
  type Dependencies =
    // format: off
    FairAssets &
    FairClasses &
    FairConfig &
    FairProjects &
    FairResources &
    KnoraApi &
    StringFormatter &
    Tracing
    // format: on

  type Provided = FairServerEndpoints

  val layer: URLayer[Dependencies, Provided] =
    ZLayer.makeSome[Dependencies, Provided](
      AppShellLive.layer,
      FairEndpoints.layer,
      FairRestService.layer,
      FairServerEndpoints.layer,
      ResourceFactsBuilder.layer,
    )
}
