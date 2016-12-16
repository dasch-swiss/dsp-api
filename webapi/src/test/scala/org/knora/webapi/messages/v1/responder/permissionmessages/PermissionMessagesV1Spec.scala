package org.knora.webapi.messages.v1.responder.permissionmessages

import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1
import org.scalatest.{Matchers, WordSpecLike}

/**
  * This spec is used to test subclasses of the [[UsersResponderRequestV1]] class.
  */
class PermissionMessagesV1Spec extends WordSpecLike with Matchers {

    "querying the user's PermissionProfileV1 with 'hasPermissionFor'" should {

        "return true if the user is allowed to create a resource (root user)" in {

            val projectIri = SharedAdminTestData.INCUNABULA_PROJECT_IRI
            val resourceClassIri = "http://www.knora.org/ontology/incunabula#book"

            val result = SharedAdminTestData.rootUser.permissionData.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri, None)

            result should be(true)
        }


        "return true if the user is allowed to create a resource (project user)" in {

            val projectIri = SharedAdminTestData.INCUNABULA_PROJECT_IRI
            val resourceClassIri = "http://www.knora.org/ontology/incunabula#book"

            val result = SharedAdminTestData.incunabulaUser.permissionData.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri, None)

            result should be(true)
        }

        "return false if the user is not allowed to create a resource" in {
            val projectIri = SharedAdminTestData.INCUNABULA_PROJECT_IRI
            val resourceClassIri = "http://www.knora.org/ontology/incunabula#book"

            val result = SharedAdminTestData.normalUser.permissionData.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri, None)

            result should be(false)
        }
    }
}