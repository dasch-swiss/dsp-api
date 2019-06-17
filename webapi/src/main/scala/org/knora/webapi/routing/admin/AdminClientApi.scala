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

package org.knora.webapi.routing.admin

import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.clientapi._


/**
  * Represents the structure of generated client library code for the admin API.
  */
class AdminClientApi extends ClientApi {
    implicit private val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    override val endpoints: Set[ClientEndpoint] = Set(
        new UsersEndpoint()
    )

    override val name: String = "AdminApi"

    override val description: String = "A client API for administering Knora."

    override val modulePath: Seq[String] = Seq("admin")
}
