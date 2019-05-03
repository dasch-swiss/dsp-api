/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
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
    override def renderInRequests = true
    override def renderInResponses = true
    override val companion: ProjectHeader.type = ProjectHeader
    override def value: String = token
}

object ProjectHeader extends ModeledCustomHeaderCompanion[ProjectHeader] {
    override val name: String = RouteUtilV2.PROJECT_HEADER
    override def parse(value: String) = Try(new ProjectHeader(value))
}
