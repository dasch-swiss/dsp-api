/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.permissionsmessages

import zio.ZIO

import java.util.UUID

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.AdministrativePermissionAbbreviations
import org.knora.webapi.messages.OntologyConstants.KnoraBase.EntityPermissionAbbreviations
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsMessagesUtilADM.PermissionTypeAndCodes
import org.knora.webapi.responders.admin.PermissionsResponderADM
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM._
import org.knora.webapi.sharedtestdata.SharedTestDataADM2._
import org.knora.webapi.sharedtestdata._
import org.knora.webapi.slice.admin.api.service.PermissionsRestService
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

/**
 * This spec is used to test subclasses of the [[PermissionsResponderRequestADM]] class.
 */
class PermissionsMessagesADMSpec extends CoreSpec {

  "Administrative Permission Get Requests" should {

    "return 'BadRequest' if the supplied permission IRI for AdministrativePermissionForIriGetRequestADM is not valid" in {
      val permissionIri = "invalid-permission-IRI"
      val caught = intercept[BadRequestException](
        AdministrativePermissionForIriGetRequestADM(
          administrativePermissionIri = permissionIri,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID(),
        ),
      )
      assert(caught.getMessage === s"Invalid permission IRI: $permissionIri.")
    }

    "return 'BadRequest' if the supplied project IRI for AdministrativePermissionForProjectGroupGetADM is not valid" in {
      val projectIri = "invalid-project-IRI"
      val caught = intercept[BadRequestException](
        AdministrativePermissionForProjectGroupGetADM(
          projectIri = projectIri,
          groupIri = OntologyConstants.KnoraAdmin.ProjectMember,
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      assert(caught.getMessage === s"Invalid project IRI $projectIri")
    }

    "return 'ForbiddenException' if the user requesting AdministrativePermissionForProjectGroupGetADM is not system or project Admin" in {
      val caught = intercept[ForbiddenException](
        AdministrativePermissionForProjectGroupGetADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          groupIri = OntologyConstants.KnoraAdmin.ProjectMember,
          requestingUser = SharedTestDataADM.imagesUser02,
        ),
      )
      assert(caught.getMessage === "Administrative permission can only be queried by system and project admin.")
    }
  }

  "Administrative Permission Create Requests" should {
    "return 'BadRequest' if the supplied project IRI for AdministrativePermissionCreateRequestADM is not valid" in {
      val exit = UnsafeZioRun.run(
        PermissionsRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            forProject = "invalid-project-IRI",
            forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
            hasPermissions = Set(PermissionADM.ProjectAdminAllPermission),
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, "Project IRI is invalid.")
    }

    "return 'BadRequest' if the supplied group IRI for AdministrativePermissionCreateRequestADM is not valid" in {
      val groupIri = "invalid-group-iri"
      val exit = UnsafeZioRun.run(
        PermissionsRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = groupIri,
            hasPermissions = Set(PermissionADM.ProjectAdminAllPermission),
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Invalid group IRI $groupIri")
    }

    "return 'BadRequest' if the supplied permission IRI for AdministrativePermissionCreateRequestADM is not valid" in {
      val permissionIri = "invalid-permission-IRI"
      val exit = UnsafeZioRun.run(
        PermissionsRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            id = Some(permissionIri),
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
            hasPermissions = Set(PermissionADM.ProjectAdminAllPermission),
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
        PermissionsRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
            hasPermissions = hasPermissions,
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](
        exit,
        s"Invalid value for name parameter of hasPermissions: $invalidName, it should be one of " +
          s"${AdministrativePermissionAbbreviations.toString}",
      )
    }

    "return 'BadRequest' if the a permissions supplied for AdministrativePermissionCreateRequestADM had invalid name" in {
      val exit = UnsafeZioRun.run(
        PermissionsRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
            hasPermissions = Set.empty[PermissionADM],
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, "Permissions needs to be supplied.")
    }

    "return 'ForbiddenException' if the user requesting AdministrativePermissionCreateRequestADM is not system or project admin" in {
      val exit = UnsafeZioRun.run(
        PermissionsRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
            hasPermissions = Set(PermissionADM.ProjectAdminAllPermission),
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

  "Object Access Permission Get Requests" should {
    "return 'BadRequest' if the supplied resource IRI for ObjectAccessPermissionsForResourceGetADM is not a valid KnoraResourceIri" in {
      val caught = intercept[BadRequestException](
        ObjectAccessPermissionsForResourceGetADM(
          resourceIri = SharedTestDataADM.customValueIRI,
          requestingUser = SharedTestDataADM.anythingAdminUser,
        ),
      )
      // a value IRI is given instead of a resource IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid resource IRI: ${SharedTestDataADM.customValueIRI}")
    }

    "return 'BadRequest' if the supplied resource IRI for ObjectAccessPermissionsForValueGetADM is not a valid KnoraValueIri" in {
      val caught = intercept[BadRequestException](
        ObjectAccessPermissionsForValueGetADM(
          valueIri = SharedTestDataADM.customResourceIRI,
          requestingUser = SharedTestDataADM.anythingAdminUser,
        ),
      )
      // a resource IRI is given instead of a value IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid value IRI: ${SharedTestDataADM.customResourceIRI}")
    }
  }

  "Default Object Access Permission Get Requests" should {

    "return 'BadRequest' if the supplied project IRI for DefaultObjectAccessPermissionGetADM is not valid" in {
      val projectIri = "invalid-project-IRI"
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = projectIri,
          groupIri = Some(OntologyConstants.KnoraAdmin.ProjectMember),
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      assert(caught.getMessage === s"Invalid project IRI $projectIri")
    }

    "return 'BadRequest' if the supplied resourceClass IRI for DefaultObjectAccessPermissionGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          resourceClassIri = Some(SharedTestDataADM.customResourceIRI),
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      // a resource IRI is given instead of a resource class IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid resource class IRI: ${SharedTestDataADM.customResourceIRI}")
    }

    "return 'BadRequest' if the supplied property IRI for DefaultObjectAccessPermissionGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          propertyIri = Some(SharedTestDataADM.customValueIRI),
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      // a value IRI is given instead of a property IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid property IRI: ${SharedTestDataADM.customValueIRI}")
    }

    "return 'BadRequest' if both group and resource class are supplied for DefaultObjectAccessPermissionGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          groupIri = Some(OntologyConstants.KnoraAdmin.ProjectMember),
          resourceClassIri = Some(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      assert(caught.getMessage === s"Not allowed to supply groupIri and resourceClassIri together.")
    }

    "return 'BadRequest' if both group and property are supplied for DefaultObjectAccessPermissionGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          groupIri = Some(OntologyConstants.KnoraAdmin.ProjectMember),
          propertyIri = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY_LocalHost),
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      assert(caught.getMessage === s"Not allowed to supply groupIri and propertyIri together.")
    }

    "return 'BadRequest' if no group, resourceClassIri or propertyIri are supplied for DefaultObjectAccessPermissionGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      assert(
        caught.getMessage === s"Either a group, a resource class, a property, or a combination of resource class and property must be given.",
      )
    }

