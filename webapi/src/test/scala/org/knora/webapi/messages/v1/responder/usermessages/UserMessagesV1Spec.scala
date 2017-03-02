/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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
import org.knora.webapi.messages.v1.responder.permissionmessages.{PermissionDataType, PermissionDataV1}
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder
import org.scalatest.{Matchers, WordSpecLike}

/**
  * This spec is used to test subclasses of the [[UsersResponderRequestV1]] class.
  */
class UserMessagesV1Spec extends WordSpecLike with Matchers {

    val lang = SharedAdminTestData.rootUser.userData.lang
    val user_id = SharedAdminTestData.rootUser.userData.user_id
    val token = SharedAdminTestData.rootUser.userData.token
    val firstname = SharedAdminTestData.rootUser.userData.firstname
    val lastname = SharedAdminTestData.rootUser.userData.lastname
    val email = SharedAdminTestData.rootUser.userData.email
    val password = SharedAdminTestData.rootUser.userData.password
    val groups = SharedAdminTestData.rootUser.groups
    val projects = SharedAdminTestData.rootUser.projects
    val permissionData = SharedAdminTestData.rootUser.permissionData
    val sessionId = SharedAdminTestData.rootUser.sessionId


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
                projects = projects,
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
                projects = projects,
                permissionData = permissionData.ofType(PermissionDataType.RESTRICTED),
                sessionId = sessionId
            )

            assert(rootUserProfileV1.ofType(UserProfileType.RESTRICTED) === rootUserProfileV1Safe)
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
                permissionData = PermissionDataV1(anonymousUser = false)
            )

            // test SCrypt
            assert(encoder.matches("123456", encoder.encode("123456")))

            // test UserProfileV1 BCrypt usage
            assert(up.passwordMatch("123456"))
        }

        "allow checking the password (2)" in {
            SharedAdminTestData.rootUser.passwordMatch("test") should equal(true)
        }
    }
}
