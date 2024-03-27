/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import org.apache.pekko

import scala.collection.Map

import org.knora.webapi._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionType
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM2
import org.knora.webapi.slice.admin.domain.model.ObjectAccessPermission

import pekko.testkit.ImplicitSender

class PermissionUtilADMSpec extends CoreSpec with ImplicitSender {

  val permissionLiteral =
    "RV knora-admin:UnknownUser|V knora-admin:KnownUser|M knora-admin:ProjectMember|CR knora-admin:Creator"

  val parsedPermissionLiteral: Map[ObjectAccessPermission, Set[IRI]] = Map(
    ObjectAccessPermission.RestrictedView -> Set(OntologyConstants.KnoraAdmin.UnknownUser),
    ObjectAccessPermission.View           -> Set(OntologyConstants.KnoraAdmin.KnownUser),
    ObjectAccessPermission.Modify         -> Set(OntologyConstants.KnoraAdmin.ProjectMember),
    ObjectAccessPermission.ChangeRights   -> Set(OntologyConstants.KnoraAdmin.Creator),
  )

  "PermissionUtil" should {

    "return user's max permission for a specific resource (incunabula normal project member user)" in {
      PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataADM2.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.incunabulaMemberUser,
      ) should equal(Some(ObjectAccessPermission.Modify)) // modify permission
    }

