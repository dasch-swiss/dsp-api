package org.knora.webapi.messages.v1.responder.permissionmessages

import org.knora.webapi.SharedOntologyTestDataADM._
import org.knora.webapi.SharedTestDataV1._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages.ResourceCreateOperation
import org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1
import org.scalatest.{Matchers, WordSpecLike}

/**
  * This spec is used to test subclasses of the [[UsersResponderRequestV1]] class.
  */
class PermissionMessagesV1Spec extends WordSpecLike with Matchers {

    "querying the user's 'PermissionProfileV1' with 'hasPermissionFor'" should {

        "return true if the user is allowed to create a resource (root user)" in {

            val projectIri = INCUNABULA_PROJECT_IRI
            val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

            val result = SharedTestDataV1.rootUser.permissionData.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri, None)

            result should be(true)
        }


        "return true if the user is allowed to create a resource (project admin user)" in {

            val projectIri = INCUNABULA_PROJECT_IRI
            val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

            val result = SharedTestDataV1.incunabulaProjectAdminUser.permissionData.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri, None)

            result should be(true)
        }

        "return true if the user is allowed to create a resource (project member user)" in {

            val projectIri = INCUNABULA_PROJECT_IRI
            val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

            val result = SharedTestDataV1.incunabulaMemberUser.permissionData.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri, None)

            result should be(true)
        }


        "return false if the user is not allowed to create a resource" in {
            val projectIri = INCUNABULA_PROJECT_IRI
            val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

            val result = SharedTestDataV1.normalUser.permissionData.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri, None)

            result should be(false)
        }

        "return true if the user is allowed to create a resource (ProjectResourceCreateRestrictedPermission)" in {
            val projectIri = IMAGES_PROJECT_IRI
            val allowedResourceClassIri01 = s"$IMAGES_ONTOLOGY_IRI#bild"
            val allowedResourceClassIri02 = s"$IMAGES_ONTOLOGY_IRI#bildformat"
            val notAllowedResourceClassIri = s"$IMAGES_ONTOLOGY_IRI#person"

            val result1 = SharedTestDataV1.imagesReviewerUser.permissionData.hasPermissionFor(ResourceCreateOperation(allowedResourceClassIri01), projectIri, None)
            result1 should be(true)

            val result2 = SharedTestDataV1.imagesReviewerUser.permissionData.hasPermissionFor(ResourceCreateOperation(allowedResourceClassIri02), projectIri, None)
            result2 should be(true)
        }

        "return false if the user is not allowed to create a resource (ProjectResourceCreateRestrictedPermission)" in {
            val projectIri = IMAGES_PROJECT_IRI
            val notAllowedResourceClassIri = s"$IMAGES_ONTOLOGY_IRI#person"

            val result = SharedTestDataV1.imagesReviewerUser.permissionData.hasPermissionFor(ResourceCreateOperation(notAllowedResourceClassIri), projectIri, None)
            result should be(false)
        }
    }

    "querying the user's 'PermissionsProfileV1' with 'hasProjectAdminAllPermissionFor'" should {

        "return true if the user has the 'ProjectAdminAllPermission' (incunabula project admin user)" in {
            val projectIri = INCUNABULA_PROJECT_IRI
            val result = SharedTestDataV1.incunabulaProjectAdminUser.permissionData.hasProjectAdminAllPermissionFor(projectIri)

            result should be(true)
        }

        "return false if the user has the 'ProjectAdminAllPermission' (incunabula member user)" in {
            val projectIri = INCUNABULA_PROJECT_IRI
            val result = SharedTestDataV1.incunabulaMemberUser.permissionData.hasProjectAdminAllPermissionFor(projectIri)

            result should be(false)
        }
    }
}