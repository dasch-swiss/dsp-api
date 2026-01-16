/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.sharedtestdata

import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo

/**
 * This object holds data representations for the data in 'test_data/project_data/permissions-data.ttl'.
 */
object SharedPermissionsTestData {

  // Images Demo Project Permissions
  val perm002_a1 =
    AdministrativePermissionADM(
      iri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/QYdrY7O6QD2VR30oaAt3Yg"),
      forProject = imagesProjectIri,
      forGroup = KnoraGroupRepo.builtIn.ProjectMember.id,
      hasPermissions = Set(
        PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
      ),
    )

  val perm002_a2 =
    AdministrativePermissionADM(
      iri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ"),
      forProject = imagesProjectIri,
      forGroup = KnoraGroupRepo.builtIn.ProjectAdmin.id,
      hasPermissions = Set(
        PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
        PermissionADM.from(Permission.Administrative.ProjectAdminAll),
      ),
    )

  val perm002_a3 =
    AdministrativePermissionADM(
      iri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/6eeT4ezSRjO21Im2Dm12Qg"),
      forProject = imagesProjectIri,
      forGroup = GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/images-reviewer"),
      hasPermissions = Set(
        PermissionADM.from(Permission.Administrative.ProjectResourceCreateRestricted, s"$IMAGES_ONTOLOGY_IRI#bild"),
        PermissionADM.from(
          Permission.Administrative.ProjectResourceCreateRestricted,
          s"$IMAGES_ONTOLOGY_IRI#bildformat",
        ),
      ),
    )

  val perm0003_a4 =
    DefaultObjectAccessPermissionADM(
      iri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/PNTn7ZvsS_OabbexCxr_Eg"),
      forProject = imagesProjectIri,
      forGroup = Some(GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/images-reviewer")),
      hasPermissions = Set(PermissionADM.from(Permission.ObjectAccess.Delete, KnoraGroupRepo.builtIn.Creator.id.value)),
    )

  val perm002_d1 =
    DefaultObjectAccessPermissionADM(
      iri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA"),
      forProject = imagesProjectIri,
      forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id),
      hasPermissions = Set(
        PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value),
        PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.ProjectMember.id.value),
        PermissionADM.from(Permission.ObjectAccess.View, KnoraGroupRepo.builtIn.KnownUser.id.value),
      ),
    )

  val perm002_d2 =
    DefaultObjectAccessPermissionADM(
      iri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/9XTMKHm_ScmwtgDXbF6Onw"),
      forProject = imagesProjectIri,
      forGroup = Some(KnoraGroupRepo.builtIn.KnownUser.id),
      hasPermissions = Set(
        PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value),
        PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.ProjectMember.id.value),
        PermissionADM.from(Permission.ObjectAccess.View, KnoraGroupRepo.builtIn.KnownUser.id.value),
      ),
    )

  // Incunabula Project Permissions
  val perm003_a1 =
    AdministrativePermissionADM(
      iri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/kJ_xFUUJQLS9eJ3S9PazXQ"),
      forProject = incunabulaProjectIri,
      forGroup = KnoraGroupRepo.builtIn.ProjectMember.id,
      hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll)),
    )

  val perm003_a2 =
    AdministrativePermissionADM(
      iri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/OySsjGn8QSqIpXUiSYnSSQ"),
      forProject = incunabulaProjectIri,
      forGroup = KnoraGroupRepo.builtIn.ProjectAdmin.id,
      hasPermissions = Set(
        PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
        PermissionADM.from(Permission.Administrative.ProjectAdminAll),
      ),
    )

  val perm003_d1 =
    DefaultObjectAccessPermissionADM(
      iri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"),
      forProject = incunabulaProjectIri,
      forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id),
      hasPermissions = Set(
        PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value),
        PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.ProjectMember.id.value),
        PermissionADM.from(Permission.ObjectAccess.View, KnoraGroupRepo.builtIn.KnownUser.id.value),
        PermissionADM.from(Permission.ObjectAccess.RestrictedView, KnoraGroupRepo.builtIn.UnknownUser.id.value),
      ),
    )

  val perm003_d2 =
    DefaultObjectAccessPermissionADM(
      iri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/sdHG20U6RoiwSu8MeAT1vA"),
      forProject = incunabulaProjectIri,
      forResourceClass = Some(INCUNABULA_BOOK_RESOURCE_CLASS),
      hasPermissions = Set(
        PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value),
        PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.ProjectMember.id.value),
        PermissionADM.from(Permission.ObjectAccess.View, KnoraGroupRepo.builtIn.KnownUser.id.value),
        PermissionADM.from(Permission.ObjectAccess.RestrictedView, KnoraGroupRepo.builtIn.UnknownUser.id.value),
      ),
    )

  val perm003_d3 =
    DefaultObjectAccessPermissionADM(
      iri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/V7Fc9gnTRHWPN1xVXYVt9A"),
      forProject = incunabulaProjectIri,
      forResourceClass = Some(INCUNABULA_PAGE_RESOURCE_CLASS),
      hasPermissions = Set(
        PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value),
        PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.ProjectMember.id.value),
        PermissionADM.from(Permission.ObjectAccess.View, KnoraGroupRepo.builtIn.KnownUser.id.value),
      ),
    )

  val perm003_d4 =
    DefaultObjectAccessPermissionADM(
      iri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/T12XnPXxQ42jBMIf6RK1pg"),
      forProject = incunabulaProjectIri,
      forProperty = Some(INCUNABULA_PartOf_Property),
      hasPermissions = Set(
        PermissionADM.from(Permission.ObjectAccess.View, KnoraGroupRepo.builtIn.KnownUser.id.value),
        PermissionADM.from(Permission.ObjectAccess.RestrictedView, KnoraGroupRepo.builtIn.UnknownUser.id.value),
      ),
    )

  val perm003_d5 =
    DefaultObjectAccessPermissionADM(
      iri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/5r5B_SJzTuCf8Hwj3MZmgw"),
      forProject = incunabulaProjectIri,
      forResourceClass = Some(INCUNABULA_PAGE_RESOURCE_CLASS),
      forProperty = Some(INCUNABULA_PartOf_Property),
      hasPermissions = Set(
        PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.ProjectMember.id.value),
      ),
    )
}
