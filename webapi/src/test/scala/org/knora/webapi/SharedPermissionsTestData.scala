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

import org.knora.webapi.messages.v1.responder.permissionmessages.{PermissionV1, _}

/* Helper case classes */
case class ap (iri: String, p: AdministrativePermissionV1)
case class oap (iri: String, p: ObjectAccessPermissionV1)
case class doap (iri: String, p: DefaultObjectAccessPermissionV1)

/**
  * This object holds data representations for the data in '_test_data/all_data/permissions-data.ttl'.
  */
object SharedPermissionsTestData {

    /*************************************/
    /** Knora System Permissions        **/
    /*************************************/

    val perm001_d1 =
        doap(
            iri = "http://data.knora.org/permissions/001-d1",
            p = DefaultObjectAccessPermissionV1(iri = "http://data.knora.org/permissions/001-d1", forProject = OntologyConstants.KnoraBase.SystemProject, forGroup = None, forResourceClass = Some(OntologyConstants.KnoraBase.LinkObj), forProperty = None, hasPermissions = Set(
                                PermissionV1.ModifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionV1.ViewPermission(OntologyConstants.KnoraBase.KnownUser),
                                PermissionV1.ViewPermission(OntologyConstants.KnoraBase.UnknownUser)
                            ))
        )

    val perm001_d2 =
        doap(
            iri = "http://data.knora.org/permissions/001-d2",
            p = DefaultObjectAccessPermissionV1(iri = "http://data.knora.org/permissions/001-d2", forProject = OntologyConstants.KnoraBase.SystemProject, forGroup = None, forResourceClass = Some(OntologyConstants.KnoraBase.Region), forProperty = None, hasPermissions = Set(
                                PermissionV1.ModifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionV1.ViewPermission(OntologyConstants.KnoraBase.KnownUser),
                                PermissionV1.ViewPermission(OntologyConstants.KnoraBase.UnknownUser)
                            ))
        )

    val perm001_d3 =
        doap(
            iri = "http://data.knora.org/permissions/001-d3",
            p = DefaultObjectAccessPermissionV1(iri = "http://data.knora.org/permissions/001-d3", forProject = OntologyConstants.KnoraBase.SystemProject, forGroup = None, forResourceClass = None, forProperty = Some(OntologyConstants.KnoraBase.HasStillImageFileValue), hasPermissions = Set(
                                PermissionV1.ModifyPermission(OntologyConstants.KnoraBase.Creator),
                                PermissionV1.ModifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionV1.ViewPermission(OntologyConstants.KnoraBase.KnownUser),
                                PermissionV1.RestrictedViewPermission(OntologyConstants.KnoraBase.UnknownUser)
                            ))
        )


    /*************************************/
    /** Images Demo Project Permissions **/
    /*************************************/

    val perm002_a1 =
        ap(
            iri = "http://data.knora.org/permissions/002-a1",
            p = AdministrativePermissionV1(iri = "http://data.knora.org/permissions/002-a1", forProject = SharedAdminTestData.IMAGES_PROJECT_IRI, forGroup = OntologyConstants.KnoraBase.ProjectMember, hasPermissions = Set(PermissionV1.ProjectResourceCreateAllPermission))
        )

    val perm002_a2 =
        ap(
            iri = "http://data.knora.org/permissions/002-a2",
            p = AdministrativePermissionV1(iri = "http://data.knora.org/permissions/002-a2", forProject = SharedAdminTestData.IMAGES_PROJECT_IRI, forGroup = OntologyConstants.KnoraBase.ProjectAdmin, hasPermissions = Set(
                                PermissionV1.ProjectResourceCreateAllPermission,
                                PermissionV1.ProjectAdminAllPermission
                            ))
        )

