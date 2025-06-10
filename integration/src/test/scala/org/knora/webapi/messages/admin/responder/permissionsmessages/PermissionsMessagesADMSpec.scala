/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.permissionsmessages

import zio.ZIO

import java.util.UUID

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import org.knora.webapi.E2ESpec
import org.knora.webapi.responders.admin.PermissionsResponder
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.*
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM2.*
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

/**
 * This spec is used to test subclasses of the [[PermissionsResponderRequestADM]] class.
 */
class PermissionsMessagesADMSpec extends E2ESpec {

  object PermissionRestService {
    def createAdministrativePermission(
      request: CreateAdministrativePermissionAPIRequestADM,
      user: User,
    ): ZIO[PermissionsResponder, Throwable, AdministrativePermissionCreateResponseADM] =
      ZIO.serviceWithZIO[PermissionsResponder](_.createAdministrativePermission(request, user, UUID.randomUUID()))

    def createDefaultObjectAccessPermission(
      req: CreateDefaultObjectAccessPermissionAPIRequestADM,
    ): ZIO[PermissionsResponder, Throwable, DefaultObjectAccessPermissionCreateResponseADM] =
      ZIO.serviceWithZIO[PermissionsResponder](_.createDefaultObjectAccessPermission(req, UUID.randomUUID()))
  }

