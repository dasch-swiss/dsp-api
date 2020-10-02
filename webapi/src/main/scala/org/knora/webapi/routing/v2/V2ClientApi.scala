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

package org.knora.webapi.routing.v2

import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.util.{ClientApi, ClientEndpoint}

/**
 * Represents the structure of client test data generated for Knora API v2.
 */
class V2ClientApi(routeData: KnoraRouteData) extends ClientApi {
    /**
     * The endpoints in this [[ClientApi]].
     */
    override val endpoints: Seq[ClientEndpoint] = Seq(
        new OntologiesRouteV2(routeData),
        new ValuesRouteV2(routeData),
        new SearchRouteV2(routeData),
        new ListsRouteV2(routeData)
    )

    /**
     * The directory name to be used for the generated test data.
     */
    override val directoryName: String = "v2"
}
