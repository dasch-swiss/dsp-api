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
import org.knora.webapi
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoV1, ProjectV1JsonProtocol}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileType.UserProfileType
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import spray.json._


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new user.
  *
  * @param username      the username of the user to be created (unique).
  * @param givenName     the given name of the user to be created.
  * @param familyName    the family name of the user to be created
  * @param email         the email of the user to be created.
  * @param password      the password of the user to be created.
  * @param isActive      the status of the user to be created.
  * @param lang          the default language of the user to be created.
  */
case class CreateUserApiRequestV1(username: String,
                                  givenName: String,
                                  familyName: String,
                                  email: String,
                                  password: String,
                                  isActive: Boolean,
                                  lang: String) {

    def toJsValue = UserV1JsonProtocol.createUserApiRequestV1Format.write(this)

}

/**
  * Represents an API request payload that asks the Knora API server to update one property of an existing user.
  *
  * @param propertyIri  the property of the user to be updated.
  * @param newValue     the new value for the property of the user to be updated.
  */
case class UpdateUserApiRequestV1(propertyIri: String,
                                  newValue: String) {

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
  * @param userProfileType the extent of the information returned.
  */
case class UserProfileByIRIGetRequestV1(userIri: IRI,
                                        userProfileType: UserProfileType = UserProfileType.SHORT) extends UsersResponderRequestV1

/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileV1]].
  *
  * @param username the username of the user to be queried.
  * @param userProfileType the extent of the information returned.
  */
case class UserProfileByUsernameGetRequestV1(username: String,
                                             userProfileType: UserProfileType = UserProfileType.SHORT) extends UsersResponderRequestV1


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
  * @param propertyIri the IRI of the property to be updated.
  * @param newValue the new value for the property.
  * @param userProfile the user profile of the user requesting the update.
  * @param apiRequestID the ID of the API request.
  */
case class UserUpdateRequestV1(userIri: webapi.IRI,
                               propertyIri: webapi.IRI,
                               newValue: Any,
                               userProfile: UserProfileV1,
                               apiRequestID: UUID) extends UsersResponderRequestV1


// Responses

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
  * @param userData basic information about the user.
  * @param groups   the groups that the user belongs to.
  * @param projects the projects that the user belongs to.
  * @param projectInfos the project info of the projects that the user belongs to.
  * @param projectGroups the projects and all groups inside a project the user belongs to.
  * @param isInSystemAdminGroup the user's knora-base:SystemAdmin group membership status.
  * @param isInProjectAdminGroup shows for which projects the user is member in the knora-base:ProjectAdmin group.
  * @param projectAdministrativePermissions the user's administrative permissions for each project.
  * @param projectDefaultObjectAccessPermissions the user's default object access permissions for each project.
  * @param sessionId the sessionId,.
  */
