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

import org.knora.webapi
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.{KnoraRequestV1, KnoraResponseV1}
import spray.httpx.SprayJsonSupport
import spray.json._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new user.
  *
  * @param username      the username of the user to be created.
  * @param givenName     the given name of the user to be created.
  * @param familyName    the family name of the user to be created
  * @param email         the email of the user to be created.
  * @param password      the password of the user to be created.
  * @param isActiveUser  the status of the user to be created.
  * @param isSystemAdmin the system admin status of the user to be created.
  * @param lang          the default language of the user to be created.
  */
case class CreateUserApiRequestV1(username: String,
                                  givenName: String,
                                  familyName: String,
                                  email: String,
                                  password: String,
                                  isActiveUser: Boolean,
                                  isSystemAdmin: Boolean,
                                  lang: String) {

    def toJsValue = UserV1JsonProtocol.createUserApiRequestV1Format.write(this)

}

/**
  * Represents an API request payload that asks the Knora API server to update an existing user.
  *
  * @param username      the new username of the user to be updated.
  * @param givenName     the new given name of the user to be updated.
  * @param familyName    the new family name of the user to be updated.
  * @param email         the new email address of the user to be updated.
  * @param password      the new password of the user to be updated.
  * @param isSystemAdmin the new system admin status of the user to be updated.
  * @param lang          the new default language of the user to be updated.
  */
case class UpdateUserApiRequestV1(username: Option[String],
                                  givenName: Option[String],
                                  familyName: Option[String],
                                  email: Option[String],
                                  password: Option[String],
                                  isActiveUser: Option[Boolean],
                                  isSystemAdmin: Option[Boolean],
                                  lang: Option[String]) {

    def toJsValue = UserV1JsonProtocol.updateUserApiRequestV1Format.write(this)
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
case class UserCreateRequestV1(newUserData: NewUserDataV1,
                               userProfile: UserProfileV1,
                               apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Request updating of an existing user.
  *
  * @param userIri the IRI of the user to be updated.
  * @param updatedUserData a [[UpdatedUserDataV1]] containing only information that needs to be updated.
  * @param userProfile the user profile of the user requesting the update.
  * @param apiRequestID the ID of the API request.
  */
case class UserUpdateRequestV1(userIri: webapi.IRI,
                               updatedUserData: UpdatedUserDataV1,
                               userProfile: UserProfileV1,
                               apiRequestID: UUID) extends UsersResponderRequestV1

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
case class UserProfileV1(userData: UserDataV1,
                         groups: Seq[IRI] = Nil,
                         projects: Seq[IRI] = Nil,
                         isGroupAdminFor: Seq[IRI] = Nil,
                         isProjectAdminFor: Seq[IRI] = Nil) {

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
            user_id = olduserdata.user_id,
            username = olduserdata.username,
            firstname = olduserdata.firstname,
            lastname = olduserdata.lastname,
            email = olduserdata.email,
            hashedpassword = None, // remove hashed password
            token = None, // remove token
            isActiveUser = olduserdata.isActiveUser,
            isSystemAdmin = None, // remove system admin status
            lang = olduserdata.lang
        )

        UserProfileV1(
            userData = newuserdata,
            groups = groups,
            projects = projects,
            isGroupAdminFor = Nil, // remove group admin information
            isProjectAdminFor = Nil // remove project admin information
        )
    }
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents basic information about a user.
  *
  * @param user_id        the user's IRI.
  * @param username       the user's username.
  * @param firstname      the user's given name.
  * @param lastname       the user's surname.
  * @param email          the user's email address.
  * @param hashedpassword the user's hashed password.
  * @param token          the user's API token used as credentials.
  * @param isActiveUser   the user's status.
  * @param isSystemAdmin  the user's system admin status.
  * @param lang           the ISO 639-1 code of the user's preferred language.
  */
case class UserDataV1(user_id: Option[IRI] = None,
                      username: Option[String] = None,
                      firstname: Option[String] = None,
                      lastname: Option[String] = None,
                      email: Option[String] = None,
                      hashedpassword: Option[String] = None,
                      token: Option[String] = None,
                      isActiveUser: Option[Boolean] = None,
                      isSystemAdmin: Option[Boolean] = None,
                      lang: String)


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
                         isSystemAdmin: Boolean,
                         lang: String)

/**
  * Represents information about the user that need to be updated.
  *
  * @param username the new username of the user to be updated.
  * @param givenName the new given name of the user to be updated.
  * @param familyName the new family name of the user to be updated.
  * @param email the new email address of the user to be updated.
  * @param password the new password of the user to be updated.
  * @param isActiveUser the new status of the user to be updated.
  * @param isSystemAdmin the new system admin status of the user to be
  * @param lang the new default language of the user to be updated.
  */
case class UpdatedUserDataV1(username: Option[String] = None,
                             givenName: Option[String] = None,
                             familyName: Option[String] = None,
                             email: Option[String] = None,
                             password: Option[String] = None,
                             isActiveUser: Option[Boolean] = None,
                             isSystemAdmin: Option[Boolean] = None,
                             lang: Option[String] = None)
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for formatting objects as JSON.
  */
object UserV1JsonProtocol extends DefaultJsonProtocol with NullOptions with SprayJsonSupport {

    implicit val userDataV1Format: JsonFormat[UserDataV1] = jsonFormat10(UserDataV1)
    implicit val userProfileV1Format: JsonFormat[UserProfileV1] = jsonFormat5(UserProfileV1)
    implicit val newUserDataV1Format: JsonFormat[NewUserDataV1] = jsonFormat7(NewUserDataV1)
    implicit val createUserApiRequestV1Format: RootJsonFormat[CreateUserApiRequestV1] = jsonFormat8(CreateUserApiRequestV1)
    implicit val updateUserApiRequestV1Format: RootJsonFormat[UpdateUserApiRequestV1] = jsonFormat8(UpdateUserApiRequestV1)
    implicit val userCreateResponseV1Format: RootJsonFormat[UserOperationResponseV1] = jsonFormat3(UserOperationResponseV1)
}
