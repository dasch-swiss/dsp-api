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
import org.knora.webapi.messages.v1.responder.permissionmessages.{PermissionProfileType, PermissionProfileV1}
import org.mindrot.jbcrypt.BCrypt
import org.scalatest.{Matchers, WordSpecLike}

/**
  * This spec is used to test subclasses of the [[UsersResponderRequestV1]] class.
  */
class UserMessagesV1Spec extends WordSpecLike with Matchers {

    val lang = SharedTestData.rootUserProfileV1.userData.lang
    val user_id = SharedTestData.rootUserProfileV1.userData.user_id
    val token = SharedTestData.rootUserProfileV1.userData.token
    val username = SharedTestData.rootUserProfileV1.userData.username
    val firstname = SharedTestData.rootUserProfileV1.userData.firstname
    val lastname = SharedTestData.rootUserProfileV1.userData.lastname
    val email = SharedTestData.rootUserProfileV1.userData.email
    val password = SharedTestData.rootUserProfileV1.userData.password
    val groups = SharedTestData.rootUserProfileV1.groups
    val projects = SharedTestData.rootUserProfileV1.projects
    val permissionProfile = SharedTestData.rootUserProfileV1.permissionProfile
    val sessionId = SharedTestData.rootUserProfileV1.sessionId


    "The UserProfileV1 case class " should {
        "return a safe UserProfileV1 when requested " in {
            val rootUserProfileV1 = UserProfileV1(
                UserDataV1(
                    user_id = user_id,
                    username = username,
                    firstname = firstname,
                    lastname = lastname,
                    email = email,
                    password = password,
                    token = token,
                    lang = lang
                ),
                groups = groups,
                projects = projects,
                permissionProfile = permissionProfile,
                sessionId = sessionId

            )
            val rootUserProfileV1Safe = UserProfileV1(
                UserDataV1(
                    user_id = user_id,
                    username = username,
                    firstname = firstname,
                    lastname = lastname,
                    email = email,
                    password = None,
                    token = None,
                    lang = lang
                    ),
                groups = groups,
                projects = projects,
                permissionProfile = permissionProfile.ofType(PermissionProfileType.SAFE),
                sessionId = sessionId
            )

            assert(rootUserProfileV1.ofType(UserProfileType.SAFE) === rootUserProfileV1Safe)
        }
        "allow checking the password " in {
            val hp = BCrypt.hashpw("123456", BCrypt.gensalt())
            val up = UserProfileV1(
                UserDataV1(
                    password = Some(hp),
                    lang = lang
                ))

            // test BCrypt
            assert(BCrypt.checkpw("123456", BCrypt.hashpw("123456", BCrypt.gensalt())))

            // test UserProfileV1 BCrypt usage
            assert(up.passwordMatch("123456"))
        }
    }
}
