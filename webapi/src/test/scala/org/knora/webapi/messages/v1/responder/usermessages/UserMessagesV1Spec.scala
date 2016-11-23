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
import org.mindrot.jbcrypt.BCrypt
import org.scalatest.{Matchers, WordSpecLike}

/**
  * This spec is used to test subclasses of the [[UsersResponderRequestV1]] class.
  */
class UserMessagesV1Spec extends WordSpecLike with Matchers {

    val lang = SharedAdminTestData.rootUser.userData.lang
    val user_id = SharedAdminTestData.rootUser.userData.user_id
    val token = SharedAdminTestData.rootUser.userData.token
    val username = SharedAdminTestData.rootUser.userData.username
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
                permissionData = permissionData,
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
                permissionData = permissionData.ofType(PermissionDataType.SAFE),
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