    val perm002_d1 =
        doap(
            iri = "http://data.knora.org/permissions/002-d1",
            p = DefaultObjectAccessPermissionV1(iri = "http://data.knora.org/permissions/002-d1", forProject = SharedAdminTestData.IMAGES_PROJECT_IRI, forGroup = Some(OntologyConstants.KnoraBase.ProjectMember), forResourceClass = None, forProperty = None, hasPermissions = Set(
                                PermissionV1.ChangeRightsPermission(OntologyConstants.KnoraBase.Creator),
                                PermissionV1.ModifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionV1.ViewPermission(OntologyConstants.KnoraBase.KnownUser)
                            ))
        )

    /*************************************/
    /** Incunabula Project Permissions  **/
    /*************************************/

    val perm003_a1 =
        ap(
            iri = "http://data.knora.org/permissions/003-a1",
            p = AdministrativePermissionV1(iri = "http://data.knora.org/permissions/003-a1", forProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI, forGroup = OntologyConstants.KnoraBase.ProjectMember, hasPermissions = Set(PermissionV1.ProjectResourceCreateAllPermission))
        )

    val perm003_a2 =
        ap(
            iri = "http://data.knora.org/permissions/003-a2",
            p = AdministrativePermissionV1(iri = "http://data.knora.org/permissions/003-a2", forProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI, forGroup = OntologyConstants.KnoraBase.ProjectAdmin, hasPermissions = Set(
                                PermissionV1.ProjectResourceCreateAllPermission,
                                PermissionV1.ProjectAdminAllPermission
                            ))
        )

    val perm003_o1 =
        oap(
            iri = "http://data.knora.org/00014b43f902", // incunabula:Page
            p = ObjectAccessPermissionV1(forResource = Some("http://data.knora.org/00014b43f902"), forValue = None, hasPermissions = Set(
                                PermissionV1.ModifyPermission(OntologyConstants.KnoraBase.Owner),
                                PermissionV1.ModifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionV1.ViewPermission(OntologyConstants.KnoraBase.KnownUser),
                                PermissionV1.RestrictedViewPermission(OntologyConstants.KnoraBase.UnknownUser)
                            ))
        )

    val perm003_o2 =
        oap(
            iri = "http://data.knora.org/00014b43f902/values/1ad3999ad60b", // knora-base:TextValue
            p = ObjectAccessPermissionV1(forResource = None, forValue = Some("http://data.knora.org/00014b43f902/values/1ad3999ad60b"), hasPermissions = Set(
                                    PermissionV1.ViewPermission(OntologyConstants.KnoraBase.UnknownUser),
                                    PermissionV1.ViewPermission(OntologyConstants.KnoraBase.KnownUser),
                                    PermissionV1.ViewPermission(OntologyConstants.KnoraBase.ProjectMember),
                                    PermissionV1.ViewPermission(OntologyConstants.KnoraBase.Owner)
                                ))
        )

    val perm003_d1 =
        doap(
            iri = "http://data.knora.org/permissions/003-d1",
            p = DefaultObjectAccessPermissionV1(iri = "http://data.knora.org/permissions/003-d1", forProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI, forGroup = Some(OntologyConstants.KnoraBase.ProjectMember), forResourceClass = None, forProperty = None, hasPermissions = Set(
                                PermissionV1.ChangeRightsPermission(OntologyConstants.KnoraBase.Creator),
                                PermissionV1.ModifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionV1.ViewPermission(OntologyConstants.KnoraBase.KnownUser),
                                PermissionV1.RestrictedViewPermission(OntologyConstants.KnoraBase.UnknownUser)
                            ))
        )

    val perm003_d2 =
        doap(
            iri = "http://data.knora.org/permissions/003-d2",
            p = DefaultObjectAccessPermissionV1(iri = "http://data.knora.org/permissions/003-d2", forProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI, forGroup = None, forResourceClass = Some("http://www.knora.org/ontology/incunabula#Book"), forProperty = None, hasPermissions = Set(
                                PermissionV1.ChangeRightsPermission(OntologyConstants.KnoraBase.Creator),
                                PermissionV1.ModifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionV1.ViewPermission(OntologyConstants.KnoraBase.KnownUser),
                                PermissionV1.RestrictedViewPermission(OntologyConstants.KnoraBase.UnknownUser)
                            ))
        )

