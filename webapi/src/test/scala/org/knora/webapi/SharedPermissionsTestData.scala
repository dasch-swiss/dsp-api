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

package org.knora.webapi

import org.knora.webapi.messages.v1.responder.permissionmessages._

/* Helper case classes */
case class ap (iri: String, p: AdministrativePermissionV1)
case class doap (iri: String, p: DefaultObjectAccessPermissionV1)

/**
  * This object holds data representations for the data in '_test_data/all_data/permissions-data.ttl'.
  */
object SharedPermissionsTestData {

    /*************************************/
    /** Knora System Permissions        **/
    /*************************************/

    val perm001_1 =
        doap(
            iri = "http://data.knora.org/permissions/001-1",
            p = DefaultObjectAccessPermissionV1(
                forProject = OntologyConstants.KnoraBase.SystemProject,
                forGroup = None,
                forResourceClass = Some(OntologyConstants.KnoraBase.LinkObj),
                forProperty = None,
                hasPermissions = Seq(
                    PermissionV1.DefaultModifyPermission(Set(OntologyConstants.KnoraBase.ProjectMember)),
                    PermissionV1.DefaultViewPermission(Set(OntologyConstants.KnoraBase.KnownUser, OntologyConstants.KnoraBase.UnknownUser))
                )
            )
        )

    val perm001_2 =
        doap(
            iri = "http://data.knora.org/permissions/001-2",
            p = DefaultObjectAccessPermissionV1(
                forProject = OntologyConstants.KnoraBase.SystemProject,
                forGroup = None,
                forResourceClass = Some(OntologyConstants.KnoraBase.Region),
                forProperty = None,
                hasPermissions = Seq(
                    PermissionV1.DefaultModifyPermission(Set(OntologyConstants.KnoraBase.ProjectMember)),
                    PermissionV1.DefaultViewPermission(Set(OntologyConstants.KnoraBase.KnownUser, OntologyConstants.KnoraBase.UnknownUser))
                )
            )
        )

    val perm001_3 =
        doap(
            iri = "http://data.knora.org/permissions/001-3",
            p = DefaultObjectAccessPermissionV1(
                forProject = OntologyConstants.KnoraBase.SystemProject,
                forGroup = None,
                forResourceClass = Some(OntologyConstants.KnoraBase.HasStillImageFileValue),
                forProperty = None,
                hasPermissions = Seq(
                    PermissionV1.DefaultModifyPermission(Set(OntologyConstants.KnoraBase.Creator, OntologyConstants.KnoraBase.ProjectMember)),
                    PermissionV1.DefaultViewPermission(Set(OntologyConstants.KnoraBase.KnownUser, OntologyConstants.KnoraBase.UnknownUser))
                )
            )
        )


    /*************************************/
    /** Images Demo Project Permissions **/
    /*************************************/

    val perm002_1 =
        ap(
            iri = "http://data.knora.org/permissions/002-1",
            p = AdministrativePermissionV1(
                forProject = SharedAdminTestData.IMAGES_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectMember,
                hasPermissions = Seq(PermissionV1.ProjectResourceCreateAllPermission)
            )
        )

    val perm002_2 =
        ap(
            iri = "http://data.knora.org/permissions/002-2",
            p = AdministrativePermissionV1(
                forProject = SharedAdminTestData.IMAGES_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectAdmin,
                hasPermissions = Seq(
                    PermissionV1.ProjectResourceCreateAllPermission,
                    PermissionV1.ProjectAdminAllPermission
                )
            )
        )

    val perm002_3 =
        doap(
            iri = "http://data.knora.org/permissions/002-3",
            p = DefaultObjectAccessPermissionV1(
                forProject = SharedAdminTestData.IMAGES_PROJECT_IRI,
                forGroup = Some(OntologyConstants.KnoraBase.ProjectMember),
                forResourceClass = None,
                forProperty = None,
                hasPermissions = Seq(
                    PermissionV1.DefaultChangeRightsPermission(Set(OntologyConstants.KnoraBase.Creator)),
                    PermissionV1.DefaultModifyPermission(Set(OntologyConstants.KnoraBase.ProjectMember)),
                    PermissionV1.DefaultViewPermission(Set(OntologyConstants.KnoraBase.KnownUser))
                )
            )
        )

    /*************************************/
    /** Incunabula Project Permissions  **/
    /*************************************/

    val perm003_1 =
        ap(
            iri = "http://data.knora.org/permissions/003-1",
            p = AdministrativePermissionV1(
                forProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectMember,
                hasPermissions = Seq(PermissionV1.ProjectResourceCreateAllPermission)
            )
        )

    val perm003_2 =
        ap(
            iri = "http://data.knora.org/permissions/003-2",
            p = AdministrativePermissionV1(
                forProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectAdmin,
                hasPermissions = Seq(
                    PermissionV1.ProjectResourceCreateAllPermission,
                    PermissionV1.ProjectAdminAllPermission
                )
            )
        )

