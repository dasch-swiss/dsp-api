/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.permissionsmessages

import org.knora.webapi.CoreSpec
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.exceptions.ForbiddenException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.AdministrativePermissionAbbreviations
import org.knora.webapi.messages.OntologyConstants.KnoraBase.EntityPermissionAbbreviations
import org.knora.webapi.messages.StringFormatter.IriErrorMessages.UuidInvalid
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsMessagesUtilADM.PermissionTypeAndCodes
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM._
import org.knora.webapi.sharedtestdata.SharedTestDataV1._
import org.knora.webapi.sharedtestdata._

import java.util.UUID

/**
 * This spec is used to test subclasses of the [[PermissionsResponderRequestADM]] class.
 */
class PermissionsMessagesADMSpec extends CoreSpec() {

  "Administrative Permission Get Requests" should {
    "return 'BadRequest' if the supplied project IRI for AdministrativePermissionsForProjectGetRequestADM is not valid" in {
      val projectIri = "invalid-project-IRI"
      val caught = intercept[BadRequestException](
        AdministrativePermissionsForProjectGetRequestADM(
          projectIri = projectIri,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid project IRI $projectIri")
    }

    "return 'ForbiddenException' if the user requesting AdministrativePermissionsForProjectGetRequestADM is not system or project Admin" in {
      val caught = intercept[ForbiddenException](
        AdministrativePermissionsForProjectGetRequestADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          requestingUser = SharedTestDataADM.imagesUser02,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === "Administrative permission can only be queried by system and project admin.")
    }

    "return 'ForbiddenException' if the user requesting AdministrativePermissionForProjectGroupGetRequestADM is not system or project Admin" in {
      val caught = intercept[ForbiddenException](
        AdministrativePermissionForProjectGroupGetRequestADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          groupIri = OntologyConstants.KnoraAdmin.ProjectMember,
          requestingUser = SharedTestDataADM.imagesUser02
        )
      )
      assert(caught.getMessage === "Administrative permission can only be queried by system and project admin.")
    }

    "return 'BadRequest' if the supplied permission IRI for AdministrativePermissionForIriGetRequestADM is not valid" in {
      val permissionIri = "invalid-permission-IRI"
      val caught = intercept[BadRequestException](
        AdministrativePermissionForIriGetRequestADM(
          administrativePermissionIri = permissionIri,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid permission IRI $permissionIri is given.")
    }

    "return 'BadRequest' if the supplied project IRI for AdministrativePermissionForProjectGroupGetADM is not valid" in {
      val projectIri = "invalid-project-IRI"
      val caught = intercept[BadRequestException](
        AdministrativePermissionForProjectGroupGetADM(
          projectIri = projectIri,
          groupIri = OntologyConstants.KnoraAdmin.ProjectMember,
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      assert(caught.getMessage === s"Invalid project IRI $projectIri")
    }

    "return 'ForbiddenException' if the user requesting AdministrativePermissionForProjectGroupGetADM is not system or project Admin" in {
      val caught = intercept[ForbiddenException](
        AdministrativePermissionForProjectGroupGetADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          groupIri = OntologyConstants.KnoraAdmin.ProjectMember,
          requestingUser = SharedTestDataADM.imagesUser02
        )
      )
      assert(caught.getMessage === "Administrative permission can only be queried by system and project admin.")
    }
  }

  "Administrative Permission Create Requests" should {
    "return 'BadRequest' if the supplied project IRI for AdministrativePermissionCreateRequestADM is not valid" in {
      val forProject = "invalid-project-IRI"
      val caught = intercept[BadRequestException](
        AdministrativePermissionCreateRequestADM(
          createRequest = CreateAdministrativePermissionAPIRequestADM(
            forProject = forProject,
            forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
            hasPermissions = Set(PermissionADM.ProjectAdminAllPermission)
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid project IRI $forProject")
    }

    "return 'BadRequest' if the supplied group IRI for AdministrativePermissionCreateRequestADM is not valid" in {
      val groupIri = "invalid-group-iri"
      val caught = intercept[BadRequestException](
        AdministrativePermissionCreateRequestADM(
          createRequest = CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.ANYTHING_PROJECT_IRI,
            forGroup = groupIri,
            hasPermissions = Set(PermissionADM.ProjectAdminAllPermission)
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid group IRI $groupIri")
    }

    "return 'BadRequest' if the supplied permission IRI for AdministrativePermissionCreateRequestADM is not valid" in {
      val permissionIri = "invalid-permission-IRI"
      val caught = intercept[BadRequestException](
        AdministrativePermissionCreateRequestADM(
          createRequest = CreateAdministrativePermissionAPIRequestADM(
            id = Some(permissionIri),
            forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
            forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
            hasPermissions = Set(PermissionADM.ProjectAdminAllPermission)
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid permission IRI $permissionIri is given.")
    }

    "throw 'BadRequest' for AdministrativePermissionCreateRequestADM if the supplied permission IRI contains bad UUID version" in {
      val permissionIRIWithUUIDVersion3 = "http://rdfh.ch/permissions/0001/Ul3IYhDMOQ2fyoVY0ePz0w"
      val caught = intercept[BadRequestException](
        AdministrativePermissionCreateRequestADM(
          createRequest = CreateAdministrativePermissionAPIRequestADM(
            id = Some(permissionIRIWithUUIDVersion3),
            forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
            forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
            hasPermissions = Set(PermissionADM.ProjectAdminAllPermission)
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === IriErrorMessages.UuidInvalid)
    }

    "return 'BadRequest' if the no permissions supplied for AdministrativePermissionCreateRequestADM" in {
      val invalidName = "Delete"
      val hasPermissions = Set(
        PermissionADM(
          name = invalidName,
          additionalInformation = None,
          permissionCode = None
        )
      )
      val caught = intercept[BadRequestException](
        AdministrativePermissionCreateRequestADM(
          createRequest = CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
            forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
            hasPermissions = hasPermissions
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(
        caught.getMessage === s"Invalid value for name parameter of hasPermissions: $invalidName, it should be one of " +
          s"${AdministrativePermissionAbbreviations.toString}"
      )
    }

    "return 'BadRequest' if the a permissions supplied for AdministrativePermissionCreateRequestADM had invalid name" in {
      val caught = intercept[BadRequestException](
        AdministrativePermissionCreateRequestADM(
          createRequest = CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
            forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
            hasPermissions = Set.empty[PermissionADM]
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === "Permissions needs to be supplied.")
    }

    "return 'ForbiddenException' if the user requesting AdministrativePermissionCreateRequestADM is not system or project admin" in {
      val caught = intercept[ForbiddenException](
        AdministrativePermissionCreateRequestADM(
          createRequest = CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
            forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
            hasPermissions = Set(PermissionADM.ProjectAdminAllPermission)
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesReviewerUser,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === "A new administrative permission can only be added by system or project admin.")
    }

  }

  "Object Access Permission Get Requests" should {
    "return 'BadRequest' if the supplied resource IRI for ObjectAccessPermissionsForResourceGetADM is not a valid KnoraResourceIri" in {
      val caught = intercept[BadRequestException](
        ObjectAccessPermissionsForResourceGetADM(
          resourceIri = SharedTestDataADM.customValueIRI,
          requestingUser = SharedTestDataADM.anythingAdminUser
        )
      )
      // a value IRI is given instead of a resource IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid resource IRI: ${SharedTestDataADM.customValueIRI}")
    }

    "return 'BadRequest' if the supplied resource IRI for ObjectAccessPermissionsForValueGetADM is not a valid KnoraValueIri" in {
      val caught = intercept[BadRequestException](
        ObjectAccessPermissionsForValueGetADM(
          valueIri = SharedTestDataADM.customResourceIRI,
          requestingUser = SharedTestDataADM.anythingAdminUser
        )
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
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      assert(caught.getMessage === s"Invalid project IRI $projectIri")
    }

    "return 'BadRequest' if the supplied resourceClass IRI for DefaultObjectAccessPermissionGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          resourceClassIri = Some(SharedTestDataADM.customResourceIRI),
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      // a resource IRI is given instead of a resource class IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid resource class IRI: ${SharedTestDataADM.customResourceIRI}")
    }

    "return 'BadRequest' if the supplied property IRI for DefaultObjectAccessPermissionGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          propertyIri = Some(SharedTestDataADM.customValueIRI),
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      // a value IRI is given instead of a property IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid property IRI: ${SharedTestDataADM.customValueIRI}")
    }

    "return 'BadRequest' if both group and resource class are supplied for DefaultObjectAccessPermissionGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          groupIri = Some(OntologyConstants.KnoraAdmin.ProjectMember),
          resourceClassIri = Some(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      assert(caught.getMessage === s"Not allowed to supply groupIri and resourceClassIri together.")
    }

    "return 'BadRequest' if both group and property are supplied for DefaultObjectAccessPermissionGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          groupIri = Some(OntologyConstants.KnoraAdmin.ProjectMember),
          propertyIri = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY_LocalHost),
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      assert(caught.getMessage === s"Not allowed to supply groupIri and propertyIri together.")
    }

    "return 'BadRequest' if no group, resourceClassIri or propertyIri are supplied for DefaultObjectAccessPermissionGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      assert(
        caught.getMessage === s"Either a group, a resource class, a property, or a combination of resource class and property must be given."
      )
    }

    "return 'ForbiddenException' if requesting user of DefaultObjectAccessPermissionGetRequestADM is not system or project admin" in {
      val caught = intercept[ForbiddenException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          groupIri = Some(OntologyConstants.KnoraAdmin.ProjectMember),
          requestingUser = SharedTestDataADM.imagesUser02
        )
      )
      assert(
        caught.getMessage === s"Default object access permissions can only be queried by system and project admin."
      )
    }

    "return 'BadRequest' if the supplied project IRI for DefaultObjectAccessPermissionsForProjectGetRequestADM is not valid" in {
      val projectIri = "invalid-project-IRI"
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionsForProjectGetRequestADM(
          projectIri = projectIri,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid project IRI $projectIri")
    }

    "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionsForProjectGetRequestADM is not System or project Admin" in {
      val caught = intercept[ForbiddenException](
        DefaultObjectAccessPermissionsForProjectGetRequestADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          requestingUser = SharedTestDataADM.imagesUser02,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === "Default object access permissions can only be queried by system and project admin.")
    }

    "return 'BadRequest' if the supplied permission IRI for DefaultObjectAccessPermissionForIriGetRequestADM is not valid" in {
      val permissionIri = "invalid-permission-IRI"
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionForIriGetRequestADM(
          defaultObjectAccessPermissionIri = permissionIri,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid permission IRI $permissionIri is given.")
    }

    "return 'BadRequest' if the supplied resourceClass IRI for DefaultObjectAccessPermissionsStringForResourceClassGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionsStringForResourceClassGetADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          resourceClassIri = SharedTestDataADM.customResourceIRI,
          targetUser = SharedTestDataADM.imagesReviewerUser,
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      // a resource IRI is given instead of a resource class IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid resource class IRI: ${SharedTestDataADM.customResourceIRI}")
    }

    "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionsStringForResourceClassGetADM is not system or project admin" in {
      val caught = intercept[ForbiddenException](
        DefaultObjectAccessPermissionsStringForResourceClassGetADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
          targetUser = SharedTestDataADM.imagesReviewerUser,
          requestingUser = SharedTestDataADM.imagesUser02
        )
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
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      assert(caught.getMessage === s"Invalid project IRI $projectIri")
    }

    "return 'BadRequest' if the target user of DefaultObjectAccessPermissionsStringForResourceClassGetADM is an Anonymous user" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionsStringForResourceClassGetADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
          targetUser = SharedTestDataADM.anonymousUser,
          requestingUser = SharedTestDataADM.imagesUser01
        )
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
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      assert(caught.getMessage === s"Invalid project IRI $projectIri")
    }

    "return 'BadRequest' if the supplied resourceClass IRI for DefaultObjectAccessPermissionsStringForPropertyGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionsStringForPropertyGetADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          resourceClassIri = SharedTestDataADM.customResourceIRI,
          propertyIri = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY,
          targetUser = SharedTestDataADM.imagesReviewerUser,
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      // a resource IRI is given instead of a resource class IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid resource class IRI: ${SharedTestDataADM.customResourceIRI}")
    }

    "return 'BadRequest' if the supplied property IRI for DefaultObjectAccessPermissionsStringForPropertyGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionsStringForPropertyGetADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
          propertyIri = SharedTestDataADM.customValueIRI,
          targetUser = SharedTestDataADM.imagesReviewerUser,
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      // a value IRI is given instead of a property IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid property IRI: ${SharedTestDataADM.customValueIRI}")
    }

    "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionsStringForPropertyGetADM is not system or project admin" in {
      val caught = intercept[ForbiddenException](
        DefaultObjectAccessPermissionsStringForPropertyGetADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
          propertyIri = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY,
          targetUser = SharedTestDataADM.imagesReviewerUser,
          requestingUser = SharedTestDataADM.imagesUser02
        )
      )
      assert(caught.getMessage === "Default object access permissions can only be queried by system and project admin.")
    }

    "return 'BadRequest' if the target user of DefaultObjectAccessPermissionsStringForPropertyGetADM is an Anonymous user" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionsStringForPropertyGetADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
          propertyIri = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY,
          targetUser = SharedTestDataADM.anonymousUser,
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      assert(caught.getMessage === s"Anonymous Users are not allowed.")
    }
  }

  "Default Object Access Permission Create Requests" should {
    "return 'BadRequest' if the supplied project IRI for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val forProject = "invalid-project-IRI"
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = forProject,
            forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember))
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid project IRI $forProject")
    }

    "return 'BadRequest' if the supplied group IRI for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val groupIri = "invalid-group-iri"
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.ANYTHING_PROJECT_IRI,
            forGroup = Some(groupIri),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember))
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid group IRI $groupIri")
    }

    "return 'BadRequest' if the supplied custom permission IRI for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val permissionIri = "invalid-permission-IRI"
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            id = Some(permissionIri),
            forProject = SharedTestDataADM.ANYTHING_PROJECT_IRI,
            forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember))
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid permission IRI $permissionIri is given.")
    }

    "throw 'BadRequest' for DefaultObjectAccessPermissionCreateRequestADM if the supplied permission IRI contains bad UUID version" in {
      val permissionIRIWithUUIDVersion3 = "http://rdfh.ch/permissions/0001/Ul3IYhDMOQ2fyoVY0ePz0w"
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            id = Some(permissionIRIWithUUIDVersion3),
            forProject = SharedTestDataADM.ANYTHING_PROJECT_IRI,
            forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember))
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === IriErrorMessages.UuidInvalid)
    }

    "return 'BadRequest' if the no permissions supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.ANYTHING_PROJECT_IRI,
            forGroup = Some(SharedTestDataADM.thingSearcherGroup.id),
            hasPermissions = Set.empty[PermissionADM]
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === "Permissions needs to be supplied.")
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission with invalid name" in {
      val invalidName = "invalid"
      val hasPermissions = Set(
        PermissionADM(
          name = invalidName,
          additionalInformation = Some(OntologyConstants.KnoraAdmin.Creator),
          permissionCode = Some(8)
        )
      )
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
            forProperty = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
            hasPermissions = hasPermissions
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(
        caught.getMessage ===
          s"Invalid value for name parameter of hasPermissions: $invalidName, it should be one of " +
          s"${EntityPermissionAbbreviations.toString}"
      )
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission with invalid code" in {
      val invalidCode = 10
      val hasPermissions = Set(
        PermissionADM(
          name = OntologyConstants.KnoraBase.ChangeRightsPermission,
          additionalInformation = Some(OntologyConstants.KnoraAdmin.Creator),
          permissionCode = Some(invalidCode)
        )
      )
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
            forProperty = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
            hasPermissions = hasPermissions
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(
        caught.getMessage ===
          s"Invalid value for permissionCode parameter of hasPermissions: $invalidCode, it should be one of " +
          s"${PermissionTypeAndCodes.values.toString}"
      )
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission with inconsistent code and name" in {
      val code = 2
      val name = OntologyConstants.KnoraBase.ChangeRightsPermission
      val hasPermissions = Set(
        PermissionADM(
          name = name,
          additionalInformation = Some(OntologyConstants.KnoraAdmin.Creator),
          permissionCode = Some(code)
        )
      )
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
            forProperty = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
            hasPermissions = hasPermissions
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Given permission code $code and permission name $name are not consistent.")
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission without any code or name" in {

      val hasPermissions = Set(
        PermissionADM(
          name = "",
          additionalInformation = Some(OntologyConstants.KnoraAdmin.Creator),
          permissionCode = None
        )
      )
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
            forProperty = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
            hasPermissions = hasPermissions
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(
        caught.getMessage ===
          s"One of permission code or permission name must be provided for a default object access permission."
      )
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission without additionalInformation parameter" in {

      val hasPermissions = Set(
        PermissionADM(
          name = OntologyConstants.KnoraBase.ChangeRightsPermission,
          additionalInformation = None,
          permissionCode = Some(8)
        )
      )
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
            forProperty = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
            hasPermissions = hasPermissions
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.anythingAdminUser,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(
        caught.getMessage ===
          s"additionalInformation of a default object access permission type cannot be empty."
      )
    }

    "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionCreateRequestADM is not system or project Admin" in {
      val caught = intercept[ForbiddenException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.ANYTHING_PROJECT_IRI,
            forGroup = Some(SharedTestDataADM.thingSearcherGroup.id),
            hasPermissions = Set(PermissionADM.restrictedViewPermission(SharedTestDataADM.thingSearcherGroup.id))
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.anythingUser2,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === "A new default object access permission can only be added by a system admin.")
    }

    "return 'BadRequest' if the both group and resource class are supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = ANYTHING_PROJECT_IRI,
            forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
            forResourceClass = Some(ANYTHING_THING_RESOURCE_CLASS_LocalHost),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember))
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === "Not allowed to supply groupIri and resourceClassIri together.")
    }

    "return 'BadRequest' if the both group and property are supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = ANYTHING_PROJECT_IRI,
            forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
            forProperty = Some(ANYTHING_HasDate_PROPERTY_LocalHost),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember))
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === "Not allowed to supply groupIri and propertyIri together.")
    }

    "return 'BadRequest' if propertyIri supplied for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = ANYTHING_PROJECT_IRI,
            forProperty = Some(SharedTestDataADM.customValueIRI),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember))
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid property IRI: ${SharedTestDataADM.customValueIRI}")
    }

    "return 'BadRequest' if resourceClassIri supplied for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = ANYTHING_PROJECT_IRI,
            forResourceClass = Some(ANYTHING_THING_RESOURCE_CLASS_LocalHost),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember))
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid resource class IRI: $ANYTHING_THING_RESOURCE_CLASS_LocalHost")
    }

    "return 'BadRequest' if neither a group, nor a resource class, nor a property is supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = ANYTHING_PROJECT_IRI,
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember))
          ).prepareHasPermissions,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(
        caught.getMessage === "Either a group, a resource class, a property, or a combination of resource class and property must be given."
      )
    }
  }

  "get all project permissions" should {
    "return 'BadRequest' if the supplied project IRI for PermissionsForProjectGetRequestADM is not valid" in {
      val projectIri = "invalid-project-IRI"
      val caught = intercept[BadRequestException](
        PermissionsForProjectGetRequestADM(
          projectIri = projectIri,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid project IRI $projectIri")
    }

    "return 'ForbiddenException' if the user requesting PermissionsForProjectGetRequestADM is not system or project Admin" in {
      val caught = intercept[ForbiddenException](
        PermissionsForProjectGetRequestADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser02,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === "Permissions can only be queried by system and project admin.")
    }
  }

  "querying the user's 'PermissionsDataADM' with 'hasPermissionFor'" should {
    "return true if the user is allowed to create a resource (root user)" in {

      val projectIri       = INCUNABULA_PROJECT_IRI
      val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

      val result = SharedTestDataADM.rootUser.permissions.hasPermissionFor(
        ResourceCreateOperation(resourceClassIri),
        projectIri,
        None
      )

      result should be(true)
    }

    "return true if the user is allowed to create a resource (project admin user)" in {

      val projectIri       = INCUNABULA_PROJECT_IRI
      val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

      val result = SharedTestDataADM.incunabulaProjectAdminUser.permissions
        .hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri, None)

      result should be(true)
    }

    "return true if the user is allowed to create a resource (project member user)" in {

      val projectIri       = INCUNABULA_PROJECT_IRI
      val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

      val result = SharedTestDataADM.incunabulaMemberUser.permissions
        .hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri, None)

      result should be(true)
    }

    "return false if the user is not allowed to create a resource" in {
      val projectIri       = INCUNABULA_PROJECT_IRI
      val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

      val result = SharedTestDataADM.normalUser.permissions.hasPermissionFor(
        ResourceCreateOperation(resourceClassIri),
        projectIri,
        None
      )

      result should be(false)
    }

    "return true if the user is allowed to create a resource (ProjectResourceCreateRestrictedPermission)" in {
      val projectIri                 = IMAGES_PROJECT_IRI
      val allowedResourceClassIri01  = s"$IMAGES_ONTOLOGY_IRI#bild"
      val allowedResourceClassIri02  = s"$IMAGES_ONTOLOGY_IRI#bildformat"
      val notAllowedResourceClassIri = s"$IMAGES_ONTOLOGY_IRI#person"

      val result1 = SharedTestDataADM.imagesReviewerUser.permissions
        .hasPermissionFor(ResourceCreateOperation(allowedResourceClassIri01), projectIri, None)
      result1 should be(true)

      val result2 = SharedTestDataADM.imagesReviewerUser.permissions
        .hasPermissionFor(ResourceCreateOperation(allowedResourceClassIri02), projectIri, None)
      result2 should be(true)
    }

    "return false if the user is not allowed to create a resource (ProjectResourceCreateRestrictedPermission)" in {
      val projectIri                 = IMAGES_PROJECT_IRI
      val notAllowedResourceClassIri = s"$IMAGES_ONTOLOGY_IRI#person"

      val result = SharedTestDataADM.imagesReviewerUser.permissions
        .hasPermissionFor(ResourceCreateOperation(notAllowedResourceClassIri), projectIri, None)
      result should be(false)
    }
  }

  "querying the user's 'PermissionsProfileV1' with 'hasProjectAdminAllPermissionFor'" should {

    "return true if the user has the 'ProjectAdminAllPermission' (incunabula project admin user)" in {
      val projectIri = INCUNABULA_PROJECT_IRI
      val result     = SharedTestDataADM.incunabulaProjectAdminUser.permissions.hasProjectAdminAllPermissionFor(projectIri)

      result should be(true)
    }

    "return false if the user has the 'ProjectAdminAllPermission' (incunabula member user)" in {
      val projectIri = INCUNABULA_PROJECT_IRI
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
          requestingUser = SharedTestDataADM.imagesUser02
        )
      )
      assert(caught.getMessage === s"Invalid permission IRI $permissionIri is given.")
    }

    "not update permission group if invalid permission IRI given" in {
      val permissionIri = "invalid-permission-iri"
      val newGroupIri   = SharedTestDataADM.imagesReviewerGroup.id
      val caught = intercept[BadRequestException](
        PermissionChangeGroupRequestADM(
          permissionIri = permissionIri,
          changePermissionGroupRequest = ChangePermissionGroupApiRequestADM(newGroupIri),
          requestingUser = SharedTestDataADM.imagesUser02,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid permission IRI $permissionIri is given.")
    }

    "not update permission group if invalid group IRI given" in {
      val permissionIri = SharedPermissionsTestData.perm001_d1.iri
      val newGroupIri   = "invalid-group-iri"
      val caught = intercept[BadRequestException](
        PermissionChangeGroupRequestADM(
          permissionIri = permissionIri,
          changePermissionGroupRequest = ChangePermissionGroupApiRequestADM(newGroupIri),
          requestingUser = SharedTestDataADM.imagesUser02,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid IRI $newGroupIri is given.")
    }

    "not update hasPermissions set of a permission if invalid permission IRI given" in {
      val permissionIri  = "invalid-permission-iri"
      val hasPermissions = Set(PermissionADM.ProjectAdminAllPermission)
      val caught = intercept[BadRequestException](
        PermissionChangeHasPermissionsRequestADM(
          permissionIri = permissionIri,
          changePermissionHasPermissionsRequest = ChangePermissionHasPermissionsApiRequestADM(hasPermissions),
          requestingUser = SharedTestDataADM.imagesUser02,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid permission IRI $permissionIri is given.")
    }

    "not update hasPermissions set of a permission if invalid empty set given" in {
      val permissionIri  = SharedPermissionsTestData.perm001_d1.iri
      val hasPermissions = Set.empty[PermissionADM]
      val caught = intercept[BadRequestException](
        PermissionChangeHasPermissionsRequestADM(
          permissionIri = permissionIri,
          changePermissionHasPermissionsRequest = ChangePermissionHasPermissionsApiRequestADM(hasPermissions),
          requestingUser = SharedTestDataADM.imagesUser02,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"hasPermissions cannot be empty.")
    }

    "not update resource class of a doap if invalid permission IRI given" in {
      val permissionIri    = "invalid-permission-iri"
      val resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS
      val caught = intercept[BadRequestException](
        PermissionChangeResourceClassRequestADM(
          permissionIri = permissionIri,
          changePermissionResourceClassRequest = ChangePermissionResourceClassApiRequestADM(resourceClassIri),
          requestingUser = SharedTestDataADM.imagesUser02,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid permission IRI $permissionIri is given.")
    }

    "not update resource class of a doap if invalid resource class IRI is given" in {
      val permissionIri    = SharedPermissionsTestData.perm001_d1.iri
      val resourceClassIri = "invalid-iri"
      val caught = intercept[BadRequestException](
        PermissionChangeResourceClassRequestADM(
          permissionIri = permissionIri,
          changePermissionResourceClassRequest = ChangePermissionResourceClassApiRequestADM(resourceClassIri),
          requestingUser = SharedTestDataADM.imagesUser02,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid resource class IRI $resourceClassIri is given.")
    }

    "not update property of a doap if invalid permission IRI given" in {
      val permissionIri = "invalid-permission-iri"
      val propertyIri   = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY
      val caught = intercept[BadRequestException](
        PermissionChangePropertyRequestADM(
          permissionIri = permissionIri,
          changePermissionPropertyRequest = ChangePermissionPropertyApiRequestADM(propertyIri),
          requestingUser = SharedTestDataADM.imagesUser02,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid permission IRI $permissionIri is given.")
    }

    "not update property of a doap if invalid property IRI is given" in {
      val permissionIri = SharedPermissionsTestData.perm001_d1.iri
      val propertyIri   = "invalid-iri"
      val caught = intercept[BadRequestException](
        PermissionChangePropertyRequestADM(
          permissionIri = permissionIri,
          changePermissionPropertyRequest = ChangePermissionPropertyApiRequestADM(propertyIri),
          requestingUser = SharedTestDataADM.imagesUser02,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid property IRI $propertyIri is given.")
    }

    "return 'BadRequest' if the supplied permission IRI for PermissionDeleteRequestADM is not valid" in {
      val permissionIri = "invalid-permission-Iri"
      val caught = intercept[BadRequestException](
        PermissionDeleteRequestADM(
          permissionIri = permissionIri,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === s"Invalid permission IRI $permissionIri is given.")
    }
  }
}
