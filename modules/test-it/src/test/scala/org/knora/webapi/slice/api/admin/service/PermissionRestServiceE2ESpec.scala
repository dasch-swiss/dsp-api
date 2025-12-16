/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

import zio.Exit
import zio.ZIO
import zio.test.*

import scala.reflect.ClassTag

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.admin.responder.permissionsmessages.*
import org.knora.webapi.responders.admin.PermissionsResponder
import org.knora.webapi.sharedtestdata.*
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM2.*
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo

object PermissionRestServiceE2ESpec extends E2EZSpec {
  private val permissionRestService = ZIO.serviceWithZIO[PermissionRestService]

  val e2eSpec = suite("PermissionResponder")(
    suite("Administrative Permission Create Requests")(
      test("return 'BadRequest' if the no permissions supplied for AdministrativePermissionCreateRequestADM") {
        val invalidName    = "Delete"
        val hasPermissions = Set(PermissionADM(name = invalidName, additionalInformation = None, permissionCode = None))
        permissionRestService(
          _.createAdministrativePermission(SharedTestDataADM.imagesUser01)(
            CreateAdministrativePermissionAPIRequestADM(
              forProject = SharedTestDataADM.imagesProjectIri,
              forGroup = KnoraGroupRepo.builtIn.ProjectMember.id,
              hasPermissions = hasPermissions,
            ),
          ),
        ).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageContaining[BadRequestException](
              s"Invalid value for name parameter of hasPermissions: $invalidName, it should be one of " +
                s"${Permission.Administrative.allTokens.mkString(", ")}",
            ),
          ),
        )
      },
      test(
        "return 'BadRequest' if the a permissions supplied for AdministrativePermissionCreateRequestADM had invalid name",
      ) {
        permissionRestService(
          _.createAdministrativePermission(SharedTestDataADM.imagesUser01)(
            CreateAdministrativePermissionAPIRequestADM(
              forProject = SharedTestDataADM.imagesProjectIri,
              forGroup = KnoraGroupRepo.builtIn.ProjectMember.id,
              hasPermissions = Set.empty[PermissionADM],
            ),
          ),
        ).exit
          .map(assert(_)(E2EZSpec.failsWithMessageContaining[BadRequestException]("Admin Permissions need to be supplied.")))
      },
      test(
        "return 'ForbiddenException' if the user requesting AdministrativePermissionCreateRequestADM is not system or project admin",
      ) {
        permissionRestService(
          _.createAdministrativePermission(SharedTestDataADM.imagesReviewerUser)(
            CreateAdministrativePermissionAPIRequestADM(
              forProject = SharedTestDataADM.imagesProjectIri,
              forGroup = KnoraGroupRepo.builtIn.ProjectMember.id,
              hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectAdminAll)),
            ),
          ),
        ).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageContaining[ForbiddenException](
              "You are logged in with username 'images-reviewer-user', " +
                "but only a system administrator or project administrator has permissions for this operation.",
            ),
          ),
        )
      },
    ),
    suite("Default Object Access Permission Create Requests")(
      test(
        "return 'BadRequest' if the supplied project IRI for DefaultObjectAccessPermissionCreateRequestADM is not valid",
      ) {
        permissionRestService(
          _.createDefaultObjectAccessPermission(SharedTestDataADM.imagesUser01)(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = "invalid-project-IRI",
              forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
              hasPermissions = Set(
                PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
              ),
            ),
          ),
        ).exit.map(assert(_)(E2EZSpec.failsWithMessageContaining[BadRequestException]("Project IRI is invalid.")))
      },
      test(
        "return 'BadRequest' if the supplied group IRI for DefaultObjectAccessPermissionCreateRequestADM is not valid",
      ) {
        val groupIri = "invalid-group-iri"
        permissionRestService(
          _.createDefaultObjectAccessPermission(SharedTestDataADM.imagesUser01)(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = SharedTestDataADM.imagesProjectIri.value,
              forGroup = Some(groupIri),
              hasPermissions = Set(
                PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
              ),
            ),
          ),
        ).exit
          .map(assert(_)(E2EZSpec.failsWithMessageContaining[BadRequestException](s"Group IRI is invalid: $groupIri")))
      },
      test(
        "return 'BadRequest' if the supplied custom permission IRI for DefaultObjectAccessPermissionCreateRequestADM is not valid",
      ) {
        val permissionIri = "invalid-permission-IRI"
        permissionRestService(
          _.createDefaultObjectAccessPermission(SharedTestDataADM.imagesUser01)(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              id = Some(permissionIri),
              forProject = SharedTestDataADM.imagesProjectIri.value,
              forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
              hasPermissions = Set(
                PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
              ),
            ),
          ),
        ).exit.map(
          assert(_)(E2EZSpec.failsWithMessageContaining[BadRequestException](s"Couldn't parse IRI: $permissionIri")),
        )
      },
      test("return 'BadRequest' if the no permissions supplied for DefaultObjectAccessPermissionCreateRequestADM") {
        permissionRestService(
          _.createDefaultObjectAccessPermission(SharedTestDataADM.imagesUser01)(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = SharedTestDataADM.imagesProjectIri.value,
              forGroup = Some(SharedTestDataADM.thingSearcherGroup.id),
              hasPermissions = Set.empty[PermissionADM],
            ),
          ),
        ).exit
          .map(assert(_)(E2EZSpec.failsWithMessageContaining[BadRequestException]("Permissions needs to be supplied.")))
      },
      test(
        "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission with invalid name",
      ) {
        val hasPermissions = Set(
          PermissionADM(
            name = "invalid",
            additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
            permissionCode = Some(8),
          ),
        )
        ZIO
          .serviceWithZIO[PermissionsResponder](_.verifyHasPermissionsDOAP(hasPermissions))
          .exit
          .map(
            assert(_)(
              E2EZSpec.failsWithMessageContaining[BadRequestException](
                "Invalid value for name parameter of hasPermissions: invalid, it should be one of " +
                  s"${Permission.ObjectAccess.allTokens.mkString(", ")}",
              ),
            ),
          )
      },
      test(
        "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission with invalid code",
      ) {
        val invalidCode    = 10
        val hasPermissions = Set(
          PermissionADM(
            name = Permission.ObjectAccess.ChangeRights.token,
            additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
            permissionCode = Some(invalidCode),
          ),
        )
        ZIO
          .serviceWithZIO[PermissionsResponder](_.verifyHasPermissionsDOAP(hasPermissions))
          .exit
          .map(
            assert(_)(
              E2EZSpec.failsWithMessageContaining[BadRequestException](
                s"Invalid value for permissionCode parameter of hasPermissions: $invalidCode, it should be one of " +
                  s"${Permission.ObjectAccess.allCodes.mkString(", ")}",
              ),
            ),
          )
      },
      test(
        "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission with inconsistent code and name",
      ) {
        val hasPermissions = Set(
          PermissionADM(
            name = Permission.ObjectAccess.ChangeRights.token,
            additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
            permissionCode = Some(Permission.ObjectAccess.View.code),
          ),
        )
        ZIO
          .serviceWithZIO[PermissionsResponder](_.verifyHasPermissionsDOAP(hasPermissions))
          .exit
          .map(
            assert(_)(
              E2EZSpec.failsWithMessageContaining[BadRequestException](
                s"Given permission code 2 and permission name CR are not consistent.",
              ),
            ),
          )
      },
      test(
        "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission without any code or name",
      ) {
        val hasPermissions = Set(
          PermissionADM(
            name = "",
            additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
            permissionCode = None,
          ),
        )
        ZIO
          .serviceWithZIO[PermissionsResponder](_.verifyHasPermissionsDOAP(hasPermissions))
          .exit
          .map(
            assert(_)(
              E2EZSpec.failsWithMessageContaining[BadRequestException](
                s"One of permission code or permission name must be provided for a default object access permission.",
              ),
            ),
          )
      },
      test(
        "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission without additionalInformation parameter",
      ) {
        val hasPermissions = Set(
          PermissionADM(
            name = Permission.ObjectAccess.ChangeRights.token,
            additionalInformation = None,
            permissionCode = Some(8),
          ),
        )
        ZIO
          .serviceWithZIO[PermissionsResponder](_.verifyHasPermissionsDOAP(hasPermissions))
          .exit
          .map(
            assert(_)(
              E2EZSpec.failsWithMessageContaining[BadRequestException](
                s"additionalInformation of a default object access permission type cannot be empty.",
              ),
            ),
          )
      },
      test(
        "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionCreateRequestADM is not system or project Admin",
      ) {
        permissionRestService(
          _.createDefaultObjectAccessPermission(SharedTestDataADM.anythingUser2)(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = SharedTestDataADM.anythingProjectIri.value,
              forGroup = Some(SharedTestDataADM.thingSearcherGroup.id),
              hasPermissions =
                Set(PermissionADM.from(Permission.ObjectAccess.RestrictedView, SharedTestDataADM.thingSearcherGroup.id)),
            ),
          ),
        ).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageContaining[ForbiddenException](
              "You are logged in with username 'anything.user02', but only a system administrator or project administrator has permissions for this operation.",
            ),
          ),
        )
      },
      test(
        "return 'BadRequest' if the both group and resource class are supplied for DefaultObjectAccessPermissionCreateRequestADM",
      ) {
        permissionRestService(
          _.createDefaultObjectAccessPermission(SharedTestDataADM.rootUser)(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = anythingProjectIri,
              forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
              forResourceClass = Some(ANYTHING_THING_RESOURCE_CLASS_LocalHost),
              hasPermissions = Set(
                PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
              ),
            ),
          ),
        ).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageContaining[BadRequestException](
              "DOAP restrictions must be either for a group, a resource class, a property, " +
                "or a combination of a resource class and a property. ",
            ),
          ),
        )
      },
      test(
        "return 'BadRequest' if the both group and property are supplied for DefaultObjectAccessPermissionCreateRequestADM",
      ) {
        permissionRestService(
          _.createDefaultObjectAccessPermission(SharedTestDataADM.rootUser)(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = anythingProjectIri,
              forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
              forProperty = Some(ANYTHING_HasDate_PROPERTY_LocalHost),
              hasPermissions = Set(
                PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
              ),
            ),
          ),
        ).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageContaining[BadRequestException](
              "DOAP restrictions must be either for a group, a resource class, a property, " +
                "or a combination of a resource class and a property. ",
            ),
          ),
        )
      },
      test(
        "return 'BadRequest' if propertyIri supplied for DefaultObjectAccessPermissionCreateRequestADM is not valid",
      ) {
        permissionRestService(
          _.createDefaultObjectAccessPermission(SharedTestDataADM.rootUser)(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = anythingProjectIri,
              forProperty = Some(SharedTestDataADM.customValueIRI),
              hasPermissions = Set(
                PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
              ),
            ),
          ),
        ).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageContaining[BadRequestException](
              "<http://rdfh.ch/0001/5zCt1EMJKezFUOW_RCB0Gw/values/tdWAtnWK2qUC6tr4uQLAHA> is not a Knora property IRI",
            ),
          ),
        )
      },
      test(
        "return 'BadRequest' if neither a group, nor a resource class, nor a property is supplied for DefaultObjectAccessPermissionCreateRequestADM",
      ) {
        permissionRestService(
          _.createDefaultObjectAccessPermission(SharedTestDataADM.rootUser)(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = anythingProjectIri,
              hasPermissions = Set(
                PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
              ),
            ),
          ),
        ).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageContaining[BadRequestException](
              "DOAP restrictions must be either for a group, a resource class, a property, " +
                "or a combination of a resource class and a property. ",
            ),
          ),
        )
      },
    ),
    suite("querying the user's 'PermissionsDataADM' with 'hasPermissionFor'")(
      test("return true if the user is allowed to create a resource (root user)") {
        val projectIri       = incunabulaProjectIri
        val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"
        assertTrue(
          SharedTestDataADM.rootUser.permissions.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri),
        )
      },
      test("return true if the user is allowed to create a resource (project admin user)") {
        val projectIri       = incunabulaProjectIri
        val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"
        assertTrue(
          SharedTestDataADM.incunabulaProjectAdminUser.permissions
            .hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri),
        )
      },
      test("return true if the user is allowed to create a resource (project member user)") {
        val projectIri       = incunabulaProjectIri
        val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"
        assertTrue(
          SharedTestDataADM.incunabulaMemberUser.permissions
            .hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri),
        )
      },
      test("return false if the user is not allowed to create a resource") {
        val projectIri       = incunabulaProjectIri
        val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"
        assertTrue(
          !SharedTestDataADM.normalUser.permissions
            .hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri),
        )
      },
      test("return true if the user is allowed to create a resource (ProjectResourceCreateRestrictedPermission)") {
        val projectIri                = imagesProjectIri
        val allowedResourceClassIri01 = s"$IMAGES_ONTOLOGY_IRI#bild"
        val allowedResourceClassIri02 = s"$IMAGES_ONTOLOGY_IRI#bildformat"
        val result1                   = SharedTestDataADM.imagesReviewerUser.permissions
          .hasPermissionFor(ResourceCreateOperation(allowedResourceClassIri01), projectIri)
        val result2 = SharedTestDataADM.imagesReviewerUser.permissions
          .hasPermissionFor(ResourceCreateOperation(allowedResourceClassIri02), projectIri)
        assertTrue(result1, result2)
      },
      test("return false if the user is not allowed to create a resource (ProjectResourceCreateRestrictedPermission)") {
        val projectIri                 = imagesProjectIri
        val notAllowedResourceClassIri = s"$IMAGES_ONTOLOGY_IRI#person"
        assertTrue(
          !SharedTestDataADM.imagesReviewerUser.permissions
            .hasPermissionFor(ResourceCreateOperation(notAllowedResourceClassIri), projectIri),
        )
      },
    ),
    suite("querying the user's 'PermissionsProfileV1' with 'hasProjectAdminAllPermissionFor'")(
      test("return true if the user has the 'ProjectAdminAllPermission' (incunabula project admin user)") {
        val projectIri = incunabulaProjectIri
        assertTrue(SharedTestDataADM.incunabulaProjectAdminUser.permissions.hasProjectAdminAllPermissionFor(projectIri))
      },
      test("return false if the user has the 'ProjectAdminAllPermission' (incunabula member user)") {
        val projectIri = incunabulaProjectIri
        assertTrue(!SharedTestDataADM.incunabulaMemberUser.permissions.hasProjectAdminAllPermissionFor(projectIri))
      },
    ),
  )
}
