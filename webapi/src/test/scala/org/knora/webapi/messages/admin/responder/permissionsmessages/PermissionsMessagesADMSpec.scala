/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import org.knora.webapi.SharedOntologyTestDataADM._
import org.knora.webapi.SharedTestDataV1._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1
import org.scalatest.{Matchers, WordSpecLike}

/**
  * This spec is used to test subclasses of the [[UsersResponderRequestV1]] class.
  */
class PermissionsMessagesADMSpec extends WordSpecLike with Matchers {

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