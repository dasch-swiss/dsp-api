/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import io.opentelemetry.api
import zio.*
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing

object OtelSetup {
  val layer: ULayer[api.OpenTelemetry & Tracing & ContextStorage] =
    (OpenTelemetry.global <*> OpenTelemetry.contextZIO >+> OpenTelemetry.tracing("global")).orDie
}
