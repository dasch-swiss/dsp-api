/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import akka.testkit.ImplicitSender
import scala.collection.Map

import org.knora.webapi._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionType
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.PermissionUtilADM._
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataV1

class PermissionUtilADMSpec extends CoreSpec with ImplicitSender {

  val permissionLiteral =
    "RV knora-admin:UnknownUser|V knora-admin:KnownUser|M knora-admin:ProjectMember|CR knora-admin:Creator"

  val parsedPermissionLiteral: Map[EntityPermission, Set[IRI]] = Map(
    RestrictedViewPermission -> Set(OntologyConstants.KnoraAdmin.UnknownUser),
    ViewPermission           -> Set(OntologyConstants.KnoraAdmin.KnownUser),
    ModifyPermission         -> Set(OntologyConstants.KnoraAdmin.ProjectMember),
    ChangeRightsPermission   -> Set(OntologyConstants.KnoraAdmin.Creator)
  )

  "PermissionUtil" should {

    "return user's max permission for a specific resource (incunabula normal project member user)" in {
      PermissionUtilADM.getUserPermissionV1(
        entityIri = "http://rdfh.ch/00014b43f902",
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataV1.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        userProfile = SharedTestDataV1.incunabulaMemberUser
      ) should equal(Some(6)) // modify permission

      PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataV1.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.incunabulaMemberUser
      ) should equal(Some(ModifyPermission)) // modify permission
    }

