/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.apache.pekko.http.scaladsl.model.headers.ModeledCustomHeader
import org.apache.pekko.http.scaladsl.model.headers.ModeledCustomHeaderCompanion

import scala.util.Try

import org.knora.webapi.slice.search.search.api.ApiV2.Headers.xKnoraAcceptSchemaHeader

/**
 * A custom Pekko HTTP header "x-knora-accept-schema", which a client can send to specify
 * a project from which results should be returned.
 *
 * The definition follows [[https://doc.pekko.io/docs/pekko-http/current/common/http-model.html#custom-headers]].
 */
class ProjectHeader(token: String) extends ModeledCustomHeader[ProjectHeader] {
  override def renderInRequests              = true
  override def renderInResponses             = true
  override val companion: ProjectHeader.type = ProjectHeader
  override def value: String                 = token
}

object ProjectHeader extends ModeledCustomHeaderCompanion[ProjectHeader] {
  override val name: String                             = xKnoraAcceptSchemaHeader
  override def parse(value: String): Try[ProjectHeader] = Try(new ProjectHeader(value))
}
