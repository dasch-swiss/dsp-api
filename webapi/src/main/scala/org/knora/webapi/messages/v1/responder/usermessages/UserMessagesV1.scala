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

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.permissionmessages.{PermissionDataV1, PermissionV1JsonProtocol}
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectV1JsonProtocol
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileType.UserProfileType
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.util.MessageUtil
import spray.json._


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new user.
  *
  * @param email       the email of the user to be created (unique).
  * @param givenName   the given name of the user to be created.
  * @param familyName  the family name of the user to be created
  * @param password    the password of the user to be created.
  * @param status      the status of the user to be created (active = true, inactive = false) (default = true).
  * @param lang        the default language of the user to be created (default = "en").
  * @param systemAdmin the system admin membership (default = false).
  */
case class CreateUserApiRequestV1(email: String,
                                  givenName: String,
                                  familyName: String,
                                  password: String,
                                  status: Boolean = true,
                                  lang: String = "en",
                                  systemAdmin: Boolean = false) {

    def toJsValue = UserV1JsonProtocol.createUserApiRequestV1Format.write(this)
}

/**
  * Represents an API request payload that asks the Knora API server to update an existing user.
  *
  * @param email       the new email address. Needs to be unique on the server.
  * @param givenName   the new given name.
  * @param familyName  the new family name.
  * @param password    the new password.
  * @param status      the new status.
  * @param lang        the new ISO 639-1 code of the new preferred language.
  * @param systemAdmin the new system admin membership status.
  */
case class UpdateUserApiRequestV1(email: Option[String] = None,
                                  givenName: Option[String] = None,
                                  familyName: Option[String] = None,
                                  password: Option[String] = None,
                                  status: Option[Boolean] = None,
                                  lang: Option[String] = None,
                                  systemAdmin: Option[Boolean] = None) {

    def toJsValue = UserV1JsonProtocol.updateUserApiRequestV1Format.write(this)
}


case class ChangeUserPasswordApiRequestV1(oldPassword: String,
                                          newPassword: String) {

    def toJsValue = UserV1JsonProtocol.changeUserPasswordApiRequestV1Format.write(this)
}

case class ChangeUserStatusApiRequestV1(newStatus: Boolean) {

    def toJsValue = UserV1JsonProtocol.changeUserStatusApiRequestV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing message that can be sent to `UsersResponderV1`.
  */
sealed trait UsersResponderRequestV1 extends KnoraRequestV1

/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileResponseV1]].
  *
  * @param userIri         the IRI of the user to be queried.
  * @param userProfileType the extent of the information returned.
  */
case class UserProfileByIRIGetRequestV1(userIri: IRI,
                                        userProfileType: UserProfileType,
                                        userProfile: UserProfileV1) extends UsersResponderRequestV1


/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileV1]].
  *
  * @param userIri         the IRI of the user to be queried.
  * @param userProfileType the extent of the information returned.
  */
case class UserProfileByIRIGetV1(userIri: IRI,
                                 userProfileType: UserProfileType) extends UsersResponderRequestV1

/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileResponseV1]].
  *
  * @param email           the email of the user to be queried.
  * @param userProfileType the extent of the information returned.
  * @param userProfile     the requesting user's profile.
  */
case class UserProfileByEmailGetRequestV1(email: String,
                                          userProfileType: UserProfileType,
                                          userProfile: UserProfileV1) extends UsersResponderRequestV1


/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileV1]].
  *
  * @param email           the email of the user to be queried.
  * @param userProfileType the extent of the information returned.
  */
case class UserProfileByEmailGetV1(email: String,
                                   userProfileType: UserProfileType) extends UsersResponderRequestV1

/**
  * Requests the creation of a new user.
  *
  * @param createRequest the [[CreateUserApiRequestV1]] information used for creating the new user.
  * @param userProfile   the user profile of the user creating the new user.
  * @param apiRequestID  the ID of the API request.
  */
