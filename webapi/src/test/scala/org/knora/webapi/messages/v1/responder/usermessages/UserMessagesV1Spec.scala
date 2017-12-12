/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.v1.responder.usermessages

import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionDataType, PermissionsDataADM}
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionDataType
import org.scalatest.{Matchers, WordSpecLike}
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder

/**
  * This spec is used to test subclasses of the [[UsersResponderRequestV1]] class.
  */
class UserMessagesV1Spec extends WordSpecLike with Matchers {

    private val lang = SharedTestDataV1.rootUser.userData.lang
    private val user_id = SharedTestDataV1.rootUser.userData.user_id
    private val token = SharedTestDataV1.rootUser.userData.token
    private val firstname = SharedTestDataV1.rootUser.userData.firstname
    private val lastname = SharedTestDataV1.rootUser.userData.lastname
    private val email = SharedTestDataV1.rootUser.userData.email
    private val password = SharedTestDataV1.rootUser.userData.password
    private val groups = SharedTestDataV1.rootUser.groups
    private val projects_info = SharedTestDataV1.rootUser.projects_info
    private val permissionData = SharedTestDataV1.rootUser.permissionData
    private val sessionId = SharedTestDataV1.rootUser.sessionId


    "The UserProfileV1 case class " should {
        "return a safe UserProfileV1 when requested " in {
            val rootUserProfileV1 = UserProfileV1(
                UserDataV1(
                    user_id = user_id,
                    email = email,
                    firstname = firstname,
                    lastname = lastname,
                    password = password,
                    token = token,
                    lang = lang
                ),
                groups = groups,
                projects_info = projects_info,
                permissionData = permissionData,
                sessionId = sessionId

            )
            val rootUserProfileV1Safe = UserProfileV1(
                UserDataV1(
                    user_id = user_id,
                    email = email,
                    firstname = firstname,
                    lastname = lastname,
                    password = None,
                    token = None,
                    lang = lang
                ),
                groups = groups,
                projects_info = projects_info,
                permissionData = permissionData.ofType(PermissionDataType.RESTRICTED),
                sessionId = sessionId
            )

            assert(rootUserProfileV1.ofType(UserProfileTypeV1.RESTRICTED) === rootUserProfileV1Safe)
        }
        "allow checking the password (1)" in {
            //hashedPassword =  encoder.encode(createRequest.password);
            val encoder = new SCryptPasswordEncoder
            val hp = encoder.encode("123456")
            val up = UserProfileV1(
                userData = UserDataV1(
                    password = Some(hp),
                    lang = lang
                ),
                permissionData = PermissionsDataADM(anonymousUser = false)
            )

            // test SCrypt
            assert(encoder.matches("123456", encoder.encode("123456")))

            // test UserProfileV1 BCrypt usage
            assert(up.passwordMatch("123456"))
        }

        "allow checking the password (2)" in {
            SharedTestDataV1.rootUser.passwordMatch("test") should equal(true)
        }
    }
}