    "return user's max permission for a specific resource (incunabula project admin user)" in {
      PermissionUtilADM.getUserPermissionV1(
        entityIri = "http://rdfh.ch/00014b43f902",
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataV1.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        userProfile = SharedTestDataV1.incunabulaProjectAdminUser
      ) should equal(Some(8)) // change rights permission

      PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataV1.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.incunabulaProjectAdminUser
      ) should equal(Some(ChangeRightsPermission)) // change rights permission
    }

    "return user's max permission for a specific resource (incunabula creator user)" in {
      PermissionUtilADM.getUserPermissionV1(
        entityIri = "http://rdfh.ch/00014b43f902",
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataV1.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        userProfile = SharedTestDataV1.incunabulaCreatorUser
      ) should equal(Some(8)) // change rights permission

      PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataV1.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.incunabulaCreatorUser
      ) should equal(Some(ChangeRightsPermission)) // change rights permission
    }

    "return user's max permission for a specific resource (root user)" in {
      PermissionUtilADM.getUserPermissionV1(
        entityIri = "http://rdfh.ch/00014b43f902",
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataV1.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        userProfile = SharedTestDataV1.rootUser
      ) should equal(Some(8)) // change rights permission

      PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataV1.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.rootUser
      ) should equal(Some(ChangeRightsPermission)) // change rights permission
    }

    "return user's max permission for a specific resource (normal user)" in {
      PermissionUtilADM.getUserPermissionV1(
        entityIri = "http://rdfh.ch/00014b43f902",
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataV1.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        userProfile = SharedTestDataV1.normalUser
      ) should equal(Some(2)) // view permission

      PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataV1.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.normalUser
      ) should equal(Some(ViewPermission)) // view permission
    }

    "return user's max permission for a specific resource (anonymous user)" in {
      PermissionUtilADM.getUserPermissionV1(
        entityIri = "http://rdfh.ch/00014b43f902",
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataV1.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        userProfile = SharedTestDataV1.anonymousUser
      ) should equal(Some(1)) // restricted view permission

      PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataV1.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.anonymousUser
      ) should equal(Some(RestrictedViewPermission)) // restricted view permission
    }

    "return user's max permission from assertions for a specific resource" in {
      val assertions: Seq[(IRI, String)] = Seq(
        (OntologyConstants.KnoraBase.AttachedToUser, "http://rdfh.ch/users/91e19f1e01"),
        (OntologyConstants.KnoraBase.AttachedToProject, SharedTestDataV1.incunabulaProjectIri),
        (OntologyConstants.KnoraBase.HasPermissions, permissionLiteral)
      )

      PermissionUtilADM.getUserPermissionFromAssertionsV1(
        entityIri = "http://rdfh.ch/00014b43f902",
        assertions = assertions,
        userProfile = SharedTestDataV1.incunabulaMemberUser
      ) should equal(Some(6)) // modify permissions

      PermissionUtilADM.getUserPermissionFromAssertionsADM(
        entityIri = "http://rdfh.ch/00014b43f902",
        assertions = assertions,
        requestingUser = SharedTestDataADM.incunabulaMemberUser
      ) should equal(Some(ModifyPermission)) // modify permissions
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
        PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.Creator),
        PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
        PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
        PermissionADM.viewPermission("http://rdfh.ch/groups/customgroup"),
        PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
      )

      PermissionUtilADM.parsePermissionsWithType(
        Some(hasPermissionsString),
        PermissionType.OAP
      ) should contain allElementsOf permissionsSet
    }

    "return parsed permissions string as 'Set[PermissionV1]' (administrative permissions)" in {
      val hasPermissionsString =
        "ProjectResourceCreateAllPermission|ProjectAdminAllPermission|ProjectResourceCreateRestrictedPermission <http://www.knora.org/ontology/00FF/images#bild>,<http://www.knora.org/ontology/00FF/images#bildformat>"

      val permissionsSet = Set(
        PermissionADM.ProjectResourceCreateAllPermission,
        PermissionADM.ProjectAdminAllPermission,
        PermissionADM.projectResourceCreateRestrictedPermission("http://www.knora.org/ontology/00FF/images#bild"),
        PermissionADM.projectResourceCreateRestrictedPermission("http://www.knora.org/ontology/00FF/images#bildformat")
      )

      PermissionUtilADM.parsePermissionsWithType(
        Some(hasPermissionsString),
        PermissionType.AP
      ) should contain allElementsOf permissionsSet
    }

    "build a 'PermissionV1' object" in {
      PermissionUtilADM.buildPermissionObject(
        name = OntologyConstants.KnoraAdmin.ProjectResourceCreateRestrictedPermission,
        iris = Set("1", "2", "3")
      ) should equal(
        Set(
          PermissionADM.projectResourceCreateRestrictedPermission("1"),
          PermissionADM.projectResourceCreateRestrictedPermission("2"),
          PermissionADM.projectResourceCreateRestrictedPermission("3")
        )
      )
    }

    "remove duplicate permissions" in {

      val duplicatedPermissions = Seq(
        PermissionADM.restrictedViewPermission("1"),
        PermissionADM.restrictedViewPermission("1"),
        PermissionADM.restrictedViewPermission("2"),
        PermissionADM.changeRightsPermission("2"),
        PermissionADM.changeRightsPermission("3"),
        PermissionADM.changeRightsPermission("3")
      )

      val deduplicatedPermissions = Set(
        PermissionADM.restrictedViewPermission("1"),
        PermissionADM.restrictedViewPermission("2"),
        PermissionADM.changeRightsPermission("2"),
        PermissionADM.changeRightsPermission("3")
      )

      val result = PermissionUtilADM.removeDuplicatePermissions(duplicatedPermissions)
      result.size should equal(deduplicatedPermissions.size)
      result should contain allElementsOf deduplicatedPermissions

    }

    "remove lesser permissions" in {
      val withLesserPermissions = Set(
        PermissionADM.restrictedViewPermission("1"),
        PermissionADM.viewPermission("1"),
        PermissionADM.modifyPermission("2"),
        PermissionADM.changeRightsPermission("1"),
        PermissionADM.deletePermission("2")
      )

      val withoutLesserPermissions = Set(
        PermissionADM.changeRightsPermission("1"),
        PermissionADM.deletePermission("2")
      )

      val result = PermissionUtilADM.removeLesserPermissions(withLesserPermissions, PermissionType.OAP)
      result.size should equal(withoutLesserPermissions.size)
      result should contain allElementsOf withoutLesserPermissions
    }

    "create permissions string" in {
      val permissions = Set(
        PermissionADM.changeRightsPermission("1"),
        PermissionADM.deletePermission("2"),
        PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
        PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
        PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser)
      )

      val permissionsString = "CR 1,knora-admin:Creator|D 2|M knora-admin:ProjectMember|V knora-admin:KnownUser"
      val result            = PermissionUtilADM.formatPermissionADMs(permissions, PermissionType.OAP)

      result should equal(permissionsString)

    }
  }
}
