/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi

import org.knora.webapi.SharedOntologyTestDataADM._
import org.knora.webapi.SharedTestDataV1._
import org.knora.webapi.messages.admin.responder.permissionsmessages.{AdministrativePermissionADM, DefaultObjectAccessPermissionADM, ObjectAccessPermissionADM, PermissionADM}

/* Helper case classes */
case class ap (iri: String, p: AdministrativePermissionADM)
case class oap (iri: String, p: ObjectAccessPermissionADM)
case class doap (iri: String, p: DefaultObjectAccessPermissionADM)

/**
  * This object holds data representations for the data in '_test_data/all_data/permissions-data.ttl'.
  */
object SharedPermissionsTestData {

    /*************************************/
    /** Knora System Permissions        **/
    /*************************************/

    val perm001_d1: doap =
        doap(
            iri = "http://rdfh.ch/permissions/0000/001-d1",
            p = DefaultObjectAccessPermissionADM(iri = "http://rdfh.ch/permissions/0000/001-d1", forProject = OntologyConstants.KnoraBase.SystemProject, forGroup = None, forResourceClass = Some(OntologyConstants.KnoraBase.LinkObj), forProperty = None, hasPermissions = Set(
                                PermissionADM.modifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionADM.viewPermission(OntologyConstants.KnoraBase.KnownUser),
                                PermissionADM.viewPermission(OntologyConstants.KnoraBase.UnknownUser)
                            ))
        )

    val perm001_d2: doap =
        doap(
            iri = "http://rdfh.ch/permissions/0000/001-d2",
            p = DefaultObjectAccessPermissionADM(iri = "http://rdfh.ch/permissions/0000/001-d2", forProject = OntologyConstants.KnoraBase.SystemProject, forGroup = None, forResourceClass = Some(OntologyConstants.KnoraBase.Region), forProperty = None, hasPermissions = Set(
                                PermissionADM.modifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionADM.viewPermission(OntologyConstants.KnoraBase.KnownUser),
                                PermissionADM.viewPermission(OntologyConstants.KnoraBase.UnknownUser)
                            ))
        )

    val perm001_d3: doap =
        doap(
            iri = "http://rdfh.ch/permissions/0000/001-d3",
            p = DefaultObjectAccessPermissionADM(iri = "http://rdfh.ch/permissions/0000/001-d3", forProject = OntologyConstants.KnoraBase.SystemProject, forGroup = None, forResourceClass = None, forProperty = Some(OntologyConstants.KnoraBase.HasStillImageFileValue), hasPermissions = Set(
                                PermissionADM.modifyPermission(OntologyConstants.KnoraBase.Creator),
                                PermissionADM.modifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionADM.viewPermission(OntologyConstants.KnoraBase.KnownUser),
                                PermissionADM.restrictedViewPermission(OntologyConstants.KnoraBase.UnknownUser)
                            ))
        )


    /*************************************/
    /** Images Demo Project Permissions **/
    /*************************************/