case class UserProfileV1(userData: UserDataV1 = UserDataV1(lang = "en"),
                         groups: Seq[IRI] = Vector.empty[IRI],
                         projects: Seq[IRI] = Vector.empty[IRI],
                         projectInfos: Seq[ProjectInfoV1] = Vector.empty[ProjectInfoV1],
                         projectGroups: Map[IRI, List[IRI]] = Map.empty[IRI, List[IRI]],
                         isInSystemAdminGroup: Boolean = false,
                         isInProjectAdminGroup: Seq[IRI] = Vector.empty[IRI],
                         projectAdministrativePermissions: Map[IRI, List[String]] = Map.empty[IRI, List[String]],
                         projectDefaultObjectAccessPermissions: Map[IRI, List[String]] = Map.empty[IRI, List[String]],
                         sessionId: Option[String] = None,
                         isSystemUser: Boolean = false) {

    /**
      * Check password using either SHA-1 or BCrypt. The BCrypt password always starts with '$2a$'
      *
      * @param password the password to check.
      * @return true if password matches and false if password doesn't match.
      */
    def passwordMatch(password: String): Boolean = {
        userData.password.exists {
            hashedpassword => hashedpassword match {
                case hp if hp.startsWith("$2a$") => {
                    //println(s"password: $password, hashedpassword: $hashedpassword")
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
        userData.password.exists { hashedPassword =>
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
        userData.password.exists {
            hashedPassword => BCrypt.checkpw(password, hashedPassword)
        }
    }

    /**
      * Creating a [[UserProfileV1]] with sensitive information stripped.
      *
      * @return a [[UserProfileV1]]
      */
    def ofType(userProfileType: UserProfileType.Value): UserProfileV1 = {

        userProfileType match {
            case UserProfileType.SHORT => {
                val olduserdata = userData
                val newuserdata = UserDataV1(
                    lang = olduserdata.lang,
                    user_id = olduserdata.user_id,
                    token = None, // remove token
                    username = olduserdata.username,
                    firstname = olduserdata.firstname,
                    lastname = olduserdata.lastname,
                    email = olduserdata.email,
                    password = None, // remove password
                    isActiveUser = olduserdata.isActiveUser,
                    active_project = olduserdata.active_project
                )

                UserProfileV1(
                    userData = newuserdata,
                    groups = groups,
                    projects = projects,
                    projectInfos = Vector.empty[ProjectInfoV1], // remove
                    projectGroups = projectGroups,
                    isInSystemAdminGroup = false, // remove system admin status
                    isInProjectAdminGroup = Vector.empty[IRI], // remove privileged group membership
                    projectAdministrativePermissions = Map.empty[IRI, List[String]], // remove administrative permission information
                    projectDefaultObjectAccessPermissions = Map.empty[IRI, List[String]], // remove default object access permission information
                    sessionId = None // remove session id
                )
            }
            case UserProfileType.SAFE => {
                val olduserdata = userData
                val newuserdata = UserDataV1(
                    lang = olduserdata.lang,
                    user_id = olduserdata.user_id,
                    token = None, // remove token
                    username = olduserdata.username,
                    firstname = olduserdata.firstname,
                    lastname = olduserdata.lastname,
                    email = olduserdata.email,
                    password = None, // remove password
                    isActiveUser = olduserdata.isActiveUser,
                    active_project = olduserdata.active_project
                )

                UserProfileV1(
                    userData = newuserdata,
                    groups = groups,
                    projects = projects,
                    projectInfos = projectInfos,
                    projectGroups = projectGroups,
                    isInSystemAdminGroup = false, // remove system admin status
                    isInProjectAdminGroup = Vector.empty[IRI], // remove privileged group membership
                    projectAdministrativePermissions = Map.empty[IRI, List[String]], // remove administrative permission information
                    projectDefaultObjectAccessPermissions = Map.empty[IRI, List[String]], // remove default object access permission information
                    sessionId = None // remove session id
                )
            }
            case UserProfileType.FULL => {
                UserProfileV1(
                    userData = userData,
                    groups = groups,
                    projects = projects,
                    projectInfos = projectInfos,
                    projectGroups = projectGroups,
                    isInSystemAdminGroup = isInSystemAdminGroup,
                    isInProjectAdminGroup = isInProjectAdminGroup,
                    projectAdministrativePermissions =projectAdministrativePermissions,
                    projectDefaultObjectAccessPermissions =projectDefaultObjectAccessPermissions,
                    sessionId = sessionId
                )
            }
            case _ => throw BadRequestException(s"The requested userProfileType: $userProfileType is invalid.")
        }
    }

    def getDigest: String = {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        val time = System.currentTimeMillis().toString
        val value = (time + userData.toString)getBytes("UTF-8")
        md.digest(value).map("%02x".format(_)).mkString
    }

    def setSessionId(sessionId: String): UserProfileV1 = {
        UserProfileV1(
            userData = userData,
            groups = groups,
            projects = projects,
            isInSystemAdminGroup = isInSystemAdminGroup,
            isInProjectAdminGroup = isInProjectAdminGroup,
            sessionId = Some(sessionId)
        )
    }

    def isSystemAdmin: Boolean = {
        isInSystemAdminGroup
    }
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents basic information about a user.
  *
  * @param lang         The ISO 639-1 code of the user's preferred language.
  * @param user_id      The user's IRI.
  * @param token        TODO: document this
  * @param username     The user's username.
  * @param firstname    The user's given name.
  * @param lastname     The user's surname.
  * @param email        The user's email address.
  * @param password     The user's hashed password.
  * @param isActiveUser The user's status.
  * @param active_project
  */
case class UserDataV1(lang: String,
                      user_id: Option[IRI] = None,
                      token: Option[String] = None,
                      username: Option[String] = None,
                      firstname: Option[String] = None,
                      lastname: Option[String] = None,
                      email: Option[String] = None,
                      password: Option[String] = None,
                      isActiveUser: Option[Boolean] = None,
                      active_project: Option[IRI] = None ) {

    def toJsValue = UserV1JsonProtocol.userDataV1Format.write(this)

}


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

/**
  * UserProfile types:
  * short: short without sensitive information
  * safe: everything without sensitive information
  * full: everything
  */
object UserProfileType extends Enumeration {
    /* TODO: Extend to incorporate user privacy wishes */

    type UserProfileType = Value

    val SHORT = Value(0, "short") // short without sensitive information
    val SAFE = Value(1, "safe") // everything without sensitive information (password, etc.)
    val FULL = Value(2, "full") // everything

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
            case None => throw InconsistentTriplestoreDataException(s"Project info type not supported: $name")
        }
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for formatting objects as JSON.
  */
object UserV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions with ProjectV1JsonProtocol {

    implicit val userDataV1Format: JsonFormat[UserDataV1] = lazyFormat(jsonFormat10(UserDataV1))
    implicit val userProfileV1Format: JsonFormat[UserProfileV1] = jsonFormat11(UserProfileV1)
    implicit val newUserDataV1Format: JsonFormat[NewUserDataV1] = jsonFormat6(NewUserDataV1)
    implicit val createUserApiRequestV1Format: RootJsonFormat[CreateUserApiRequestV1] = jsonFormat7(CreateUserApiRequestV1)
    implicit val updateUserApiRequestV1Format: RootJsonFormat[UpdateUserApiRequestV1] = jsonFormat2(UpdateUserApiRequestV1)
    implicit val userOperationResponseV1Format: RootJsonFormat[UserOperationResponseV1] = jsonFormat2(UserOperationResponseV1)
}
