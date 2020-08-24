/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.admin.responder.permissionsmessages

import java.util.UUID

import org.knora.webapi.exceptions.{BadRequestException, ForbiddenException}
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM._
import org.knora.webapi.sharedtestdata._
import org.knora.webapi.sharedtestdata.SharedTestDataV1._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

/**
  * This spec is used to test subclasses of the [[UsersResponderRequestV1]] class.
  */
class PermissionsMessagesADMSpec extends AnyWordSpecLike with Matchers {

    "Administrative Permission Get Requests" should {
        "return 'BadRequest' if the supplied project IRI for AdministrativePermissionsForProjectGetRequestADM is not valid" in {
            val caught = intercept[BadRequestException](
                AdministrativePermissionsForProjectGetRequestADM(
                    projectIri = "invalid-project-IRI",
                    requestingUser = SharedTestDataADM.imagesUser01,
                    apiRequestID = UUID.randomUUID()
                )
            )
            assert(caught.getMessage === "Invalid project IRI")
        }

        "return 'ForbiddenException' if the user requesting AdministrativePermissionsForProjectGetRequestADM is not SystemAdmin" in {
            val caught = intercept[ForbiddenException](
                AdministrativePermissionsForProjectGetRequestADM(
                    projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
                    requestingUser = SharedTestDataADM.imagesUser02,
                    apiRequestID = UUID.randomUUID()
                )
            )
            assert(caught.getMessage === "Administrative permission can only be queried by system and project admin.")
        }

        "return 'BadRequest' if the supplied permission IRI for AdministrativePermissionForIriGetRequestADM is not valid" in {
            val caught = intercept[BadRequestException](
                AdministrativePermissionForIriGetRequestADM(
                    administrativePermissionIri = "invalid-permission-IRI",
                    requestingUser = SharedTestDataADM.imagesUser01,
                    apiRequestID = UUID.randomUUID()
                )
            )
            assert(caught.getMessage === "Invalid permission IRI")
        }

        "return 'ForbiddenException' if the user requesting AdministrativePermissionForIriGetRequestADM is not SystemAdmin" in {
            val caught = intercept[ForbiddenException](
                AdministrativePermissionForIriGetRequestADM(
                    administrativePermissionIri = "http://rdfh.ch/permissions/permissionIRI",
                    requestingUser = SharedTestDataADM.imagesUser02,
                    apiRequestID = UUID.randomUUID()
                )
            )
            assert(caught.getMessage === "Administrative permission can only be queried by system and project admin.")
        }


        "return 'BadRequest' if the supplied project IRI for AdministrativePermissionForProjectGroupGetADM is not valid" in {
            val caught = intercept[BadRequestException](
                AdministrativePermissionForProjectGroupGetADM(
                    projectIri = "invalid-project-IRI",
                    groupIri = OntologyConstants.KnoraAdmin.ProjectMember,
                    requestingUser = SharedTestDataADM.imagesUser01
                )
            )
            assert(caught.getMessage === "Invalid project IRI")
        }

//        "return 'BadRequest' if the supplied group IRI for AdministrativePermissionForProjectGroupGetADM is not valid" in {
//            val caught = intercept[BadRequestException](
//                AdministrativePermissionForProjectGroupGetADM(
//                    projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
//                    groupIri = "invalid-group-iri",
//                    requestingUser = SharedTestDataADM.imagesUser01
//                )
//            )
//            assert(caught.getMessage === "Invalid group IRI")
//        }

        "return 'ForbiddenException' if the user requesting AdministrativePermissionForProjectGroupGetADM is not SystemAdmin" in {
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
            val caught = intercept[BadRequestException](
                AdministrativePermissionCreateRequestADM(
                    createRequest = CreateAdministrativePermissionAPIRequestADM(
                        forProject = "invalid-project-IRI",
                        forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
                        hasPermissions = Set(PermissionADM.ProjectAdminAllPermission)
                    ),
                    requestingUser = SharedTestDataADM.imagesUser01,
                    apiRequestID = UUID.randomUUID()
                )
            )
            assert(caught.getMessage === "Invalid project IRI")
        }

//        "return 'BadRequest' if the supplied group IRI for AdministrativePermissionCreateRequestADM is not valid" in {
//            val caught = intercept[BadRequestException](
//                AdministrativePermissionCreateRequestADM(
//                    createRequest = CreateAdministrativePermissionAPIRequestADM(
//                        forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
//                        forGroup = "invalid-group-IRI",
//                        hasPermissions = Set(PermissionADM.ProjectAdminAllPermission)
//                    ),
//                    requestingUser = SharedTestDataADM.imagesUser01,
//                    apiRequestID = UUID.randomUUID()
//                )
//            )
//            assert(caught.getMessage === "Invalid group IRI")
//        }

        "return 'BadRequest' if the no permissions supplied for AdministrativePermissionCreateRequestADM" in {
            val caught = intercept[BadRequestException](
                AdministrativePermissionCreateRequestADM(
                    createRequest = CreateAdministrativePermissionAPIRequestADM(
                        forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
                        forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
                        hasPermissions = Set.empty[PermissionADM]
                    ),
                    requestingUser = SharedTestDataADM.imagesUser01,
                    apiRequestID = UUID.randomUUID()
                )
            )
            assert(caught.getMessage === "Permissions needs to be supplied.")
        }

        "return 'ForbiddenException' if the user requesting AdministrativePermissionCreateRequestADM is not SystemAdmin" in {
            val caught = intercept[ForbiddenException](
                AdministrativePermissionCreateRequestADM(
                    createRequest = CreateAdministrativePermissionAPIRequestADM(
                        forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
                        forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
                        hasPermissions = Set(PermissionADM.ProjectAdminAllPermission)
                    ),
                    requestingUser = SharedTestDataADM.imagesReviewerUser,
                    apiRequestID = UUID.randomUUID()
                )
            )
            assert(caught.getMessage === "A new administrative permission can only be added by a system admin.")
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
        "return 'ForbiddenException' if the user requesting ObjectAccessPermissionsForResourceGetADM is not SystemAdmin" in {
            val caught = intercept[ForbiddenException](
                ObjectAccessPermissionsForResourceGetADM(
                    resourceIri = SharedTestDataADM.customResourceIRI,
                    requestingUser = SharedTestDataADM.anythingUser1
                )
            )
            assert(caught.getMessage === "Object access permissions can only be queried by system and project admin.")
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
        "return 'ForbiddenException' if the user requesting ObjectAccessPermissionsForValueGetADM is not SystemAdmin" in {
            val caught = intercept[ForbiddenException](
                ObjectAccessPermissionsForValueGetADM(
                    valueIri = SharedTestDataADM.customValueIRI,
                    requestingUser = SharedTestDataADM.anythingUser1
                )
            )
            assert(caught.getMessage === "Object access permissions can only be queried by system and project admin.")
        }
    }

    "Default Object Access Permission Get Requests" should {

        "return 'BadRequest' if the supplied project IRI for DefaultObjectAccessPermissionGetADM is not valid" in {
            val caught = intercept[BadRequestException](
                DefaultObjectAccessPermissionGetADM(
                    projectIri = "invalid-project-IRI",
                    groupIri = Some(OntologyConstants.KnoraAdmin.ProjectMember),
                    resourceClassIri = Some(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
                    propertyIri = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
                    requestingUser = SharedTestDataADM.imagesUser01
                )
            )
            assert(caught.getMessage === "Invalid project IRI")
        }

//        "return 'BadRequest' if the supplied optional group IRI for DefaultObjectAccessPermissionGetADM is not valid" in {
//            val caught = intercept[BadRequestException](
//                DefaultObjectAccessPermissionGetADM(
//                    projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
//                    groupIri = Some(SharedTestDataADM.imagesUser01.id),
//                    resourceClassIri = Some(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
//                    propertyIri = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
//                    requestingUser = SharedTestDataADM.imagesUser01
//                )
//            )
//            // user IRI is given instead of group IRI, exception should be thrown.
//            assert(caught.getMessage === "Invalid group IRI")
//        }

        "return 'BadRequest' if the supplied resourceClass IRI for DefaultObjectAccessPermissionGetADM is not valid" in {
            val caught = intercept[BadRequestException](
                DefaultObjectAccessPermissionGetADM(
                    projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
                    groupIri = Some(OntologyConstants.KnoraAdmin.ProjectMember),
                    resourceClassIri = Some(SharedTestDataADM.customResourceIRI),
                    propertyIri = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
                    requestingUser = SharedTestDataADM.imagesUser01
                )
            )
            // a resource IRI is given instead of a resource class IRI, exception should be thrown.
            assert(caught.getMessage === s"Invalid resource class IRI: ${SharedTestDataADM.customResourceIRI}")
        }

        "return 'BadRequest' if the supplied property IRI for DefaultObjectAccessPermissionGetADM is not valid" in {
            val caught = intercept[BadRequestException](
                DefaultObjectAccessPermissionGetADM(
                    projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
                    groupIri = Some(OntologyConstants.KnoraAdmin.ProjectMember),
                    resourceClassIri = Some(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
                    propertyIri = Some(SharedTestDataADM.customValueIRI),
                    requestingUser = SharedTestDataADM.imagesUser01
                )
            )
            // a value IRI is given instead of a property IRI, exception should be thrown.
            assert(caught.getMessage === s"Invalid property IRI: ${SharedTestDataADM.customValueIRI}")
        }

        "return 'BadRequest' if the supplied permission IRI for DefaultObjectAccessPermissionForIriGetRequestADM is not valid" in {
            val caught = intercept[BadRequestException](
                DefaultObjectAccessPermissionForIriGetRequestADM(
                    defaultObjectAccessPermissionIri = "invalid-permission-IRI",
                    requestingUser = SharedTestDataADM.imagesUser01,
                    apiRequestID = UUID.randomUUID()
                )
            )
            assert(caught.getMessage === "Invalid permission IRI")
        }

        "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionForIriGetRequestADM is not SystemAdmin" in {
            val caught = intercept[ForbiddenException](
                DefaultObjectAccessPermissionForIriGetRequestADM(
                    defaultObjectAccessPermissionIri = "http://rdfh.ch/permissions/permissionIRI",
                    requestingUser = SharedTestDataADM.imagesUser02,
                    apiRequestID = UUID.randomUUID()
                )
            )
            assert(caught.getMessage === "Default object access permissions can only be queried by system and project admin.")
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

        "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionsStringForResourceClassGetADM is not SystemAdmin" in {
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
            val caught = intercept[BadRequestException](
                DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = "invalid-project-IRI",
                    resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
                    targetUser = SharedTestDataADM.imagesUser02,
                    requestingUser = SharedTestDataADM.imagesUser01
                )
            )
            assert(caught.getMessage === "Invalid project IRI")
        }

//        "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionsStringForResourceClassGetADM is not in the same project as the target user" in {
//            val caught = intercept[ForbiddenException](
//                DefaultObjectAccessPermissionsStringForResourceClassGetADM(
//                    projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
//                    resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
//                    targetUser = SharedTestDataADM.anythingUser1,
//                    requestingUser = SharedTestDataADM.imagesUser01
//                )
//            )
//            assert(caught.getMessage === "Target user is not a member of the same project as the requesting user.")
//        }

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

        "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionsStringForPropertyGetADM is not SystemAdmin" in {
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

        "return 'BadRequest' if the supplied project IRI DefaultObjectAccessPermissionsStringForPropertyGetADM is not valid" in {
            val caught = intercept[BadRequestException](
                DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = "invalid-project-IRI",
                    resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
                    propertyIri = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY,
                    targetUser = SharedTestDataADM.imagesUser02,
                    requestingUser = SharedTestDataADM.imagesUser01
                )
            )
            assert(caught.getMessage === "Invalid project IRI")
        }

//        "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionsStringForPropertyGetADM is not in the same project as the target user" in {
//            val caught = intercept[ForbiddenException](
//                DefaultObjectAccessPermissionsStringForPropertyGetADM(
//                    projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
//                    resourceClassIri = SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
//                    propertyIri = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY,
//                    targetUser = SharedTestDataADM.anythingUser1,
//                    requestingUser = SharedTestDataADM.imagesUser01
//                )
//            )
//            assert(caught.getMessage === "Target user is not a member of the same project as the requesting user.")
//        }

        "return 'BadRequest' if the user requesting DefaultObjectAccessPermissionsStringForPropertyGetADM is not in the same project as the target user" in {
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
    }

    "querying the user's 'PermissionsDataADM' with 'hasPermissionFor'" should {
        "return true if the user is allowed to create a resource (root user)" in {

            val projectIri = INCUNABULA_PROJECT_IRI
            val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

            val result = SharedTestDataADM.rootUser.permissions.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri, None)

            result should be(true)
        }


        "return true if the user is allowed to create a resource (project admin user)" in {

            val projectIri = INCUNABULA_PROJECT_IRI
            val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

            val result = SharedTestDataADM.incunabulaProjectAdminUser.permissions.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri, None)

            result should be(true)
        }

        "return true if the user is allowed to create a resource (project member user)" in {

            val projectIri = INCUNABULA_PROJECT_IRI
            val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

            val result = SharedTestDataADM.incunabulaMemberUser.permissions.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri, None)

            result should be(true)
        }


        "return false if the user is not allowed to create a resource" in {
            val projectIri = INCUNABULA_PROJECT_IRI
            val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

            val result = SharedTestDataADM.normalUser.permissions.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri, None)

            result should be(false)
        }

        "return true if the user is allowed to create a resource (ProjectResourceCreateRestrictedPermission)" in {
            val projectIri = IMAGES_PROJECT_IRI
            val allowedResourceClassIri01 = s"$IMAGES_ONTOLOGY_IRI#bild"
            val allowedResourceClassIri02 = s"$IMAGES_ONTOLOGY_IRI#bildformat"
            val notAllowedResourceClassIri = s"$IMAGES_ONTOLOGY_IRI#person"

            val result1 = SharedTestDataADM.imagesReviewerUser.permissions.hasPermissionFor(ResourceCreateOperation(allowedResourceClassIri01), projectIri, None)
            result1 should be(true)

            val result2 = SharedTestDataADM.imagesReviewerUser.permissions.hasPermissionFor(ResourceCreateOperation(allowedResourceClassIri02), projectIri, None)
            result2 should be(true)
        }

        "return false if the user is not allowed to create a resource (ProjectResourceCreateRestrictedPermission)" in {
            val projectIri = IMAGES_PROJECT_IRI
            val notAllowedResourceClassIri = s"$IMAGES_ONTOLOGY_IRI#person"

            val result = SharedTestDataADM.imagesReviewerUser.permissions.hasPermissionFor(ResourceCreateOperation(notAllowedResourceClassIri), projectIri, None)
            result should be(false)
        }
    }

    "querying the user's 'PermissionsProfileV1' with 'hasProjectAdminAllPermissionFor'" should {

        "return true if the user has the 'ProjectAdminAllPermission' (incunabula project admin user)" in {
            val projectIri = INCUNABULA_PROJECT_IRI
            val result = SharedTestDataADM.incunabulaProjectAdminUser.permissions.hasProjectAdminAllPermissionFor(projectIri)

            result should be(true)
        }

        "return false if the user has the 'ProjectAdminAllPermission' (incunabula member user)" in {
            val projectIri = INCUNABULA_PROJECT_IRI
            val result = SharedTestDataADM.incunabulaMemberUser.permissions.hasProjectAdminAllPermissionFor(projectIri)

            result should be(false)
        }
    }
}