case class UserCreateRequestV1(createRequest: CreateUserApiRequestV1,
                               userProfile: UserProfileV1,
                               apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Request updating of an existing user.
  *
  * @param userIri       the IRI of the user to be updated.
  * @param updateRequest the data which needs to be update.
  * @param userProfile   the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserUpdateRequestV1(userIri: IRI,
                               updateRequest: UpdateUserApiRequestV1,
                               userProfile: UserProfileV1,
                               apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Request updating the users password.
  *
  * @param userIri               the IRI of the user to be updated.
  * @param changePasswordRequest the [[ChangeUserPasswordApiRequestV1]] object containing the old and new password.
  * @param userProfile           the user profile of the user requesting the update.
  * @param apiRequestID          the ID of the API request.
  */
case class UserChangePasswordRequestV1(userIri: IRI,
                                       changePasswordRequest: ChangeUserPasswordApiRequestV1,
                                       userProfile: UserProfileV1,
                                       apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Request updating the users status ('knora-base:isActiveUser' property)
  *
  * @param userIri             the IRI of the user to be updated.
  * @param changeStatusRequest the [[ChangeUserStatusApiRequestV1]] containing the new status (true / false).
  * @param userProfile         the user profile of the user requesting the update.
  * @param apiRequestID        the ID of the API request.
  */
case class UserChangeStatusRequestV1(userIri: IRI,
                                     changeStatusRequest: ChangeUserStatusApiRequestV1,
                                     userProfile: UserProfileV1,
                                     apiRequestID: UUID) extends UsersResponderRequestV1


// Responses

/**
  * Represents an answer to an user profile request.
  *
  * @param userProfile the user's profile of the requested type.
  * @param userData    information about the user that made the request.
  */
case class UserProfileResponseV1(userProfile: UserProfileV1, userData: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = UserV1JsonProtocol.userProfileResponseV1Format.write(this)
}

/**
  * Represents an answer to an user creating/modifying operation.
  *
  * @param userProfile the new user profile of the created/modified user.
  * @param userData    information about the user that made the request.
  */
case class UserOperationResponseV1(userProfile: UserProfileV1, userData: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = UserV1JsonProtocol.userOperationResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents a user's profile.
  *
  * @param userData       basic information about the user.
  * @param groups         the groups that the user belongs to.
  * @param projects       the projects that the user belongs to.
  * @param sessionId      the sessionId,.
  * @param permissionData the user's permission data.
  */
case class UserProfileV1(userData: UserDataV1 = UserDataV1(lang = "en"),
                         groups: Seq[IRI] = Vector.empty[IRI],
                         projects: Seq[IRI] = Vector.empty[IRI],
                         sessionId: Option[String] = None,
                         isSystemUser: Boolean = false,
                         permissionData: PermissionDataV1 = PermissionDataV1()
                        ) {

    /**
      * Check password using either SHA-1 or BCrypt. The SCrypt password always starts with '$s0$'
      *
      * @param password the password to check.
      * @return true if password matches and false if password doesn't match.
      */
    def passwordMatch(password: String): Boolean = {
        userData.password.exists {
            hashedpassword =>
                hashedpassword match {
                    case hp if hp.startsWith("$s0$") => {
                        //println(s"UserProfileV1 - passwordMatch - password: $password, hashedpassword: $hashedpassword")
                        import com.lambdaworks.crypto.SCryptUtil
                        SCryptUtil.check(password, hp)
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
        userData.password.exists { hashedPassword =>
            md.digest(password.getBytes("UTF-8")).map("%02x".format(_)).mkString.equals(hashedPassword)
        }
    }

    /**
      * Check password hashed using SCrypt.
      *
      * @param password the password to check
      * @return true if password matches and false if password doesn't match.
      */
    private def passwordMatchSCrypt(password: String): Boolean = {
        import com.lambdaworks.crypto.SCryptUtil
        userData.password.exists {
            hashedPassword => SCryptUtil.check(password, hashedPassword)
        }
    }

    /**
      * Creating a [[UserProfileV1]] of the requested type.
      *
      * @return a [[UserProfileV1]]
      */
    def ofType(userProfileType: UserProfileType): UserProfileV1 = {

        userProfileType match {
            case UserProfileType.RESTRICTED => {
                val olduserdata = userData
                val newuserdata = UserDataV1(
                    lang = olduserdata.lang,
                    user_id = olduserdata.user_id,
                    token = None, // remove token
                    firstname = olduserdata.firstname,
                    lastname = olduserdata.lastname,
                    email = olduserdata.email,
                    password = None, // remove password
                    isActiveUser = olduserdata.isActiveUser,
                    projects = olduserdata.projects
                )

                UserProfileV1(
                    userData = newuserdata,
                    groups = groups,
                    projects = projects,
                    permissionData = permissionData,
                    sessionId = sessionId
                )
            }
            case UserProfileType.FULL => {
                UserProfileV1(
                    userData = userData,
                    groups = groups,
                    projects = projects,
                    permissionData = permissionData,
                    sessionId = sessionId
                )
            }
            case _ => throw BadRequestException(s"The requested userProfileType: $userProfileType is invalid.")
        }
    }

    def getDigest: String = {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        val time = System.currentTimeMillis().toString
        val value = (time + userData.toString) getBytes ("UTF-8")
        md.digest(value).map("%02x".format(_)).mkString
    }

    def setSessionId(sessionId: String): UserProfileV1 = {
        UserProfileV1(
            userData = userData,
            groups = groups,
            projects = projects,
            permissionData = permissionData,
            sessionId = Some(sessionId)
        )
    }

    def toSourceString: String = {
        MessageUtil.toSource(userData) + "\n" +
                MessageUtil.toSource(groups) + "\n" +
                MessageUtil.toSource(projects) + "\n" +
                permissionData.toSourceString + "\n" +
                MessageUtil.toSource(sessionId)
    }

    def isAnonymousUser: Boolean = {
        userData.user_id match {
            case Some(iri) => true
            case None => false
        }
    }

}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents basic information about a user.
  *
  * @param lang         The ISO 639-1 code of the user's preferred language.
  * @param user_id      The user's IRI.
  * @param email        The user's email address.
  * @param password     The user's hashed password.
  * @param token        The API token. Can be used instead of email/password for authentication.
  * @param firstname    The user's given name.
  * @param lastname     The user's surname.
  * @param isActiveUser The user's status.
  */
case class UserDataV1(lang: String,
                      user_id: Option[IRI] = None,
                      email: Option[String] = None,
                      password: Option[String] = None,
                      token: Option[String] = None,
                      firstname: Option[String] = None,
                      lastname: Option[String] = None,
                      isActiveUser: Option[Boolean] = None,
                      projects: Seq[IRI] = Seq.empty[IRI]) {

    def fullname: Option[String] = {
        (firstname, lastname) match {
            case (Some(firstnameStr), Some(lastnameStr)) => Some(firstnameStr + " " + lastnameStr)
            case (Some(firstnameStr), None) => Some(firstnameStr)
            case (None, Some(lastnameStr)) => Some(lastnameStr)
            case (None, None) => None
        }
    }

    def toJsValue = UserV1JsonProtocol.userDataV1Format.write(this)

}

/**
  * UserProfile types:
  * restricted: everything without sensitive information, i.e. token, password.
  * full: everything.
  *
  * Mainly used in combination with the 'ofType' method, to make sure that a request receiving this information
  * also returns the user profile of the correct type. Should be used in cases where we don't want to expose
  * sensitive information to the outside world. Since in API V1 [[UserDataV1]] is returned with almost every response,
  * we use 'restricted' in those cases every time.
  */
object UserProfileType extends Enumeration {
    /* TODO: Extend to incorporate user privacy wishes */

    type UserProfileType = Value

    val RESTRICTED = Value(0, "restricted")
    // without sensitive information
    val FULL = Value(1, "full") // everything, including sensitive information

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
      * [[InconsistentTriplestoreDataException]].
      *
      * @param name the name of the value.
      * @return the requested value.
      */
    def lookup(name: String): Value = {
        valueMap.get(name) match {
            case Some(value) => value
            case None => throw InconsistentTriplestoreDataException(s"User profile type not supported: $name")
        }
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for formatting objects as JSON.
  */
object UserV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions with ProjectV1JsonProtocol with PermissionV1JsonProtocol {

    implicit val userDataV1Format: JsonFormat[UserDataV1] = lazyFormat(jsonFormat9(UserDataV1))
    implicit val userProfileV1Format: JsonFormat[UserProfileV1] = jsonFormat6(UserProfileV1)
    implicit val createUserApiRequestV1Format: RootJsonFormat[CreateUserApiRequestV1] = jsonFormat7(CreateUserApiRequestV1)
    implicit val updateUserApiRequestV1Format: RootJsonFormat[UpdateUserApiRequestV1] = jsonFormat7(UpdateUserApiRequestV1)
    implicit val changeUserPasswordApiRequestV1Format: RootJsonFormat[ChangeUserPasswordApiRequestV1] = jsonFormat2(ChangeUserPasswordApiRequestV1)
    implicit val changeUserStatusApiRequestV1Format: RootJsonFormat[ChangeUserStatusApiRequestV1] = jsonFormat1(ChangeUserStatusApiRequestV1)
    implicit val userProfileResponseV1Format: RootJsonFormat[UserProfileResponseV1] = jsonFormat2(UserProfileResponseV1)
    implicit val userOperationResponseV1Format: RootJsonFormat[UserOperationResponseV1] = jsonFormat2(UserOperationResponseV1)
}
