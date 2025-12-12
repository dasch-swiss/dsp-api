/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common
import zio.URLayer
import zio.ZLayer
import zio.telemetry.opentelemetry.tracing.Tracing

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.Triplestore
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive

object CommonModule { self =>

  type Dependencies = AppConfig & Tracing & Triplestore

  type Provided = IriConverter & StringFormatter & TriplestoreService

  val layer: URLayer[self.Dependencies, self.Provided] =
    StringFormatter.live >+> IriConverter.layer >+> TriplestoreServiceLive.layer
}
