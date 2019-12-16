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
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.clientapi._
import org.knora.webapi.util.{SmartIri, StringFormatter}


/**
 * Represents the structure of generated client library code for the admin API.
 */
class AdminClientApi(routeData: KnoraRouteData) extends ClientApi {
    implicit private val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
     * The serialisation format used by this [[ClientApi]].
     */
    override val serialisationFormat: ApiSerialisationFormat = Json

    /**
     * The endpoints in this [[ClientApi]].
     */
    override val endpoints: Seq[ClientEndpoint] = Seq(
        new UsersRouteADM(routeData),
        new GroupsRouteADM(routeData),
        new ProjectsRouteADM(routeData),
        new PermissionsRouteADM(routeData),
        new ListsRouteADM(routeData)
    )

    /**
     * The name of this [[ClientApi]].
     */
    override val name: String = "AdminEndpoint"

    /**
     * The directory name to be used for this API's code.
     */
    override val directoryName: String = "admin"

    /**
     * The URL path of this [[ClientApi]].
     */
    override val urlPath: String = "/admin"

    /**
     * A description of this [[ClientApi]].
     */
    override val description: String = "A client API for administering Knora."

    /**
     * A map of class IRIs to their read-only properties.
     */
    override val classesWithReadOnlyProperties: Map[SmartIri, Set[SmartIri]] = Map(
        OntologyConstants.KnoraAdminV2.UserClass -> Set(
            OntologyConstants.KnoraAdminV2.Token,
            OntologyConstants.KnoraAdminV2.SessionID,
            OntologyConstants.KnoraAdminV2.Groups,
            OntologyConstants.KnoraAdminV2.Projects,
            OntologyConstants.KnoraAdminV2.Permissions
        ),
        OntologyConstants.KnoraAdminV2.GroupClass -> Set(
            OntologyConstants.KnoraAdminV2.ProjectProperty
        ),
        OntologyConstants.KnoraAdminV2.ProjectClass -> Set(
            OntologyConstants.KnoraAdminV2.Ontologies
        )
    ).map {
        case (classIri, propertyIris) =>
            classIri.toSmartIri -> propertyIris.map(_.toSmartIri)
    }

    /**
     * A map of class IRIs to IRIs of optional set properties. Such properties have cardinality 0-n, and should
     * be made optional in generated code.
     */
    override val classesWithOptionalSetProperties: Map[SmartIri, Set[SmartIri]] = Map(
        OntologyConstants.KnoraAdminV2.UpdateProjectRequest -> Set(
            OntologyConstants.KnoraAdminV2.KeywordsProperty,
            OntologyConstants.KnoraAdminV2.ProjectDescription
        )
    ).map {
        case (classIri, propertyIris) =>
            classIri.toSmartIri -> propertyIris.map(_.toSmartIri)
    }

    /**
     * A set of IRIs of classes that represent API requests and that therefore do not need `Stored*`
     * subclasses.
     */
    override val requestClasses: Set[SmartIri] = Set(
        OntologyConstants.KnoraAdminV2.UpdateUserRequest,
        OntologyConstants.KnoraAdminV2.CreateGroupRequest,
        OntologyConstants.KnoraAdminV2.UpdateProjectRequest,
        OntologyConstants.KnoraAdminV2.UpdateGroupRequest,
        OntologyConstants.KnoraAdminV2.CreateChildNodeRequest,
        OntologyConstants.KnoraAdminV2.CreateListRequest,
        OntologyConstants.KnoraAdminV2.UpdateListInfoRequest
    ).map(_.toSmartIri)

    /**
     * A set of IRIs of classes that are always read-only and that therefore do not need `Stored*`
     * or `Read*` subclasses.
     */
    override val readOnlyClasses: Set[SmartIri] = Set(
        OntologyConstants.KnoraAdminV2.ListClass,
        OntologyConstants.KnoraAdminV2.ListNodeInfo,
        OntologyConstants.KnoraAdminV2.ListNode
    ).map(_.toSmartIri)

    /**
     * A set of IRIs of classes that represent API responses.
     */
    override val responseClasses: Set[SmartIri] = Set(
        OntologyConstants.KnoraAdminV2.UserResponse,
        OntologyConstants.KnoraAdminV2.UsersResponse,
        OntologyConstants.KnoraAdminV2.GroupResponse,
        OntologyConstants.KnoraAdminV2.GroupsResponse,
        OntologyConstants.KnoraAdminV2.ProjectResponse,
        OntologyConstants.KnoraAdminV2.ProjectsResponse,
        OntologyConstants.KnoraAdminV2.MembersResponse,
        OntologyConstants.KnoraAdminV2.KeywordsResponse,
        OntologyConstants.KnoraAdminV2.AdministrativePermissionResponse,
        OntologyConstants.KnoraAdminV2.AdministrativePermissionsResponse,
        OntologyConstants.KnoraAdminV2.ProjectRestrictedViewSettingsResponse,
        OntologyConstants.KnoraAdminV2.ListsResponse,
        OntologyConstants.KnoraAdminV2.ListResponse,
        OntologyConstants.KnoraAdminV2.ListInfoResponse,
        OntologyConstants.KnoraAdminV2.ListNodeInfoResponse
    ).map(_.toSmartIri)

    /**
     * A set of property IRIs that are used for the unique IDs of objects.
     */
    override val idProperties: Set[SmartIri] = Set(
        OntologyConstants.KnoraAdminV2.ID,
        OntologyConstants.KnoraAdminV2.Iri,
        OntologyConstants.KnoraAdminV2.ListIri
    ).map(_.toSmartIri)

    /**
     * A map of class IRIs to maps of property IRIs to non-standard names that those properties must have
     * in those classes. Needed only for JSON, and only if two different properties should have the same name in
     * different classes. `JsonInstanceInspector` also needs to know about these.
     */
    override lazy val propertyNames: Map[SmartIri, Map[SmartIri, String]] = AdminClientApi.propertyNames
}

object AdminClientApi {
    def propertyNames(implicit stringFormatter: StringFormatter): Map[SmartIri, Map[SmartIri, String]] = Map(
        OntologyConstants.KnoraAdminV2.CreateGroupRequest -> Map(
            OntologyConstants.KnoraAdminV2.ProjectWithIriObj -> "project",
            OntologyConstants.KnoraAdminV2.GroupDescription -> "description"
        ),
        OntologyConstants.KnoraAdminV2.UpdateGroupRequest -> Map(OntologyConstants.KnoraAdminV2.GroupDescription -> "description"),
        OntologyConstants.KnoraAdminV2.ProjectClass -> Map(OntologyConstants.KnoraAdminV2.ProjectDescription -> "description"),
        OntologyConstants.KnoraAdminV2.UpdateProjectRequest -> Map(OntologyConstants.KnoraAdminV2.ProjectDescription -> "description"),
        OntologyConstants.KnoraAdminV2.GroupClass -> Map(OntologyConstants.KnoraAdminV2.GroupDescription -> "description")
    ).map {
        case (classIri, propertyMap) =>
            classIri.toSmartIri -> propertyMap.map {
                case (propertyIri, propertyName) =>
                    propertyIri.toSmartIri -> propertyName
            }
    }
}