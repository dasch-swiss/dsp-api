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

import java.util.UUID

import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.{KnoraRequestV1, KnoraResponseV1}
import spray.httpx.SprayJsonSupport
import spray.json._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new user.
  *
  * @param username   the username of the user to be created.
  * @param givenName  the given name of the user to be created.
  * @param familyName the family name of the user to be created
  * @param email      the email of the user to be created.
  * @param password   the password of the user to be created.
  * @param lang       the default language of the user to be created.
  */
case class CreateUserApiRequestV1(username: String,
                                  givenName: String,
                                  familyName: String,
                                  email: String,
                                  password: String,
                                  lang: String,
                                  projects: Seq[IRI] = Vector.empty[IRI]) {

    def toJsValue = UserV1JsonProtocol.createUserApiRequestV1Format.write(this)

}


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
  * @param clean   a flag denoting if sensitive information (token, password) should be stripped
  */
case class UserProfileByIRIGetRequestV1(userIri: IRI,
                                        clean: Boolean = false) extends UsersResponderRequestV1

/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileV1]].
  *
  * @param username the username of the user to be queried.
  * @param clean    a flag denoting if sensitive information (token, password) should be stripped.
  */
case class UserProfileByUsernameGetRequestV1(username: String,
                                             clean: Boolean = false) extends UsersResponderRequestV1


/**
  * Requests the creation of a new user.
  *
  * @param newUserData  the [[NewUserDataV1]] information for creating the new user.
  * @param userProfile  the user profile of the user creating the new user.
  * @param apiRequestID the ID of the API request.
  */
case class UserCreateRequestV1(newUserData: NewUserDataV1, userProfile: UserProfileV1, apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Describes the answer to an user creating/modifying operation.
  *
  * @param userProfile the new user profile of the created/modified user.
  * @param userData    information about the user that made the request.
  * @param message     a message describing what went wrong if operation was only partially successful.
  */
case class UserOperationResponseV1(userProfile: UserProfileV1, userData: UserDataV1, message: Option[String] = None) extends KnoraResponseV1 {
    def toJsValue = UserV1JsonProtocol.userCreateResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents a user's profile.
  *
  * @param userData basic information about the user.
  * @param groups   the groups that the user belongs to.
  * @param projects the projects that the user belongs to.
  */
case class UserProfileV1(userData: UserDataV1, groups: Seq[IRI] = Nil, projects: Seq[IRI] = Nil) {

    /**
      * Check password using either SHA-1 or BCrypt. The BCrypt password always starts with '$2a$'
      *
      * @param password the password to check.
      * @return true if password matches and false if password doesn't match.
      */
    def passwordMatch(password: String): Boolean = {
        userData.hashedpassword.exists {
            hashedpassword => hashedpassword match {
                case hp if hp.startsWith("$2a$") => {
                    import org.mindrot.jbcrypt.BCrypt
                    BCrypt.checkpw(password, hp)
                }
                case hp => {
                    val md = java.security.MessageDigest.getInstance("SHA-1")
                    md.digest(password.getBytes("UTF-8")).map("%02x".format(_)).mkString.equals(hp)
                }
            }
        }
    }

    /**
      * Check password hashed using SHA-1.
      *
      * @param password the password to check.
      * @return true if password matches and false if password doesn't match.
      */
    private def passwordMatchSha1(password: String): Boolean = {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        userData.hashedpassword.exists { hashedPassword =>
            md.digest(password.getBytes("UTF-8")).map("%02x".format(_)).mkString.equals(hashedPassword)
        }
    }

    /**
      * Check password hashed using BCrypt.
      *
      * @param password the password to check
      * @return true if password matches and false if password doesn't match.
      */
    private def passwordMatchBCrypt(password: String): Boolean = {
        import org.mindrot.jbcrypt.BCrypt
        userData.hashedpassword.exists {
            hashedPassword => BCrypt.checkpw(password, hashedPassword)
        }
    }

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
            None // remove hashed password
        )

        UserProfileV1(newuserdata, groups, projects)
    }
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents basic information about a user.
  *
  * @param lang           the ISO 639-1 code of the user's preferred language.
  * @param user_id        the user's IRI.
  * @param token          the user's API token used as credentials.
  * @param username       the user's username.
  * @param firstname      the user's given name.
  * @param lastname       the user's surname.
  * @param email          the user's email address.
  * @param hashedpassword the user's hashed password.
  */
case class UserDataV1(lang: String,
                      user_id: Option[IRI] = None,
                      token: Option[String] = None,
                      username: Option[String] = None,
                      firstname: Option[String] = None,
                      lastname: Option[String] = None,
                      email: Option[String] = None,
                      hashedpassword: Option[String] = None)


/**
  * Represents basic information about the user which needs to be supplied during user creation.
  *
  * @param username   the new user's username. Needs to be unique on the server.
  * @param givenName  the new user's given name.
  * @param familyName the new user's family name.
  * @param email      the new users's email address. Needs to be unique on the server.
  * @param password   the new user's password in clear text.
  * @param lang       the ISO 639-1 code of the new user's preferred language.
  */
case class NewUserDataV1(username: String,
                         givenName: String,
                         familyName: String,
                         email: String,
                         password: String,
                         lang: String)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for formatting objects as JSON.
  */
object UserV1JsonProtocol extends DefaultJsonProtocol with NullOptions with SprayJsonSupport {

    implicit val userDataV1Format: JsonFormat[UserDataV1] = jsonFormat8(UserDataV1)
    implicit val userProfileV1Format: JsonFormat[UserProfileV1] = jsonFormat3(UserProfileV1)
    implicit val newUserDataV1Format: JsonFormat[NewUserDataV1] = jsonFormat6(NewUserDataV1)
    implicit val createUserApiRequestV1Format: RootJsonFormat[CreateUserApiRequestV1] = jsonFormat7(CreateUserApiRequestV1)
    implicit val userCreateResponseV1Format: RootJsonFormat[UserOperationResponseV1] = jsonFormat3(UserOperationResponseV1)
}
