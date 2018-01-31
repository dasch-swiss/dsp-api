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

package org.knora.webapi.messages.admin.responder.usersmessages

import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionDataType, PermissionsDataADM}
import org.scalatest.{Matchers, WordSpecLike}
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder

/**
  * This spec is used to test subclasses of the [[org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1]] class.
  */
class UsersMessagesADMSpec extends WordSpecLike with Matchers {

    private val id = SharedTestDataADM.rootUser.id
    private val email = SharedTestDataADM.rootUser.email
    private val password = SharedTestDataADM.rootUser.password
    private val token = SharedTestDataADM.rootUser.token
    private val givenName = SharedTestDataADM.rootUser.givenName
    private val familyName = SharedTestDataADM.rootUser.familyName
    private val status = SharedTestDataADM.rootUser.status
    private val lang = SharedTestDataADM.rootUser.lang
    private val groups = SharedTestDataADM.rootUser.groups
    private val projects = SharedTestDataADM.rootUser.projects
    private val sessionId = SharedTestDataADM.rootUser.sessionId
    private val permissions = SharedTestDataADM.rootUser.permissions

    "The UserADM case class " should {
        "return a RESTRICTED UserADM when requested " in {
            val rootUser = UserADM(
                id = id,
                email = email,
                password = password,
                token = token,
                givenName = givenName,
                familyName = familyName,
                status = status,
                lang = lang,
                groups = groups,
                projects = projects,
                sessionId = sessionId,
                permissions = permissions
            )
            val rootUserRestricted = UserADM(
                id = id,
                email = email,
                password = None,
                token = None,
                givenName = givenName,
                familyName = familyName,
                status = status,
                lang = lang,
                groups = groups,
                projects = projects,
                sessionId = sessionId,
                permissions = permissions.ofType(PermissionDataType.RESTRICTED)
            )

            assert(rootUser.ofType(UserInformationTypeADM.RESTRICTED) === rootUserRestricted)
        }
        "allow checking the password (1)" in {
            //hashedPassword =  encoder.encode(createRequest.password);
            val encoder = new SCryptPasswordEncoder
            val hp = encoder.encode("123456")
            val up = UserADM(
                id = "something",
                email = "something",
                password = Some(hp),
                token = None,
                givenName = "something",
                familyName = "something",
                status = status,
                lang = lang,
                groups = groups,
                projects = projects,
                sessionId = sessionId,
                permissions = PermissionsDataADM()
            )

            // test SCrypt
            assert(encoder.matches("123456", encoder.encode("123456")))

            // test UserProfileV1 BCrypt usage
            assert(up.passwordMatch("123456"))
        }

        "allow checking the password (2)" in {
            SharedTestDataADM.rootUser.passwordMatch("test") should equal(true)
        }
    }
}