    val perm002_a1: ap =
        ap(
            iri = "http://rdfh.ch/permissions/00FF/a1",
            p = AdministrativePermissionADM(iri = "http://rdfh.ch/permissions/00FF/a1", forProject = IMAGES_PROJECT_IRI, forGroup = OntologyConstants.KnoraBase.ProjectMember, hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission))
        )

    val perm002_a2: ap =
        ap(
            iri = "http://rdfh.ch/permissions/00FF/a2",
            p = AdministrativePermissionADM(iri = "http://rdfh.ch/permissions/00FF/a2", forProject = IMAGES_PROJECT_IRI, forGroup = OntologyConstants.KnoraBase.ProjectAdmin, hasPermissions = Set(
                                PermissionADM.ProjectResourceCreateAllPermission,
                                PermissionADM.ProjectAdminAllPermission
                            ))
        )

    val perm002_a3: ap =
        ap(
            iri = "http://rdfh.ch/permissions/00FF/a3",
            p = AdministrativePermissionADM(iri = "http://rdfh.ch/permissions/00FF/a3", forProject = IMAGES_PROJECT_IRI, forGroup = "http://rdfh.ch/groups/00FF/images-reviewer", hasPermissions = Set(
                PermissionADM.projectResourceCreateRestrictedPermission(s"$IMAGES_ONTOLOGY_IRI#bild"),
                PermissionADM.projectResourceCreateRestrictedPermission(s"$IMAGES_ONTOLOGY_IRI#bildformat")
            ))
        )

    val perm002_d1: doap =
        doap(
            iri = "http://rdfh.ch/permissions/00FF/d1",
            p = DefaultObjectAccessPermissionADM(iri = "http://rdfh.ch/permissions/00FF/d1", forProject = IMAGES_PROJECT_IRI, forGroup = Some(OntologyConstants.KnoraBase.ProjectMember), forResourceClass = None, forProperty = None, hasPermissions = Set(
                                PermissionADM.changeRightsPermission(OntologyConstants.KnoraBase.Creator),
                                PermissionADM.modifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionADM.viewPermission(OntologyConstants.KnoraBase.KnownUser)
                            ))
        )

    val perm002_d2: doap =
        doap(
            iri = "http://rdfh.ch/permissions/00FF/d2",
            p = DefaultObjectAccessPermissionADM(iri = "http://rdfh.ch/permissions/00FF/d2", forProject = IMAGES_PROJECT_IRI, forGroup = Some(OntologyConstants.KnoraBase.KnownUser), forResourceClass = None, forProperty = None, hasPermissions = Set(
                PermissionADM.changeRightsPermission(OntologyConstants.KnoraBase.Creator),
                PermissionADM.modifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                PermissionADM.viewPermission(OntologyConstants.KnoraBase.KnownUser)
            ))
        )

    /*************************************/
    /** Incunabula Project Permissions  **/
    /*************************************/

    val perm003_a1: ap =
        ap(
            iri = "http://rdfh.ch/permissions/0803/003-a1",
            p = AdministrativePermissionADM(iri = "http://rdfh.ch/permissions/003-a1", forProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI, forGroup = OntologyConstants.KnoraBase.ProjectMember, hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission))
        )

    val perm003_a2: ap =
        ap(
            iri = "http://rdfh.ch/permissions/0803/003-a2",
            p = AdministrativePermissionADM(iri = "http://rdfh.ch/permissions/003-a2", forProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI, forGroup = OntologyConstants.KnoraBase.ProjectAdmin, hasPermissions = Set(
                                PermissionADM.ProjectResourceCreateAllPermission,
                                PermissionADM.ProjectAdminAllPermission
                            ))
        )

    val perm003_o1: oap =
        oap(
            iri = "http://rdfh.ch/00014b43f902", // incunabula:page
            p = ObjectAccessPermissionADM(forResource = Some("http://rdfh.ch/00014b43f902"), forValue = None, hasPermissions = Set(
                                PermissionADM.changeRightsPermission(OntologyConstants.KnoraBase.Creator),
                                PermissionADM.modifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionADM.viewPermission(OntologyConstants.KnoraBase.KnownUser),
                                PermissionADM.restrictedViewPermission(OntologyConstants.KnoraBase.UnknownUser)
                            ))
        )

    val perm003_o2: oap =
        oap(
            iri = "http://rdfh.ch/00014b43f902/values/1ad3999ad60b", // knora-base:TextValue
            p = ObjectAccessPermissionADM(forResource = None, forValue = Some("http://rdfh.ch/00014b43f902/values/1ad3999ad60b"), hasPermissions = Set(
                                    PermissionADM.viewPermission(OntologyConstants.KnoraBase.UnknownUser),
                                    PermissionADM.viewPermission(OntologyConstants.KnoraBase.KnownUser),
                                    PermissionADM.viewPermission(OntologyConstants.KnoraBase.ProjectMember),
                                    PermissionADM.changeRightsPermission(OntologyConstants.KnoraBase.Creator)
                                ))
        )

    val perm003_d1: doap =
        doap(
            iri = "http://rdfh.ch/permissions/0803/003-d1",
            p = DefaultObjectAccessPermissionADM(
                iri = "http://rdfh.ch/permissions/0803/003-d1",
                forProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                forGroup = Some(OntologyConstants.KnoraBase.ProjectMember),
                forResourceClass = None,
                forProperty = None,
                hasPermissions = Set(
                    PermissionADM.changeRightsPermission(OntologyConstants.KnoraBase.Creator),
                    PermissionADM.modifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                    PermissionADM.viewPermission(OntologyConstants.KnoraBase.KnownUser),
                    PermissionADM.restrictedViewPermission(OntologyConstants.KnoraBase.UnknownUser)
                ))
        )

    val perm003_d2: doap =
        doap(
            iri = "http://rdfh.ch/permissions/0803/003-d2",
            p = DefaultObjectAccessPermissionADM(
                iri = "http://rdfh.ch/permissions/0803/003-d2",
                forProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                forGroup = None,
                forResourceClass = Some(INCUNABULA_BOOK_RESOURCE_CLASS),
                forProperty = None,
                hasPermissions = Set(
                    PermissionADM.changeRightsPermission(OntologyConstants.KnoraBase.Creator),
                    PermissionADM.modifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                    PermissionADM.viewPermission(OntologyConstants.KnoraBase.KnownUser),
                    PermissionADM.restrictedViewPermission(OntologyConstants.KnoraBase.UnknownUser)
                ))
        )

    val perm003_d3: doap =
        doap(
            iri = "http://rdfh.ch/permissions/0803/003-d3",
            p = DefaultObjectAccessPermissionADM(
                iri = "http://rdfh.ch/permissions/0803/003-d3",
                forProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                forGroup = None,
                forResourceClass = Some(INCUNABULA_PAGE_RESOURCE_CLASS),
                forProperty = None,
                hasPermissions = Set(
                    PermissionADM.changeRightsPermission(OntologyConstants.KnoraBase.Creator),
                    PermissionADM.modifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                    PermissionADM.viewPermission(OntologyConstants.KnoraBase.KnownUser)
                ))
        )

    /************************************/
    /** Anything Project Permissions   **/
    /************************************/

    val perm005_a1: ap =
        ap(
            iri = "http://rdfh.ch/permissions/0001/005-a1",
            p = AdministrativePermissionADM(iri = "http://rdfh.ch/permissions/0001/005-a1", forProject = SharedTestDataV1.ANYTHING_PROJECT_IRI, forGroup = OntologyConstants.KnoraBase.ProjectMember, hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission))
        )

    val perm005_a2: ap =
        ap(
            iri = "http://rdfh.ch/permissions/0001/005-a2",
            p = AdministrativePermissionADM(iri = "http://rdfh.ch/permissions/0001/005-a2", forProject = SharedTestDataV1.ANYTHING_PROJECT_IRI, forGroup = OntologyConstants.KnoraBase.ProjectAdmin, hasPermissions = Set(
                                PermissionADM.ProjectResourceCreateAllPermission,
                                PermissionADM.ProjectAdminAllPermission
                            ))
        )

    val perm005_d1: doap =
        doap(
            iri = "http://rdfh.ch/permissions/0001/005-d1",
            p = DefaultObjectAccessPermissionADM(iri = "http://rdfh.ch/permissions/0001/005-d1", forProject = SharedTestDataV1.ANYTHING_PROJECT_IRI, forGroup = Some(OntologyConstants.KnoraBase.ProjectMember), forResourceClass = None, forProperty = None, hasPermissions = Set(
                                PermissionADM.changeRightsPermission(OntologyConstants.KnoraBase.Creator),
                                PermissionADM.modifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                                PermissionADM.viewPermission(OntologyConstants.KnoraBase.KnownUser),
                                PermissionADM.restrictedViewPermission(OntologyConstants.KnoraBase.UnknownUser)
                            ))
        )




}
