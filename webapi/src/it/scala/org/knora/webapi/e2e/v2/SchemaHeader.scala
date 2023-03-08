/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import akka.http.scaladsl.model.headers.ModeledCustomHeader
import akka.http.scaladsl.model.headers.ModeledCustomHeaderCompanion

import scala.util.Try

import org.knora.webapi.routing.RouteUtilV2

/**
 * A custom Akka HTTP header representing [[RouteUtilV2.SCHEMA_HEADER]], which a client can send to specify
 * which ontology schema should be used in an API response.
 *
 * The definition follows [[https://doc.akka.io/docs/akka-http/current/common/http-model.html#custom-headers]].
 */
final class SchemaHeader(token: String) extends ModeledCustomHeader[SchemaHeader] {
  override def renderInRequests             = true
  override def renderInResponses            = true
  override val companion: SchemaHeader.type = SchemaHeader
  override def value: String                = token
}

object SchemaHeader extends ModeledCustomHeaderCompanion[SchemaHeader] {
  override val name: String         = RouteUtilV2.SCHEMA_HEADER
  override def parse(value: String) = Try(new SchemaHeader(value))
}