  "Administrative Permission Create Requests" should {
    "return 'BadRequest' if the supplied project IRI for AdministrativePermissionCreateRequestADM is not valid" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            forProject = "invalid-project-IRI",
            forGroup = KnoraGroupRepo.builtIn.ProjectMember.id.value,
            hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectAdminAll)),
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, "Project IRI is invalid.")
    }

    "return 'BadRequest' if the supplied group IRI for AdministrativePermissionCreateRequestADM is not valid" in {
      val groupIri = "invalid-group-iri"
      val exit = UnsafeZioRun.run(
        PermissionRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri.value,
            forGroup = groupIri,
            hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectAdminAll)),
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Invalid group IRI $groupIri")
    }

    "return 'BadRequest' if the supplied permission IRI for AdministrativePermissionCreateRequestADM is not valid" in {
      val permissionIri = "invalid-permission-IRI"
      val exit = UnsafeZioRun.run(
        PermissionRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            id = Some(permissionIri),
            forProject = SharedTestDataADM.imagesProjectIri.value,
            forGroup = KnoraGroupRepo.builtIn.ProjectMember.id.value,
            hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectAdminAll)),
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Invalid permission IRI: $permissionIri.")
    }

    "return 'BadRequest' if the no permissions supplied for AdministrativePermissionCreateRequestADM" in {
      val invalidName = "Delete"
      val hasPermissions = Set(
        PermissionADM(
          name = invalidName,
          additionalInformation = None,
          permissionCode = None,
        ),
      )
      val exit = UnsafeZioRun.run(
        PermissionRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri.value,
            forGroup = KnoraGroupRepo.builtIn.ProjectMember.id.value,
            hasPermissions = hasPermissions,
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](
        exit,
        s"Invalid value for name parameter of hasPermissions: $invalidName, it should be one of " +
          s"${Permission.Administrative.allTokens.mkString(", ")}",
      )
    }

    "return 'BadRequest' if the a permissions supplied for AdministrativePermissionCreateRequestADM had invalid name" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri.value,
            forGroup = KnoraGroupRepo.builtIn.ProjectMember.id.value,
            hasPermissions = Set.empty[PermissionADM],
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, "Permissions needs to be supplied.")
    }

    "return 'ForbiddenException' if the user requesting AdministrativePermissionCreateRequestADM is not system or project admin" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri.value,
            forGroup = KnoraGroupRepo.builtIn.ProjectMember.id.value,
            hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectAdminAll)),
          ),
          SharedTestDataADM.imagesReviewerUser,
        ),
      )
      assertFailsWithA[ForbiddenException](
        exit,
        "You are logged in with username 'images-reviewer-user', but only a system administrator or project administrator has permissions for this operation.",
      )
    }

  }

  "Default Object Access Permission Create Requests" should {
    "return 'BadRequest' if the supplied project IRI for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val forProject = "invalid-project-IRI"
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = forProject,
            forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
            hasPermissions = Set(
              PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
            ),
          ),
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Project IRI is invalid.")
    }

    "return 'BadRequest' if the supplied group IRI for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val groupIri = "invalid-group-iri"
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri.value,
            forGroup = Some(groupIri),
            hasPermissions = Set(
              PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
            ),
          ),
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Group IRI is invalid: $groupIri")
    }

    "return 'BadRequest' if the supplied custom permission IRI for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val permissionIri = "invalid-permission-IRI"
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            id = Some(permissionIri),
            forProject = SharedTestDataADM.imagesProjectIri.value,
            forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
            hasPermissions = Set(
              PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
            ),
          ),
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Couldn't parse IRI: $permissionIri")
    }

    "return 'BadRequest' if the no permissions supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri.value,
            forGroup = Some(SharedTestDataADM.thingSearcherGroup.id),
            hasPermissions = Set.empty[PermissionADM],
          ),
        ),
      )
      assertFailsWithA[BadRequestException](exit, "Permissions needs to be supplied.")
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission with invalid name" in {
      val hasPermissions = Set(
        PermissionADM(
          name = "invalid",
          additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
          permissionCode = Some(8),
        ),
      )
      val exit =
        UnsafeZioRun.run(ZIO.serviceWithZIO[PermissionsResponder](_.verifyHasPermissionsDOAP(hasPermissions)))
      assertFailsWithA[BadRequestException](
        exit,
        "Invalid value for name parameter of hasPermissions: invalid, it should be one of " +
          s"${Permission.ObjectAccess.allTokens.mkString(", ")}",
      )
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission with invalid code" in {
      val invalidCode = 10
      val hasPermissions = Set(
        PermissionADM(
          name = Permission.ObjectAccess.ChangeRights.token,
          additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
          permissionCode = Some(invalidCode),
        ),
      )

      val exit =
        UnsafeZioRun.run(ZIO.serviceWithZIO[PermissionsResponder](_.verifyHasPermissionsDOAP(hasPermissions)))
      assertFailsWithA[BadRequestException](
        exit,
        s"Invalid value for permissionCode parameter of hasPermissions: $invalidCode, it should be one of " +
          s"${Permission.ObjectAccess.allCodes.mkString(", ")}",
      )
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission with inconsistent code and name" in {
      val hasPermissions = Set(
        PermissionADM(
          name = Permission.ObjectAccess.ChangeRights.token,
          additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
          permissionCode = Some(Permission.ObjectAccess.View.code),
        ),
      )

      val exit =
        UnsafeZioRun.run(ZIO.serviceWithZIO[PermissionsResponder](_.verifyHasPermissionsDOAP(hasPermissions)))
      assertFailsWithA[BadRequestException](
        exit,
        s"Given permission code 2 and permission name CR are not consistent.",
      )
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission without any code or name" in {

      val hasPermissions = Set(
        PermissionADM(
          name = "",
          additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
          permissionCode = None,
        ),
      )

      val exit =
        UnsafeZioRun.run(ZIO.serviceWithZIO[PermissionsResponder](_.verifyHasPermissionsDOAP(hasPermissions)))
      assertFailsWithA[BadRequestException](
        exit,
        s"One of permission code or permission name must be provided for a default object access permission.",
      )
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission without additionalInformation parameter" in {

      val hasPermissions = Set(
        PermissionADM(
          name = Permission.ObjectAccess.ChangeRights.token,
          additionalInformation = None,
          permissionCode = Some(8),
        ),
      )
      val exit =
        UnsafeZioRun.run(ZIO.serviceWithZIO[PermissionsResponder](_.verifyHasPermissionsDOAP(hasPermissions)))
      assertFailsWithA[BadRequestException](
        exit,
        s"additionalInformation of a default object access permission type cannot be empty.",
      )
    }

    "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionCreateRequestADM is not system or project Admin" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.anythingProjectIri.value,
            forGroup = Some(SharedTestDataADM.thingSearcherGroup.id),
            hasPermissions =
              Set(PermissionADM.from(Permission.ObjectAccess.RestrictedView, SharedTestDataADM.thingSearcherGroup.id)),
          ),
        ),
      )
      assertFailsWithA[ForbiddenException](
        exit,
        "You are logged in with username 'anything.user02', but only a system administrator or project administrator has permissions for this operation.",
      )
    }

    "return 'BadRequest' if the both group and resource class are supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = anythingProjectIri,
            forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
            forResourceClass = Some(ANYTHING_THING_RESOURCE_CLASS_LocalHost),
            hasPermissions = Set(
              PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
            ),
          ),
        ),
      )
      assertFailsWithA[BadRequestException](
        exit,
        "DOAP restrictions must be either for a group, a resource class, a property, " +
          "or a combination of a resource class and a property. ",
      )
    }

    "return 'BadRequest' if the both group and property are supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = anythingProjectIri,
            forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
            forProperty = Some(ANYTHING_HasDate_PROPERTY_LocalHost),
            hasPermissions = Set(
              PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
            ),
          ),
        ),
      )
      assertFailsWithA[BadRequestException](
        exit,
        "DOAP restrictions must be either for a group, a resource class, a property, " +
          "or a combination of a resource class and a property. ",
      )
    }

    "return 'BadRequest' if propertyIri supplied for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = anythingProjectIri,
            forProperty = Some(SharedTestDataADM.customValueIRI),
            hasPermissions = Set(
              PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
            ),
          ),
        ),
      )
      assertFailsWithA[BadRequestException](
        exit,
        "<http://rdfh.ch/0001/5zCt1EMJKezFUOW_RCB0Gw/values/tdWAtnWK2qUC6tr4uQLAHA> is not a Knora property IRI",
      )
    }

    "return 'BadRequest' if neither a group, nor a resource class, nor a property is supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = anythingProjectIri,
            hasPermissions = Set(
              PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
            ),
          ),
        ),
      )
      assertFailsWithA[BadRequestException](
        exit,
        "DOAP restrictions must be either for a group, a resource class, a property, " +
          "or a combination of a resource class and a property. ",
      )
    }
  }

  "querying the user's 'PermissionsDataADM' with 'hasPermissionFor'" should {
    "return true if the user is allowed to create a resource (root user)" in {

      val projectIri       = incunabulaProjectIri
      val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

      val result =
        SharedTestDataADM.rootUser.permissions.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri)

      result should be(true)
    }

    "return true if the user is allowed to create a resource (project admin user)" in {

      val projectIri       = incunabulaProjectIri
      val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

      val result = SharedTestDataADM.incunabulaProjectAdminUser.permissions
        .hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri)

      result should be(true)
    }

    "return true if the user is allowed to create a resource (project member user)" in {

      val projectIri       = incunabulaProjectIri
      val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

      val result = SharedTestDataADM.incunabulaMemberUser.permissions
        .hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri)

      result should be(true)
    }

    "return false if the user is not allowed to create a resource" in {
      val projectIri       = incunabulaProjectIri
      val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

      val result =
        SharedTestDataADM.normalUser.permissions.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri)

      result should be(false)
    }

    "return true if the user is allowed to create a resource (ProjectResourceCreateRestrictedPermission)" in {
      val projectIri                = imagesProjectIri
      val allowedResourceClassIri01 = s"$IMAGES_ONTOLOGY_IRI#bild"
      val allowedResourceClassIri02 = s"$IMAGES_ONTOLOGY_IRI#bildformat"

      val result1 = SharedTestDataADM.imagesReviewerUser.permissions
        .hasPermissionFor(ResourceCreateOperation(allowedResourceClassIri01), projectIri)
      result1 should be(true)

      val result2 = SharedTestDataADM.imagesReviewerUser.permissions
        .hasPermissionFor(ResourceCreateOperation(allowedResourceClassIri02), projectIri)
      result2 should be(true)
    }

    "return false if the user is not allowed to create a resource (ProjectResourceCreateRestrictedPermission)" in {
      val projectIri                 = imagesProjectIri
      val notAllowedResourceClassIri = s"$IMAGES_ONTOLOGY_IRI#person"

      val result = SharedTestDataADM.imagesReviewerUser.permissions
        .hasPermissionFor(ResourceCreateOperation(notAllowedResourceClassIri), projectIri)
      result should be(false)
    }
  }

  "querying the user's 'PermissionsProfileV1' with 'hasProjectAdminAllPermissionFor'" should {

    "return true if the user has the 'ProjectAdminAllPermission' (incunabula project admin user)" in {
      val projectIri = incunabulaProjectIri
      val result     = SharedTestDataADM.incunabulaProjectAdminUser.permissions.hasProjectAdminAllPermissionFor(projectIri)

      result should be(true)
    }

    "return false if the user has the 'ProjectAdminAllPermission' (incunabula member user)" in {
      val projectIri = incunabulaProjectIri
      val result     = SharedTestDataADM.incunabulaMemberUser.permissions.hasProjectAdminAllPermissionFor(projectIri)

      result should be(false)
    }
  }
}
