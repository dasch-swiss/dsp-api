/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.v1.responder.usermessages

import org.knora.webapi._
import org.mindrot.jbcrypt.BCrypt
import org.scalatest.{Matchers, WordSpecLike}

/**
  * This spec is used to test subclasses of the [[UsersResponderRequestV1]] class.
  */
class UserMessagesV1Spec extends WordSpecLike with Matchers {

    val lang = "de"
    val user_id = Some("http://data.knora.org/users/91e19f1e01")
    val token = Some("123456")
    val username = Some("root")
    val firstname = Some("Administrator")
    val lastname = Some("Admin")
    val email = Some("test@test.ch")
    val password = Some("123456")
    val projects = List[IRI]("http://data.knora.org/projects/77275339", "http://data.knora.org/projects/images")

    "The UserProfileV1 case class " should {
        "return a clean UserProfileV1 when requested " in {
            val rootUserProfileV1 = UserProfileV1(
                UserDataV1(
                    user_id = user_id,
                    username = username,
                    firstname = firstname,
                    lastname = lastname,
                    email = email,
                    hashedpassword = password,
                    token = token,
                    lang = lang
                ),
                Vector.empty[IRI],
                projects,
                Vector.empty[IRI],
                Vector.empty[IRI]
            )
            val rootUserProfileV1Clean = UserProfileV1(
                UserDataV1(
                    user_id = user_id,
                    username = username,
                    firstname = firstname,
                    lastname = lastname,
                    email = email,
                    hashedpassword = None,
                    token = None,
                    lang = lang
                    ),
                    Vector.empty[IRI],
                    projects,
                    Vector.empty[IRI],
                    Vector.empty[IRI]
                )

            assert(rootUserProfileV1.getCleanUserProfileV1 === rootUserProfileV1Clean)
        }
        "allow checking the password " in {
            val hp = BCrypt.hashpw("123456", BCrypt.gensalt())
            val up = UserProfileV1(
                UserDataV1(
                    hashedpassword = Some(hp),
                    lang = lang
                ))

            // test BCrypt
            assert(BCrypt.checkpw("123456", BCrypt.hashpw("123456", BCrypt.gensalt())))

            // test UserProfileV1 BCrypt usage
            assert(up.passwordMatch("123456"))
        }
    }
}
