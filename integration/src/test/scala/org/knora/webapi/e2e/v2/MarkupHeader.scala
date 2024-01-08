/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.apache.pekko

import scala.util.Try

import pekko.http.scaladsl.model.headers.ModeledCustomHeader
import pekko.http.scaladsl.model.headers.ModeledCustomHeaderCompanion

/**
 * A custom Pekko HTTP header representing "x-knora-accept-markup", which a client can send to specify
 * how text markup should be returned in an API response.
 *
 * The definition follows [[https://doc.pekko.io/docs/pekko-http/current/common/http-model.html#custom-headers]].
 */
final class MarkupHeader private (token: String) extends ModeledCustomHeader[MarkupHeader] {
  override def renderInRequests             = true
  override def renderInResponses            = true
  override val companion: MarkupHeader.type = MarkupHeader
  override def value: String                = token
}

object MarkupHeader extends ModeledCustomHeaderCompanion[MarkupHeader] {
  override val name: String         = "x-knora-accept-markup"
  override def parse(value: String) = Try(new MarkupHeader(value))

  val standoff: MarkupHeader = new MarkupHeader("standoff")
}
