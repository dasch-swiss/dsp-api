/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion}
import org.knora.webapi.routing.RouteUtilV2

import scala.util.Try

/**
 * A custom Akka HTTP header representing [[RouteUtilV2.PROJECT_HEADER]], which a client can send to specify
 * a project from which results should be returned.
 *
 * The definition follows [[https://doc.akka.io/docs/akka-http/current/common/http-model.html#custom-headers]].
 */
class ProjectHeader(token: String) extends ModeledCustomHeader[ProjectHeader] {
  override def renderInRequests              = true
  override def renderInResponses             = true
  override val companion: ProjectHeader.type = ProjectHeader
  override def value: String                 = token
}

object ProjectHeader extends ModeledCustomHeaderCompanion[ProjectHeader] {
  override val name: String         = RouteUtilV2.PROJECT_HEADER
  override def parse(value: String) = Try(new ProjectHeader(value))
}
