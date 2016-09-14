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
import org.knora.webapi.messages.v1.responder.KnoraRequestV1
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoV1, ProjectV1JsonProtocol}
import org.mindrot.jbcrypt.BCrypt
import spray.json._


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing message that can be sent to `UsersResponderV1`.
  */
sealed trait UsersResponderRequestV1 extends KnoraRequestV1

/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileV1]].
  *
  * @param userIri the IRI of the user to be queried.
  */
case class UserProfileGetRequestV1(userIri: IRI) extends UsersResponderRequestV1

/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileV1]].
  *
  * @param username the username of the user to be queried.
  */
case class UserProfileByUsernameGetRequestV1(username: String) extends UsersResponderRequestV1


/**
  * Represents a user's profile.
  *
  * @param userData     basic information about the user.
  * @param groups       the groups that the user belongs to.
  * @param projects     the projects that the user belongs to.
  * @param isSystemUser `true` if this [[UserProfileV1]] represents the Knora API server itself.
  */
case class UserProfileV1(userData: UserDataV1, groups: Seq[IRI] = Nil, projects: Seq[IRI] = Nil, isSystemUser: Boolean = false) {
    def passwordMatch(password: String): Boolean = {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        userData.password.exists { hp =>
            md.digest(password.getBytes("UTF-8")).map("%02x".format(_)).mkString.equals(hp)
        }
    }

    def passwordMatchBCrypt(password: String): Boolean = userData.password.exists(hp => BCrypt.checkpw(password, hp))

    /**
      * Creating a [[UserProfileV1]] with sensitive information stripped.
      *
      * @return a [[UserProfileV1]]
      */
    def getCleanUserProfileV1: UserProfileV1 = {

        val olduserdata = userData
        val newuserdata = UserDataV1(
            olduserdata.lang,
            olduserdata.user_id,
            None, // remove token
            olduserdata.username,
            olduserdata.firstname,
            olduserdata.lastname,
            olduserdata.email,
            None, // remove password
            olduserdata.active_project,
            olduserdata.projects,
            olduserdata.projects_info
        )

        UserProfileV1(newuserdata, groups, projects)
    }
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents basic information about a user.
  *
  * @param lang      The ISO 639-1 code of the user's preferred language.
  * @param user_id   The user's IRI.
  * @param token     TODO: document this
  * @param username  The user's username.
  * @param firstname The user's given name.
  * @param lastname  The user's surname.
  * @param email     The user's email address.
  * @param password  The user's hashed password.
  * @param active_project
  * @param projects
  * @param projects_info
  */
case class UserDataV1(lang: String,
                      user_id: Option[IRI] = None,
                      token: Option[String] = None,
                      username: Option[String] = None,
                      firstname: Option[String] = None,
                      lastname: Option[String] = None,
                      email: Option[String] = None,
                      password: Option[String] = None,
                      active_project: Option[IRI] = None,
                      projects: Option[Seq[IRI]] = None, // TODO: we do not need an option here as the list could simply be empty.
                      projects_info: Seq[ProjectInfoV1] = Vector.empty[ProjectInfoV1]) {

    def toJsValue = UserDataV1JsonProtocol.userDataV1Format.write(this)

}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for formatting [[UserDataV1]] objects as JSON.
  */


object UserDataV1JsonProtocol extends DefaultJsonProtocol with NullOptions {

    import ProjectV1JsonProtocol.projectInfoV1Format

    implicit val userDataV1Format: JsonFormat[UserDataV1] = jsonFormat11(UserDataV1)
}