    "return user's max permission for a specific resource (incunabula project admin user)" in {
      PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataADM2.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.incunabulaProjectAdminUser,
      ) should equal(Some(ObjectAccessPermission.ChangeRights)) // change rights permission
    }

    "return user's max permission for a specific resource (incunabula creator user)" in {
      PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataADM2.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.incunabulaCreatorUser,
      ) should equal(Some(ObjectAccessPermission.ChangeRights)) // change rights permission
    }

    "return user's max permission for a specific resource (root user)" in {
      PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataADM2.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.rootUser,
      ) should equal(Some(ObjectAccessPermission.ChangeRights)) // change rights permission
    }

    "return user's max permission for a specific resource (normal user)" in {
      PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataADM2.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.normalUser,
      ) should equal(Some(ObjectAccessPermission.View)) // view permission
    }

    "return user's max permission for a specific resource (anonymous user)" in {
      PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataADM2.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.anonymousUser,
      ) should equal(Some(ObjectAccessPermission.RestrictedView)) // restricted view permission
    }

    "return user's max permission from assertions for a specific resource" in {
      val assertions: Seq[(IRI, String)] = Seq(
        (OntologyConstants.KnoraBase.AttachedToUser, "http://rdfh.ch/users/91e19f1e01"),
        (OntologyConstants.KnoraBase.AttachedToProject, SharedTestDataADM2.incunabulaProjectIri),
        (OntologyConstants.KnoraBase.HasPermissions, permissionLiteral),
      )

      PermissionUtilADM.getUserPermissionFromAssertionsADM(
        entityIri = "http://rdfh.ch/00014b43f902",
        assertions = assertions,
        requestingUser = SharedTestDataADM.incunabulaMemberUser,
      ) should equal(Some(ObjectAccessPermission.Modify)) // modify permissions
    }

    "return user's max permission on link value" ignore {
      // TODO
    }

    "return parsed permissions string as 'Map[IRI, Set[String]]" in {
      PermissionUtilADM.parsePermissions(permissionLiteral) should equal(parsedPermissionLiteral)
    }

    "return parsed permissions string as 'Set[PermissionV1]' (object access permissions)" in {
      val hasPermissionsString =
        "M knora-admin:Creator,knora-admin:ProjectMember|V knora-admin:KnownUser,http://rdfh.ch/groups/customgroup|RV knora-admin:UnknownUser"

      val permissionsSet = Set(
        PermissionADM.from(ObjectAccessPermission.Modify, OntologyConstants.KnoraAdmin.Creator),
        PermissionADM.from(ObjectAccessPermission.Modify, OntologyConstants.KnoraAdmin.ProjectMember),
        PermissionADM.from(ObjectAccessPermission.View, OntologyConstants.KnoraAdmin.KnownUser),
        PermissionADM.from(ObjectAccessPermission.View, "http://rdfh.ch/groups/customgroup"),
        PermissionADM.from(ObjectAccessPermission.RestrictedView, OntologyConstants.KnoraAdmin.UnknownUser),
      )

      PermissionUtilADM.parsePermissionsWithType(
        Some(hasPermissionsString),
        PermissionType.OAP,
      ) should contain allElementsOf permissionsSet
    }

    "return parsed permissions string as 'Set[PermissionV1]' (administrative permissions)" in {
      val hasPermissionsString =
        "ProjectResourceCreateAllPermission|ProjectAdminAllPermission|ProjectResourceCreateRestrictedPermission <http://www.knora.org/ontology/00FF/images#bild>,<http://www.knora.org/ontology/00FF/images#bildformat>"

      val permissionsSet = Set(
        PermissionADM.ProjectResourceCreateAllPermission,
        PermissionADM.ProjectAdminAllPermission,
        PermissionADM.projectResourceCreateRestrictedPermission("http://www.knora.org/ontology/00FF/images#bild"),
        PermissionADM.projectResourceCreateRestrictedPermission("http://www.knora.org/ontology/00FF/images#bildformat"),
      )

      PermissionUtilADM.parsePermissionsWithType(
        Some(hasPermissionsString),
        PermissionType.AP,
      ) should contain allElementsOf permissionsSet
    }

    "build a 'PermissionADM' object" in {
      PermissionUtilADM.buildPermissionObject(
        name = OntologyConstants.KnoraAdmin.ProjectResourceCreateRestrictedPermission,
        iris = Set("1", "2", "3"),
      ) should equal(
        Set(
          PermissionADM.projectResourceCreateRestrictedPermission("1"),
          PermissionADM.projectResourceCreateRestrictedPermission("2"),
          PermissionADM.projectResourceCreateRestrictedPermission("3"),
        ),
      )
    }

    "remove duplicate permissions" in {

      val duplicatedPermissions = Seq(
        PermissionADM.from(ObjectAccessPermission.View, "1"),
        PermissionADM.from(ObjectAccessPermission.View, "1"),
        PermissionADM.from(ObjectAccessPermission.View, "2"),
        PermissionADM.from(ObjectAccessPermission.ChangeRights, "2"),
        PermissionADM.from(ObjectAccessPermission.ChangeRights, "3"),
        PermissionADM.from(ObjectAccessPermission.ChangeRights, "3"),
      )

      val deduplicatedPermissions = Set(
        PermissionADM.from(ObjectAccessPermission.View, "1"),
        PermissionADM.from(ObjectAccessPermission.View, "2"),
        PermissionADM.from(ObjectAccessPermission.ChangeRights, "2"),
        PermissionADM.from(ObjectAccessPermission.ChangeRights, "3"),
      )

      val result = PermissionUtilADM.removeDuplicatePermissions(duplicatedPermissions)
      result.size should equal(deduplicatedPermissions.size)
      result should contain allElementsOf deduplicatedPermissions
    }

    "remove lesser permissions" in {
      val withLesserPermissions = Set(
        PermissionADM.from(ObjectAccessPermission.View, "1"),
        PermissionADM.from(ObjectAccessPermission.View, "1"),
        PermissionADM.from(ObjectAccessPermission.Modify, "2"),
        PermissionADM.from(ObjectAccessPermission.ChangeRights, "1"),
        PermissionADM.from(ObjectAccessPermission.Delete, "2"),
      )

      val withoutLesserPermissions = Set(
        PermissionADM.from(ObjectAccessPermission.ChangeRights, "1"),
        PermissionADM.from(ObjectAccessPermission.Delete, "2"),
      )

      val result = PermissionUtilADM.removeLesserPermissions(withLesserPermissions, PermissionType.OAP)
      result.size should equal(withoutLesserPermissions.size)
      result should contain allElementsOf withoutLesserPermissions
    }

    "create permissions string" in {
      val permissions = Set(
        PermissionADM.from(ObjectAccessPermission.ChangeRights, "1"),
        PermissionADM.from(ObjectAccessPermission.Delete, "2"),
        PermissionADM.from(ObjectAccessPermission.ChangeRights, OntologyConstants.KnoraAdmin.Creator),
        PermissionADM.from(ObjectAccessPermission.Modify, OntologyConstants.KnoraAdmin.ProjectMember),
        PermissionADM.from(ObjectAccessPermission.View, OntologyConstants.KnoraAdmin.KnownUser),
      )

      val permissionsString = "CR 1,knora-admin:Creator|D 2|M knora-admin:ProjectMember|V knora-admin:KnownUser"
      val result            = PermissionUtilADM.formatPermissionADMs(permissions, PermissionType.OAP)

      result should equal(permissionsString)

    }
  }
}