    "return 'ForbiddenException' if requesting user of DefaultObjectAccessPermissionGetRequestADM is not system or project admin" in {
      val caught = intercept[ForbiddenException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          groupIri = Some(OntologyConstants.KnoraAdmin.ProjectMember),
          requestingUser = SharedTestDataADM.imagesUser02,
        ),
      )
      assert(
        caught.getMessage === s"Default object access permissions can only be queried by system and project admin.",
      )
    }

    "return 'BadRequest' if the supplied permission IRI for DefaultObjectAccessPermissionForIriGetRequestADM is not valid" in {
      val permissionIri = "invalid-permission-IRI"
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionForIriGetRequestADM(
          defaultObjectAccessPermissionIri = permissionIri,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID(),
        ),
      )
      assert(caught.getMessage === s"Invalid permission IRI: $permissionIri.")
    }

    "return 'BadRequest' if the supplied resourceClass IRI for DefaultObjectAccessPermissionsStringForResourceClassGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionsStringForResourceClassGetADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          resourceClassIri = SharedTestDataADM.customResourceIRI,
          targetUser = SharedTestDataADM.imagesReviewerUser,
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      // a resource IRI is given instead of a resource class IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid resource class IRI: ${SharedTestDataADM.customResourceIRI}")
    }

    "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionsStringForResourceClassGetADM is not system or project admin" in {
      val caught = intercept[ForbiddenException](
        DefaultObjectAccessPermissionsStringForResourceClassGetADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
          targetUser = SharedTestDataADM.imagesReviewerUser,
          requestingUser = SharedTestDataADM.imagesUser02,
        ),
      )
      assert(caught.getMessage === "Default object access permissions can only be queried by system and project admin.")
    }

    "return 'BadRequest' if the supplied project IRI DefaultObjectAccessPermissionsStringForResourceClassGetADM is not valid" in {
      val projectIri = "invalid-project-IRI"
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionsStringForResourceClassGetADM(
          projectIri = projectIri,
          resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
          targetUser = SharedTestDataADM.imagesUser02,
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      assert(caught.getMessage === s"Invalid project IRI $projectIri")
    }

    "return 'BadRequest' if the target user of DefaultObjectAccessPermissionsStringForResourceClassGetADM is an Anonymous user" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionsStringForResourceClassGetADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
          targetUser = SharedTestDataADM.anonymousUser,
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      assert(caught.getMessage === s"Anonymous Users are not allowed.")
    }

    "return 'BadRequest' if the supplied project IRI DefaultObjectAccessPermissionsStringForPropertyGetADM is not valid" in {
      val projectIri = ""
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionsStringForPropertyGetADM(
          projectIri = projectIri,
          resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
          propertyIri = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY,
          targetUser = SharedTestDataADM.imagesUser02,
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      assert(caught.getMessage === s"Invalid project IRI $projectIri")
    }

    "return 'BadRequest' if the supplied resourceClass IRI for DefaultObjectAccessPermissionsStringForPropertyGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionsStringForPropertyGetADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          resourceClassIri = SharedTestDataADM.customResourceIRI,
          propertyIri = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY,
          targetUser = SharedTestDataADM.imagesReviewerUser,
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      // a resource IRI is given instead of a resource class IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid resource class IRI: ${SharedTestDataADM.customResourceIRI}")
    }

    "return 'BadRequest' if the supplied property IRI for DefaultObjectAccessPermissionsStringForPropertyGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionsStringForPropertyGetADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
          propertyIri = SharedTestDataADM.customValueIRI,
          targetUser = SharedTestDataADM.imagesReviewerUser,
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      // a value IRI is given instead of a property IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid property IRI: ${SharedTestDataADM.customValueIRI}")
    }

    "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionsStringForPropertyGetADM is not system or project admin" in {
      val caught = intercept[ForbiddenException](
        DefaultObjectAccessPermissionsStringForPropertyGetADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
          propertyIri = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY,
          targetUser = SharedTestDataADM.imagesReviewerUser,
          requestingUser = SharedTestDataADM.imagesUser02,
        ),
      )
      assert(caught.getMessage === "Default object access permissions can only be queried by system and project admin.")
    }

    "return 'BadRequest' if the target user of DefaultObjectAccessPermissionsStringForPropertyGetADM is an Anonymous user" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionsStringForPropertyGetADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
          propertyIri = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY,
          targetUser = SharedTestDataADM.anonymousUser,
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      assert(caught.getMessage === s"Anonymous Users are not allowed.")
    }
  }

  "Default Object Access Permission Create Requests" should {
    "return 'BadRequest' if the supplied project IRI for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val forProject = "invalid-project-IRI"
      val exit = UnsafeZioRun.run(
        PermissionsRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = forProject,
            forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember)),
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Project IRI is invalid.")
    }

    "return 'BadRequest' if the supplied group IRI for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val groupIri = "invalid-group-iri"
      val exit = UnsafeZioRun.run(
        PermissionsRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = Some(groupIri),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember)),
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Invalid group IRI $groupIri")
    }

    "return 'BadRequest' if the supplied custom permission IRI for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val permissionIri = "invalid-permission-IRI"
      val exit = UnsafeZioRun.run(
        PermissionsRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            id = Some(permissionIri),
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember)),
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Invalid permission IRI: $permissionIri.")
    }

    "return 'BadRequest' if the no permissions supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val exit = UnsafeZioRun.run(
        PermissionsRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = Some(SharedTestDataADM.thingSearcherGroup.id),
            hasPermissions = Set.empty[PermissionADM],
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, "Permissions needs to be supplied.")
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission with invalid name" in {
      val hasPermissions = Set(
        PermissionADM(
          name = "invalid",
          additionalInformation = Some(OntologyConstants.KnoraAdmin.Creator),
          permissionCode = Some(8),
        ),
      )
      val exit =
        UnsafeZioRun.run(ZIO.serviceWithZIO[PermissionsResponderADM](_.verifyHasPermissionsDOAP(hasPermissions)))
      assertFailsWithA[BadRequestException](
        exit,
        "Invalid value for name parameter of hasPermissions: invalid, it should be one of " +
          s"${EntityPermissionAbbreviations.toString}",
      )
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission with invalid code" in {
      val invalidCode = 10
      val hasPermissions = Set(
        PermissionADM(
          name = OntologyConstants.KnoraBase.ChangeRightsPermission,
          additionalInformation = Some(OntologyConstants.KnoraAdmin.Creator),
          permissionCode = Some(invalidCode),
        ),
      )

      val exit =
        UnsafeZioRun.run(ZIO.serviceWithZIO[PermissionsResponderADM](_.verifyHasPermissionsDOAP(hasPermissions)))
      assertFailsWithA[BadRequestException](
        exit,
        s"Invalid value for permissionCode parameter of hasPermissions: $invalidCode, it should be one of " +
          s"${PermissionTypeAndCodes.values.toString}",
      )
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission with inconsistent code and name" in {
      val code = 2
      val name = OntologyConstants.KnoraBase.ChangeRightsPermission
      val hasPermissions = Set(
        PermissionADM(
          name = name,
          additionalInformation = Some(OntologyConstants.KnoraAdmin.Creator),
          permissionCode = Some(code),
        ),
      )

      val exit =
        UnsafeZioRun.run(ZIO.serviceWithZIO[PermissionsResponderADM](_.verifyHasPermissionsDOAP(hasPermissions)))
      assertFailsWithA[BadRequestException](
        exit,
        s"Given permission code $code and permission name $name are not consistent.",
      )
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission without any code or name" in {

      val hasPermissions = Set(
        PermissionADM(
          name = "",
          additionalInformation = Some(OntologyConstants.KnoraAdmin.Creator),
          permissionCode = None,
        ),
      )

      val exit =
        UnsafeZioRun.run(ZIO.serviceWithZIO[PermissionsResponderADM](_.verifyHasPermissionsDOAP(hasPermissions)))
      assertFailsWithA[BadRequestException](
        exit,
        s"One of permission code or permission name must be provided for a default object access permission.",
      )
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission without additionalInformation parameter" in {

      val hasPermissions = Set(
        PermissionADM(
          name = OntologyConstants.KnoraBase.ChangeRightsPermission,
          additionalInformation = None,
          permissionCode = Some(8),
        ),
      )
      val exit =
        UnsafeZioRun.run(ZIO.serviceWithZIO[PermissionsResponderADM](_.verifyHasPermissionsDOAP(hasPermissions)))
      assertFailsWithA[BadRequestException](
        exit,
        s"additionalInformation of a default object access permission type cannot be empty.",
      )
    }

    "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionCreateRequestADM is not system or project Admin" in {
      val exit = UnsafeZioRun.run(
        PermissionsRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.anythingProjectIri,
            forGroup = Some(SharedTestDataADM.thingSearcherGroup.id),
            hasPermissions = Set(PermissionADM.restrictedViewPermission(SharedTestDataADM.thingSearcherGroup.id)),
          ),
          SharedTestDataADM.anythingUser2,
        ),
      )
      assertFailsWithA[ForbiddenException](
        exit,
        "You are logged in with username 'anything.user02', but only a system administrator or project administrator has permissions for this operation.",
      )
    }

    "return 'BadRequest' if the both group and resource class are supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val exit = UnsafeZioRun.run(
        PermissionsRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = anythingProjectIri,
            forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
            forResourceClass = Some(ANYTHING_THING_RESOURCE_CLASS_LocalHost),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember)),
          ),
          SharedTestDataADM.rootUser,
        ),
      )
      assertFailsWithA[BadRequestException](exit, "Not allowed to supply groupIri and resourceClassIri together.")
    }

    "return 'BadRequest' if the both group and property are supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val exit = UnsafeZioRun.run(
        PermissionsRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = anythingProjectIri,
            forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
            forProperty = Some(ANYTHING_HasDate_PROPERTY_LocalHost),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember)),
          ),
          SharedTestDataADM.rootUser,
        ),
      )
      assertFailsWithA[BadRequestException](exit, "Not allowed to supply groupIri and propertyIri together.")
    }

    "return 'BadRequest' if propertyIri supplied for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val exit = UnsafeZioRun.run(
        PermissionsRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = anythingProjectIri,
            forProperty = Some(SharedTestDataADM.customValueIRI),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember)),
          ),
          SharedTestDataADM.rootUser,
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Invalid property IRI: ${SharedTestDataADM.customValueIRI}")
    }

    "return 'BadRequest' if resourceClassIri supplied for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val exit = UnsafeZioRun.run(
        PermissionsRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = anythingProjectIri,
            forResourceClass = Some(ANYTHING_THING_RESOURCE_CLASS_LocalHost),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember)),
          ),
          SharedTestDataADM.rootUser,
        ),
      )
      assertFailsWithA[BadRequestException](
        exit,
        s"Invalid resource class IRI: $ANYTHING_THING_RESOURCE_CLASS_LocalHost",
      )
    }

    "return 'BadRequest' if neither a group, nor a resource class, nor a property is supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val exit = UnsafeZioRun.run(
        PermissionsRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = anythingProjectIri,
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember)),
          ),
          SharedTestDataADM.rootUser,
        ),
      )
      assertFailsWithA[BadRequestException](
        exit,
        "Either a group, a resource class, a property, or a combination of resource class and property must be given.",
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

  "given the permission IRI" should {
    "not get permission if invalid IRI given" in {
      val permissionIri = "invalid-iri"
      val caught = intercept[BadRequestException](
        PermissionByIriGetRequestADM(
          permissionIri = permissionIri,
          requestingUser = SharedTestDataADM.imagesUser02,
        ),
      )
      assert(caught.getMessage === s"Invalid permission IRI: $permissionIri.")
    }
  }
}
