/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import org.junit.runner.RunWith
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import scala.collection.Map

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionType
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM2
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo

@RunWith(classOf[DspZTestJUnitRunner])
class PermissionUtilADMSpec extends ZIOSpecDefault {

  val permissionLiteral =
    "RV knora-admin:UnknownUser|V knora-admin:KnownUser|M knora-admin:ProjectMember|CR knora-admin:Creator"

  val parsedPermissionLiteral: Map[Permission.ObjectAccess, Set[IRI]] = Map(
    Permission.ObjectAccess.RestrictedView -> Set(KnoraGroupRepo.builtIn.UnknownUser.id.value),
    Permission.ObjectAccess.View           -> Set(KnoraGroupRepo.builtIn.KnownUser.id.value),
    Permission.ObjectAccess.Modify         -> Set(KnoraGroupRepo.builtIn.ProjectMember.id.value),
    Permission.ObjectAccess.ChangeRights   -> Set(KnoraGroupRepo.builtIn.Creator.id.value),
  )

  val spec: Spec[Any, Nothing] = suite("PermissionUtil")(
    test("return user's max permission for a specific resource (incunabula normal project member user)") {
      val result = PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataADM2.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.incunabulaMemberUser,
      )
      assertTrue(result.contains(Permission.ObjectAccess.Modify))
    },
    test("return user's max permission for a specific resource (incunabula project admin user)") {
      val result = PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataADM2.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.incunabulaProjectAdminUser,
      )
      assertTrue(result.contains(Permission.ObjectAccess.ChangeRights))
    },
    test("return user's max permission for a specific resource (incunabula creator user)") {
      val result = PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataADM2.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.incunabulaCreatorUser,
      )
      assertTrue(result.contains(Permission.ObjectAccess.ChangeRights))
    },
    test("return user's max permission for a specific resource (root user)") {
      val result = PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataADM2.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.rootUser,
      )
      assertTrue(result.contains(Permission.ObjectAccess.ChangeRights))
    },
    test("return user's max permission for a specific resource (normal user)") {
      val result = PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataADM2.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.normalUser,
      )
      assertTrue(result.contains(Permission.ObjectAccess.View))
    },
    test("return user's max permission for a specific resource (anonymous user)") {
      val result = PermissionUtilADM.getUserPermissionADM(
        entityCreator = "http://rdfh.ch/users/91e19f1e01",
        entityProject = SharedTestDataADM2.incunabulaProjectIri,
        entityPermissionLiteral = permissionLiteral,
        requestingUser = SharedTestDataADM.anonymousUser,
      )
      assertTrue(result.contains(Permission.ObjectAccess.RestrictedView))
    },
    test("return user's max permission from assertions for a specific resource") {
      val assertions: Seq[(IRI, String)] = Seq(
        (OntologyConstants.KnoraBase.AttachedToUser, "http://rdfh.ch/users/91e19f1e01"),
        (OntologyConstants.KnoraBase.AttachedToProject, SharedTestDataADM2.incunabulaProjectIri),
        (OntologyConstants.KnoraBase.HasPermissions, permissionLiteral),
      )

      val result = PermissionUtilADM.getUserPermissionFromAssertionsADM(
        entityIri = "http://rdfh.ch/00014b43f902",
        assertions = assertions,
        requestingUser = SharedTestDataADM.incunabulaMemberUser,
      )
      assertTrue(result.contains(Permission.ObjectAccess.Modify))
    },
    test("return parsed permissions string as 'Map[IRI, Set[String]]") {
      assertTrue(PermissionUtilADM.parsePermissions(permissionLiteral) == parsedPermissionLiteral)
    },
    test("return parsed permissions string as 'Set[PermissionV1]' (object access permissions)") {
      val hasPermissionsString =
        "M knora-admin:Creator,knora-admin:ProjectMember|V knora-admin:KnownUser,http://rdfh.ch/groups/customgroup|RV knora-admin:UnknownUser"

      val permissionsSet = Set(
        PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.Creator.id.value),
        PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.ProjectMember.id.value),
        PermissionADM.from(Permission.ObjectAccess.View, KnoraGroupRepo.builtIn.KnownUser.id.value),
        PermissionADM.from(Permission.ObjectAccess.View, "http://rdfh.ch/groups/customgroup"),
        PermissionADM.from(Permission.ObjectAccess.RestrictedView, KnoraGroupRepo.builtIn.UnknownUser.id.value),
      )

      val result = PermissionUtilADM.parsePermissionsWithType(
        Some(hasPermissionsString),
        PermissionType.OAP,
      )
      assertTrue(permissionsSet.subsetOf(result))
    },
    test("return parsed permissions string as 'Set[PermissionV1]' (administrative permissions)") {
      val hasPermissionsString =
        "ProjectResourceCreateAllPermission|ProjectAdminAllPermission|ProjectResourceCreateRestrictedPermission <http://www.knora.org/ontology/00FF/images#bild>,<http://www.knora.org/ontology/00FF/images#bildformat>"

      val permissionsSet = Set(
        PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
        PermissionADM.from(Permission.Administrative.ProjectAdminAll),
        PermissionADM.from(
          Permission.Administrative.ProjectResourceCreateRestricted,
          "http://www.knora.org/ontology/00FF/images#bild",
        ),
        PermissionADM.from(
          Permission.Administrative.ProjectResourceCreateRestricted,
          "http://www.knora.org/ontology/00FF/images#bildformat",
        ),
      )

      val result = PermissionUtilADM.parsePermissionsWithType(
        Some(hasPermissionsString),
        PermissionType.AP,
      )
      assertTrue(permissionsSet.subsetOf(result))
    },
    test("build a 'PermissionADM' object") {
      val result = PermissionUtilADM.buildPermissionObject(
        name = Permission.Administrative.ProjectResourceCreateRestricted.token,
        iris = Set("1", "2", "3"),
      )
      val expected = Set(
        PermissionADM.from(Permission.Administrative.ProjectResourceCreateRestricted, "1"),
        PermissionADM.from(Permission.Administrative.ProjectResourceCreateRestricted, "2"),
        PermissionADM.from(Permission.Administrative.ProjectResourceCreateRestricted, "3"),
      )
      assertTrue(result == expected)
    },
    test("remove duplicate permissions") {

      val duplicatedPermissions = Seq(
        PermissionADM.from(Permission.ObjectAccess.View, "1"),
        PermissionADM.from(Permission.ObjectAccess.View, "1"),
        PermissionADM.from(Permission.ObjectAccess.View, "2"),
        PermissionADM.from(Permission.ObjectAccess.ChangeRights, "2"),
        PermissionADM.from(Permission.ObjectAccess.ChangeRights, "3"),
        PermissionADM.from(Permission.ObjectAccess.ChangeRights, "3"),
      )

      val deduplicatedPermissions = Set(
        PermissionADM.from(Permission.ObjectAccess.View, "1"),
        PermissionADM.from(Permission.ObjectAccess.View, "2"),
        PermissionADM.from(Permission.ObjectAccess.ChangeRights, "2"),
        PermissionADM.from(Permission.ObjectAccess.ChangeRights, "3"),
      )

      val result = PermissionUtilADM.removeDuplicatePermissions(duplicatedPermissions)
      assertTrue(
        result.size == deduplicatedPermissions.size,
        deduplicatedPermissions.subsetOf(result),
      )
    },
    test("create permissions string") {
      val permissions = Set(
        PermissionADM.from(Permission.ObjectAccess.ChangeRights, "1"),
        PermissionADM.from(Permission.ObjectAccess.Delete, "2"),
        PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value),
        PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.ProjectMember.id.value),
        PermissionADM.from(Permission.ObjectAccess.View, KnoraGroupRepo.builtIn.KnownUser.id.value),
      )

      val permissionsString = "CR 1,knora-admin:Creator|D 2|M knora-admin:ProjectMember|V knora-admin:KnownUser"
      val result            = PermissionUtilADM.formatPermissionADMs(permissions, PermissionType.OAP)

      assertTrue(result == permissionsString)
    },
  )
}
