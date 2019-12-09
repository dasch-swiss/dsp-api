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
import org.knora.webapi.util.clientapi.{ApiSerialisationFormat, ClientApi, ClientEndpoint, JsonLD}
import org.knora.webapi.util.{SmartIri, StringFormatter}

class V2ClientApi (routeData: KnoraRouteData) extends ClientApi {
    implicit private val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
      * The serialisation format used by this [[ClientApi]].
      */
    override val serialisationFormat: ApiSerialisationFormat = JsonLD

    /**
      * Don't generate endpoints for this API.
      */
    override val generateEndpoints: Boolean = false

    /**
      * Don't generate classes for this API.
      */
    override val generateClasses: Boolean = false

    /**
      * The endpoints in this [[ClientApi]].
      */
    override val endpoints: Seq[ClientEndpoint] = Seq(
        new OntologiesRouteV2(routeData),
        new ResourcesRouteV2(routeData),
        new ValuesRouteV2(routeData),
        new SearchRouteV2(routeData)
    )

    /**
      * The name of this [[ClientApi]].
      */
    override val name: String = "V2Endpoint"

    /**
      * The directory name to be used for this API's code.
      */
    override val directoryName: String = "v2"

    /**
      * The URL path of this [[ClientApi]].
      */
    override val urlPath: String = "/v2"

    /**
      * A description of this [[ClientApi]].
      */
    override val description: String = "The Knora API v2."

    /**
      * A map of class IRIs to their read-only properties.
      */
    override val classesWithReadOnlyProperties: Map[SmartIri, Set[SmartIri]] = Map.empty

    /**
      * A set of IRIs of classes that represent API responses.
      */
    override val responseClasses: Set[SmartIri] = Set.empty

    /**
      * A set of property IRIs that are used for the unique IDs of objects.
      */
    override val idProperties: Set[SmartIri] = Set.empty

    /**
      * A map of class IRIs to maps of property IRIs to non-standard names that those properties must have
      * in those classes. Needed only for JSON, and only if two different properties should have the same name in
      * different classes. `JsonInstanceInspector` also needs to know about these.
      */
    override val propertyNames: Map[SmartIri, Map[SmartIri, String]] = Map.empty
    /**
      * A map of class IRIs to IRIs of optional set properties. Such properties have cardinality 0-n, and should
      * be made optional in generated code.
      */
    override val classesWithOptionalSetProperties: Map[SmartIri, Set[SmartIri]] = Map.empty
}