    val perm003_d3 =
        doap(
            iri = "http://data.knora.org/permissions/003-d3",
            p = DefaultObjectAccessPermissionV1(iri = "http://data.knora.org/permissions/003-d3", forProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI, forGroup = None, forResourceClass = Some("http://www.knora.org/ontology/incunabula#Page"), forProperty = None, hasPermissions = Set(
                                PermissionV1.ChangeRightsPermission(OntologyConstants.KnoraBase.Creator),
                                PermissionV1.ModifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionV1.ViewPermission(OntologyConstants.KnoraBase.KnownUser)
                            ))
        )





    /************************************/
    /** 666 Project Permissions        **/
    /************************************/

    val perm004_a1 =
        ap(
            iri = "http://data.knora.org/permissions/004-a1",
            p = AdministrativePermissionV1(iri = "http://data.knora.org/permissions/004-a1", forProject = SharedAdminTestData.TRIPLESIX_PROJECT_IRI, forGroup = OntologyConstants.KnoraBase.ProjectMember, hasPermissions = Set(PermissionV1.ProjectResourceCreateAllPermission))
        )

    val perm004_a2 =
        ap(
            iri = "http://data.knora.org/permissions/004-a2",
            p = AdministrativePermissionV1(iri = "http://data.knora.org/permissions/004-a2", forProject = SharedAdminTestData.TRIPLESIX_PROJECT_IRI, forGroup = OntologyConstants.KnoraBase.ProjectAdmin, hasPermissions = Set(
                                PermissionV1.ProjectResourceCreateAllPermission,
                                PermissionV1.ProjectAdminAllPermission
                            ))
        )

    val perm004_d1 =
        doap(
            iri = "http://data.knora.org/permissions/004-d1",
            p = DefaultObjectAccessPermissionV1(iri = "http://data.knora.org/permissions/004-d1", forProject = SharedAdminTestData.TRIPLESIX_PROJECT_IRI, forGroup = Some(OntologyConstants.KnoraBase.ProjectMember), forResourceClass = None, forProperty = None, hasPermissions = Set(
                                PermissionV1.ChangeRightsPermission(OntologyConstants.KnoraBase.Creator),
                                PermissionV1.ModifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionV1.ViewPermission(OntologyConstants.KnoraBase.KnownUser)
                            ))
        )

    /************************************/
    /** Anything Project Permissions   **/
    /************************************/

    val perm005_a1 =
        ap(
            iri = "http://data.knora.org/permissions/005-a1",
            p = AdministrativePermissionV1(iri = "http://data.knora.org/permissions/005-a1", forProject = SharedAdminTestData.ANYTHING_PROJECT_IRI, forGroup = OntologyConstants.KnoraBase.ProjectMember, hasPermissions = Set(PermissionV1.ProjectResourceCreateAllPermission))
        )

    val perm005_a2 =
        ap(
            iri = "http://data.knora.org/permissions/005-a2",
            p = AdministrativePermissionV1(iri = "http://data.knora.org/permissions/005-a2", forProject = SharedAdminTestData.ANYTHING_PROJECT_IRI, forGroup = OntologyConstants.KnoraBase.ProjectAdmin, hasPermissions = Set(
                                PermissionV1.ProjectResourceCreateAllPermission,
                                PermissionV1.ProjectAdminAllPermission
                            ))
        )

    val perm005_d1 =
        doap(
            iri = "http://data.knora.org/permissions/005-d1",
            p = DefaultObjectAccessPermissionV1(iri = "http://data.knora.org/permissions/005-d1", forProject = SharedAdminTestData.ANYTHING_PROJECT_IRI, forGroup = Some(OntologyConstants.KnoraBase.ProjectMember), forResourceClass = None, forProperty = None, hasPermissions = Set(
                                PermissionV1.ChangeRightsPermission(OntologyConstants.KnoraBase.Creator),
                                PermissionV1.ModifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionV1.ViewPermission(OntologyConstants.KnoraBase.KnownUser)
                            ))
        )




}
