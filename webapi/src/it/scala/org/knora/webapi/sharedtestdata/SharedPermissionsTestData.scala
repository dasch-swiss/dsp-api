/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.sharedtestdata

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ObjectAccessPermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM._
import org.knora.webapi.sharedtestdata.SharedTestDataV1._

/* Helper case classes */
case class ap(iri: String, p: AdministrativePermissionADM)
case class oap(iri: String, p: ObjectAccessPermissionADM)
case class doap(iri: String, p: DefaultObjectAccessPermissionADM)

/**
 * This object holds data representations for the data in 'test_data/all_data/permissions-data.ttl'.
 */
object SharedPermissionsTestData {

  /**
   * **********************************
   */
  /** Knora System Permissions        * */
  /**
   * **********************************
   */
  val perm001_d1: doap =
    doap(
      iri = "http://rdfh.ch/permissions/0000/001-d1",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/0000/001-d1",
        forProject = OntologyConstants.KnoraAdmin.SystemProject,
        forResourceClass = Some(OntologyConstants.KnoraBase.LinkObj),
        hasPermissions = Set(
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )

  val perm001_d2: doap =
    doap(
      iri = "http://rdfh.ch/permissions/0000/001-d2",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/0000/001-d2",
        forProject = OntologyConstants.KnoraAdmin.SystemProject,
        forResourceClass = Some(OntologyConstants.KnoraBase.Region),
        hasPermissions = Set(
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )

  val perm001_d3: doap =
    doap(
      iri = "http://rdfh.ch/permissions/0000/001-d3",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/0000/001-d3",
        forProject = OntologyConstants.KnoraAdmin.SystemProject,
        forProperty = Some(OntologyConstants.KnoraBase.HasStillImageFileValue),
        hasPermissions = Set(
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )

  /**
   * **********************************
   */
  /** Images Demo Project Permissions * */
  /**
   * **********************************
   */
  val perm002_a1: ap =
    ap(
      iri = "http://rdfh.ch/permissions/00FF/a1",
      p = AdministrativePermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/a1",
        forProject = imagesProjectIri,
        forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
        hasPermissions = Set(
          PermissionADM.ProjectResourceCreateAllPermission
        )
      )
    )

  val perm002_a2: ap =
    ap(
      iri = "http://rdfh.ch/permissions/00FF/a2",
      p = AdministrativePermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/a2",
        forProject = imagesProjectIri,
        forGroup = OntologyConstants.KnoraAdmin.ProjectAdmin,
        hasPermissions = Set(
          PermissionADM.ProjectResourceCreateAllPermission,
          PermissionADM.ProjectAdminAllPermission
        )
      )
    )

  val perm002_a3: ap =
    ap(
      iri = "http://rdfh.ch/permissions/00FF/a3",
      p = AdministrativePermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/a3",
        forProject = imagesProjectIri,
        forGroup = "http://rdfh.ch/groups/00FF/images-reviewer",
        hasPermissions = Set(
          PermissionADM.projectResourceCreateRestrictedPermission(s"$IMAGES_ONTOLOGY_IRI#bild"),
          PermissionADM.projectResourceCreateRestrictedPermission(s"$IMAGES_ONTOLOGY_IRI#bildformat")
        )
      )
    )

  val perm0003_a4: doap = doap(
    iri = "http://rdfh.ch/permissions/00FF/a4",
    p = DefaultObjectAccessPermissionADM(
      iri = "http://rdfh.ch/permissions/00FF/a4",
      forProject = imagesProjectIri,
      forGroup = Some("http://rdfh.ch/groups/00FF/images-reviewer"),
      hasPermissions = Set(PermissionADM.deletePermission(OntologyConstants.KnoraAdmin.Creator))
    )
  )

