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

import org.knora.webapi.OntologyConstants
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.clientapi._


/**
  * Represents the structure of generated client library code for the admin API.
  */
class AdminClientApi extends ClientApi {
    implicit private val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val userClassRef = ClientClassReference(
        className = "User",
        classIri = OntologyConstants.KnoraAdminV2.UserClass.toSmartIri
    )

    private val usersResponse = ClientClassDefinition(
        className = "UsersResponse",
        classIri = OntologyConstants.KnoraAdminV2.UsersResponse.toSmartIri,
        properties = Vector(
            ClientPropertyDefinition(
                propertyName = "users",
                propertyIri = OntologyConstants.KnoraAdminV2.UsersProperty.toSmartIri,
                objectType = userClassRef,
                cardinality = Cardinality.MayHaveMany,
                isEditable = false
            )
        )
    )

    private val userResponse = ClientClassDefinition(
        className = "UserResponse",
        classIri = OntologyConstants.KnoraAdminV2.UserResponse.toSmartIri,
        properties = Vector(
            ClientPropertyDefinition(
                propertyName = "user",
                propertyIri = OntologyConstants.KnoraAdminV2.UserProperty.toSmartIri,
                objectType = userClassRef,
                cardinality = Cardinality.MustHaveOne,
                isEditable = false
            )
        )
    )

    override val classDefs: Set[ClientClassDefinition] = Set(
        usersResponse,
        userResponse
    )

    override val endpoints: Set[ClientEndpoint] = Set(
        new UsersEndpoint()
    )
}
