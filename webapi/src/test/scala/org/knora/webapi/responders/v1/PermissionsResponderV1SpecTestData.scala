/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v1

import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.permissionmessages._

/* Helper case classes */
case class ap (iri: String, p: AdministrativePermissionV1)
case class doap (iri: String, p: DefaultObjectAccessPermissionV1)

/**
  * This object holds data representations for the data in '_test_data/all_data/permissions-data.ttl'.
  */
object PermissionsResponderV1SpecTestData {


    /*************************************/
    /** Images Demo Project Permissions **/
    /*************************************/


    val IMAGES_PROJECT_IRI = "http://data.knora.org/projects/images"

    val perm001 =
        ap(
            iri = "http://data.knora.org/permissions/001",
            p = AdministrativePermissionV1(
                forProject = IMAGES_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectMember,
                hasPermissions = Seq(PermissionV1.ProjectResourceCreateAllPermission)
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
                hasPermissions = Seq(
                    PermissionV1.DefaultChangeRightsPermission(Set(OntologyConstants.KnoraBase.Creator)),
                    PermissionV1.DefaultModifyPermission(Set(OntologyConstants.KnoraBase.ProjectMember)),
                    PermissionV1.DefaultViewPermission(Set(OntologyConstants.KnoraBase.KnownUser))
                )
            )
        )


    val perm003 =
        ap(
            iri = "http://data.knora.org/permissions/003",
            p = AdministrativePermissionV1(
                forProject = IMAGES_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectAdmin,
                hasPermissions = Seq(
                    PermissionV1.ProjectResourceCreateAllPermission,
                    PermissionV1.ProjectAdminAllPermission
                )
            )
        )

    /*************************************/
    /** Incunabula Project Permissions  **/
    /*************************************/

    val INCUNABULA_PROJECT_IRI = "http://data.knora.org/projects/77275339"

    val perm004 =
        ap(
            iri = "http://data.knora.org/permissions/004",
            p = AdministrativePermissionV1(
                forProject = INCUNABULA_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectMember,
                hasPermissions = Seq(PermissionV1.ProjectResourceCreateAllPermission)
            )
        )


    val perm005 =
        doap(
            iri = "http://data.knora.org/permissions/005",
            p = DefaultObjectAccessPermissionV1(
                forProject = INCUNABULA_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectMember,
                forResourceClass = OntologyConstants.KnoraBase.AllResourceClasses,
                forProperty = OntologyConstants.KnoraBase.AllProperties,
                hasPermissions = Seq(
                    PermissionV1.DefaultChangeRightsPermission(Set(OntologyConstants.KnoraBase.Creator)),
                    PermissionV1.DefaultModifyPermission(Set(OntologyConstants.KnoraBase.ProjectMember)),
                    PermissionV1.DefaultViewPermission(Set(OntologyConstants.KnoraBase.KnownUser))
                )
            )
        )


    val perm006 =
        ap(
            iri = "http://data.knora.org/permissions/006",
            p = AdministrativePermissionV1(
                forProject = INCUNABULA_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectAdmin,
                hasPermissions = Seq(
                    PermissionV1.ProjectResourceCreateAllPermission,
                    PermissionV1.ProjectAdminAllPermission
                )
            )
        )


    /************************************/
    /** 666 Project Permissions        **/
    /************************************/

    val TRIPLESIX_PROJECT_IRI = "http://data.knora.org/projects/666"

    val perm007 =
        ap(
            iri = "http://data.knora.org/permissions/007",
            p = AdministrativePermissionV1(
                forProject = TRIPLESIX_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectMember,
                hasPermissions = Seq(PermissionV1.ProjectResourceCreateAllPermission)
            )
        )


    val perm008 =
        doap(
            iri = "http://data.knora.org/permissions/008",
            p = DefaultObjectAccessPermissionV1(
                forProject = TRIPLESIX_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectMember,
                forResourceClass = OntologyConstants.KnoraBase.AllResourceClasses,
                forProperty = OntologyConstants.KnoraBase.AllProperties,
                hasPermissions = Seq(
                    PermissionV1.DefaultChangeRightsPermission(Set(OntologyConstants.KnoraBase.Creator)),
                    PermissionV1.DefaultModifyPermission(Set(OntologyConstants.KnoraBase.ProjectMember)),
                    PermissionV1.DefaultViewPermission(Set(OntologyConstants.KnoraBase.KnownUser))
                )
            )
        )


    val perm009 =
        ap(
            iri = "http://data.knora.org/permissions/009",
            p = AdministrativePermissionV1(
                forProject = TRIPLESIX_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectAdmin,
                hasPermissions = Seq(
                    PermissionV1.ProjectResourceCreateAllPermission,
                    PermissionV1.ProjectAdminAllPermission
                )
            )
        )



}
