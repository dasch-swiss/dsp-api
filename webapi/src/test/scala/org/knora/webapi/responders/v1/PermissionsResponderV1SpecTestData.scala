/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.responders.v1

import dispatch.url
import org.knora.webapi.OntologyConstants
import org.knora.webapi.messages.v1.responder.permissionmessages.{AdministrativePermissionV1, DefaultObjectAccessPermissionV1}

/* Helper case classes */
case class ap (iri: String, p: AdministrativePermissionV1)
case class doap (iri: String, p: DefaultObjectAccessPermissionV1)

/**
  * This object holds data representations for the data in '_test_data/all_data/permissions-data.ttl'.
  */
object PermissionsResponderV1SpecTestData {

    val IMAGES_PROJECT_IRI = "http://data.knora.org/projects/images"

    val perm001 =
        ap(
            iri = "http://data.knora.org/permissions/001",
            p = AdministrativePermissionV1(
                forProject = IMAGES_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectMember,
                resourceCreationPermissionValues = List(OntologyConstants.KnoraBase.ProjectResourceCreateAllPermission)
            )
        )

    val perm002 =
        doap(
            iri = "http://data.knora.org/permissions/002",
            p = DefaultObjectAccessPermissionV1(
                forProject = IMAGES_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectMember,
                forResourceClass = OntologyConstants.KnoraBase.AllResourceClasses,
                forProperty = OntologyConstants.KnoraBase.AllProperties,
                hasDefaultChangeRightsPermission = List(OntologyConstants.KnoraBase.Creator),
                hasDefaultModifyPermission = List(OntologyConstants.KnoraBase.ProjectMember),
                hasDefaultViewPermission = List(OntologyConstants.KnoraBase.KnownUser)
            )
        )


    val perm003 =
        ap(
            iri = "http://data.knora.org/permissions/003",
            p = AdministrativePermissionV1()
        )
    val perm004 =
        ap(
            iri = "http://data.knora.org/permissions/004",
            p = AdministrativePermissionV1()
        )
    val perm005 =
        ap (
            iri = "http://data.knora.org/permissions/005",
            p = AdministrativePermissionV1()
        )

}