  val perm002_d1: doap =
    doap(
      iri = "http://rdfh.ch/permissions/00FF/d1",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/d1",
        forProject = imagesProjectIri,
        forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
        hasPermissions = Set(
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser)
        )
      )
    )

  val perm002_d2: doap =
    doap(
      iri = "http://rdfh.ch/permissions/00FF/d2",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/d2",
        forProject = imagesProjectIri,
        forGroup = Some(OntologyConstants.KnoraAdmin.KnownUser),
        hasPermissions = Set(
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser)
        )
      )
    )

  /**
   * **********************************
   */
  /** Incunabula Project Permissions  * */
  /**
   * **********************************
   */
  val perm003_a1: ap =
    ap(
      iri = "http://rdfh.ch/permissions/0803/003-a1",
      p = AdministrativePermissionADM(
        iri = "http://rdfh.ch/permissions/003-a1",
        forProject = SharedTestDataV1.incunabulaProjectIri,
        forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
        hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
      )
    )

  val perm003_a2: ap =
    ap(
      iri = "http://rdfh.ch/permissions/0803/003-a2",
      p = AdministrativePermissionADM(
        iri = "http://rdfh.ch/permissions/003-a2",
        forProject = SharedTestDataV1.incunabulaProjectIri,
        forGroup = OntologyConstants.KnoraAdmin.ProjectAdmin,
        hasPermissions = Set(
          PermissionADM.ProjectResourceCreateAllPermission,
          PermissionADM.ProjectAdminAllPermission
        )
      )
    )

  val perm003_o1: oap =
    oap(
      iri = "http://rdfh.ch/0803/00014b43f902", // incunabula:page
      p = ObjectAccessPermissionADM(
        forResource = Some("http://rdfh.ch/0803/00014b43f902"),
        hasPermissions = Set(
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )

  val perm003_o2: oap =
    oap(
      iri = "http://rdfh.ch/0803/00014b43f902/values/1ad3999ad60b", // knora-base:TextValue
      p = ObjectAccessPermissionADM(
        forValue = Some("http://rdfh.ch/0803/00014b43f902/values/1ad3999ad60b"),
        hasPermissions = Set(
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.UnknownUser),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator)
        )
      )
    )

  val perm003_d1: doap =
    doap(
      iri = "http://rdfh.ch/permissions/0803/003-d1",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/0803/003-d1",
        forProject = SharedTestDataV1.incunabulaProjectIri,
        forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
        hasPermissions = Set(
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )

  val perm003_d2: doap =
    doap(
      iri = "http://rdfh.ch/permissions/0803/003-d2",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/0803/003-d2",
        forProject = SharedTestDataV1.incunabulaProjectIri,
        forResourceClass = Some(INCUNABULA_BOOK_RESOURCE_CLASS),
        hasPermissions = Set(
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )

  val perm003_d3: doap =
    doap(
      iri = "http://rdfh.ch/permissions/0803/003-d3",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/0803/003-d3",
        forProject = SharedTestDataV1.incunabulaProjectIri,
        forResourceClass = Some(INCUNABULA_PAGE_RESOURCE_CLASS),
        hasPermissions = Set(
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser)
        )
      )
    )

  val perm003_d4: doap =
    doap(
      iri = "http://rdfh.ch/permissions/0803/003-d4",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/0803/003-d4",
        forProject = SharedTestDataV1.incunabulaProjectIri,
        forProperty = Some(INCUNABULA_PartOf_Property),
        hasPermissions = Set(
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )

  val perm003_d5: doap =
    doap(
      iri = "http://rdfh.ch/permissions/0803/003-d5",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/0803/003-d5",
        forProject = SharedTestDataV1.incunabulaProjectIri,
        forResourceClass = Some(INCUNABULA_PAGE_RESOURCE_CLASS),
        forProperty = Some(INCUNABULA_PartOf_Property),
        hasPermissions = Set(
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
        )
      )
    )

  /**
   * *********************************
   */
  /** Anything Project Permissions   * */
  /**
   * *********************************
   */
  val perm005_a1: ap =
    ap(
      iri = "http://rdfh.ch/permissions/0001/005-a1",
      p = AdministrativePermissionADM(
        iri = "http://rdfh.ch/permissions/0001/005-a1",
        forProject = SharedTestDataV1.anythingProjectIri,
        forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
        hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
      )
    )

  val perm005_a2: ap =
    ap(
      iri = "http://rdfh.ch/permissions/0001/005-a2",
      p = AdministrativePermissionADM(
        iri = "http://rdfh.ch/permissions/0001/005-a2",
        forProject = SharedTestDataV1.anythingProjectIri,
        forGroup = OntologyConstants.KnoraAdmin.ProjectAdmin,
        hasPermissions = Set(
          PermissionADM.ProjectResourceCreateAllPermission,
          PermissionADM.ProjectAdminAllPermission
        )
      )
    )

  val perm005_d1: doap =
    doap(
      iri = "http://rdfh.ch/permissions/0001/005-d1",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/0001/005-d1",
        forProject = SharedTestDataV1.anythingProjectIri,
        forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
        hasPermissions = Set(
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )
}
