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

package org.knora.webapi.messages.v1respondermessages.usermessages

import org.knora.webapi._
import org.scalatest.{Matchers, WordSpecLike}

/**
  * This spec is used to test subclasses of the [[UsersResponderRequestV1]] class.
  */
class UserMessagesV1Spec extends WordSpecLike with Matchers {


    "The UserProfileV1 case class " should {
        "return a clean UserProfileV1 when requested " in {

            val lang = "de"
            val user_id = Some("http://data.knora.org/users/91e19f1e01")
            val token = Some("123456")
            val username = Some("root")
            val firstname = Some("Administrator")
            val lastname = Some("Admin")
            val email = Some("test@test.ch")
            val password = Some("123456")
            val projects = List[IRI]("http://data.knora.org/projects/77275339", "http://data.knora.org/projects/images")

            val rootUserProfileV1 = UserProfileV1(UserDataV1(lang, user_id, token, username, firstname, lastname, email, password), Vector.empty[IRI], projects)
            val rootUserProfileV1Clean = UserProfileV1(UserDataV1(lang, user_id, None, username, firstname, lastname, email, None), Vector.empty[IRI], projects)

            assert(rootUserProfileV1.getCleanUserProfileV1 === rootUserProfileV1Clean)
        }
    }
}