    val perm003_3 =
        doap(
            iri = "http://data.knora.org/permissions/003-3",
            p = DefaultObjectAccessPermissionV1(
                forProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                forGroup = Some(OntologyConstants.KnoraBase.ProjectMember),
                forResourceClass = None,
                forProperty = None,
                hasPermissions = Seq(
                    PermissionV1.DefaultChangeRightsPermission(Set(OntologyConstants.KnoraBase.Creator)),
                    PermissionV1.DefaultModifyPermission(Set(OntologyConstants.KnoraBase.ProjectMember)),
                    PermissionV1.DefaultViewPermission(Set(OntologyConstants.KnoraBase.KnownUser))
                )
            )
        )

    val perm003_4 =
        doap(
            iri = "http://data.knora.org/permissions/003-4",
            p = DefaultObjectAccessPermissionV1(
                forProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                forGroup = None,
                forResourceClass = Some("http://www.knora.org/ontology/incunabula#Book"),
                forProperty = None,
                hasPermissions = Seq(
                    PermissionV1.DefaultChangeRightsPermission(Set(OntologyConstants.KnoraBase.Creator)),
                    PermissionV1.DefaultModifyPermission(Set(OntologyConstants.KnoraBase.ProjectMember)),
                    PermissionV1.DefaultViewPermission(Set(OntologyConstants.KnoraBase.KnownUser))
                )
            )
        )

    val perm003_5 =
        doap(
            iri = "http://data.knora.org/permissions/003-5",
            p = DefaultObjectAccessPermissionV1(
                forProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                forGroup = None,
                forResourceClass = Some("http://www.knora.org/ontology/incunabula#Page"),
                forProperty = None,
                hasPermissions = Seq(
                    PermissionV1.DefaultChangeRightsPermission(Set(OntologyConstants.KnoraBase.Creator)),
                    PermissionV1.DefaultModifyPermission(Set(OntologyConstants.KnoraBase.ProjectMember)),
                    PermissionV1.DefaultViewPermission(Set(OntologyConstants.KnoraBase.KnownUser))
                )
            )
        )





    /************************************/
    /** 666 Project Permissions        **/
    /************************************/

    val perm004_1 =
        ap(
            iri = "http://data.knora.org/permissions/004-1",
            p = AdministrativePermissionV1(
                forProject = SharedAdminTestData.TRIPLESIX_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectMember,
                hasPermissions = Seq(PermissionV1.ProjectResourceCreateAllPermission)
            )
        )

    val perm004_2 =
        ap(
            iri = "http://data.knora.org/permissions/004-2",
            p = AdministrativePermissionV1(
                forProject = SharedAdminTestData.TRIPLESIX_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectAdmin,
                hasPermissions = Seq(
                    PermissionV1.ProjectResourceCreateAllPermission,
                    PermissionV1.ProjectAdminAllPermission
                )
            )
        )

    val perm004_3 =
        doap(
            iri = "http://data.knora.org/permissions/004-3",
            p = DefaultObjectAccessPermissionV1(
                forProject = SharedAdminTestData.TRIPLESIX_PROJECT_IRI,
                forGroup = Some(OntologyConstants.KnoraBase.ProjectMember),
                forResourceClass = None,
                forProperty = None,
                hasPermissions = Seq(
                    PermissionV1.DefaultChangeRightsPermission(Set(OntologyConstants.KnoraBase.Creator)),
                    PermissionV1.DefaultModifyPermission(Set(OntologyConstants.KnoraBase.ProjectMember)),
                    PermissionV1.DefaultViewPermission(Set(OntologyConstants.KnoraBase.KnownUser))
                )
            )
        )

    /************************************/
    /** Anything Project Permissions   **/
    /************************************/

    val perm005_1 =
        ap(
            iri = "http://data.knora.org/permissions/005-1",
            p = AdministrativePermissionV1(
                forProject = SharedAdminTestData.ANYTHING_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectMember,
                hasPermissions = Seq(PermissionV1.ProjectResourceCreateAllPermission)
            )
        )

    val perm005_2 =
        ap(
            iri = "http://data.knora.org/permissions/005-2",
            p = AdministrativePermissionV1(
                forProject = SharedAdminTestData.ANYTHING_PROJECT_IRI,
                forGroup = OntologyConstants.KnoraBase.ProjectAdmin,
                hasPermissions = Seq(
                    PermissionV1.ProjectResourceCreateAllPermission,
                    PermissionV1.ProjectAdminAllPermission
                )
            )
        )

    val perm005_3 =
        doap(
            iri = "http://data.knora.org/permissions/005-3",
            p = DefaultObjectAccessPermissionV1(
                forProject = SharedAdminTestData.ANYTHING_PROJECT_IRI,
                forGroup = Some(OntologyConstants.KnoraBase.ProjectMember),
                forResourceClass = None,
                forProperty = None,
                hasPermissions = Seq(
                    PermissionV1.DefaultChangeRightsPermission(Set(OntologyConstants.KnoraBase.Creator)),
                    PermissionV1.DefaultModifyPermission(Set(OntologyConstants.KnoraBase.ProjectMember)),
                    PermissionV1.DefaultViewPermission(Set(OntologyConstants.KnoraBase.KnownUser))
                )
            )
        )




}
