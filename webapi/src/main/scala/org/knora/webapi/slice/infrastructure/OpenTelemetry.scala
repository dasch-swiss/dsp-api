/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import io.opentelemetry.api
import zio.*
import zio.telemetry.opentelemetry.OpenTelemetry as ZOpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing

object OpenTelemetry {
  private val instrumentationScope = "global"

  val layer: ULayer[api.OpenTelemetry & Tracing] =
    ZOpenTelemetry.global.orDie >+> ZOpenTelemetry.contextJVM >+> ZOpenTelemetry.tracing(instrumentationScope)
}
