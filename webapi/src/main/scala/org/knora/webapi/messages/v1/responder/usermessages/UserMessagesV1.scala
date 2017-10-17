/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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
import org.knora.webapi.messages.v1.responder.groupmessages.GroupV1JsonProtocol
import org.knora.webapi.messages.v1.responder.permissionmessages.{PermissionDataV1, PermissionV1JsonProtocol}
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoV1, ProjectV1JsonProtocol}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileTypeV1.UserProfileType
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
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
  * @param status      the status of the user to be created (active = true, inactive = false).
  * @param lang        the default language of the user to be created.
  * @param systemAdmin the system admin membership.
  */
case class CreateUserApiRequestV1(email: String,
                                  givenName: String,
                                  familyName: String,
                                  password: String,
                                  status: Boolean,
                                  lang: String,
                                  systemAdmin: Boolean) {

    def toJsValue: JsValue = UserV1JsonProtocol.createUserApiRequestV1Format.write(this)
}

/**
  * Represents an API request payload that asks the Knora API server to update an existing user. Information that can
  * be changed include the user's email, given name, family name, language, password, user status, and system admin
  * membership.
  *
  * @param email       the new email address. Needs to be unique on the server.
  * @param givenName   the new given name.
  * @param familyName  the new family name.
  * @param lang        the new ISO 639-1 code of the new preferred language.
  * @param oldPassword the old password.
  * @param newPassword the new password.
  * @param status      the new user status (active = true, inactive = false).
  * @param systemAdmin the new system admin membership status.
  */
case class ChangeUserApiRequestV1(email: Option[String] = None,
                                  givenName: Option[String] = None,
                                  familyName: Option[String] = None,
                                  lang: Option[String] = None,
                                  oldPassword: Option[String] = None,
                                  newPassword: Option[String] = None,
                                  status: Option[Boolean] = None,
                                  systemAdmin: Option[Boolean] = None) {

    val parametersCount: Int = List(
        email,
        givenName,
        familyName,
        lang,
        oldPassword,
        newPassword,
        status,
        systemAdmin
    ).flatten.size

    // something needs to be sent, i.e. everything 'None' is not allowed
    if (parametersCount == 0) throw BadRequestException("No data sent in API request.")


    /* check that only allowed information for the 4 cases is send and not more. */

    // change password case
    if (oldPassword.isDefined || newPassword.isDefined) {
        if (parametersCount > 2) {
            throw BadRequestException("To many parameters sent for password change.")
        } else if (parametersCount < 2) {
            throw BadRequestException("To few parameters sent for password change.")
        }
    }

    // change status case
    if (status.isDefined) {
        if (parametersCount > 1) throw BadRequestException("To many parameters sent for user status change.")
    }

    // change system admin membership case
    if (systemAdmin.isDefined) {
        if (parametersCount > 1) throw BadRequestException("To many parameters sent for system admin membership change.")
    }

    // change basic user information case
    if (parametersCount > 4) throw BadRequestException("To many parameters sent for basic user information change.")

    def toJsValue: JsValue = UserV1JsonProtocol.changeUserApiRequestV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing message that can be sent to `UsersResponderV1`.
  */
sealed trait UsersResponderRequestV1 extends KnoraRequestV1

/**
  * Get all information about all users in form of [[UsersGetResponseV1]]. The UsersGetRequestV1 returns either
  * something or a NotFound exception if there are no users found. Administration permission checking is performed.
  *
  * @param userProfileV1 the profile of the user that is making the request.
  */
case class UsersGetRequestV1(userProfileV1: UserProfileV1) extends UsersResponderRequestV1


/**
  * Get all information about all users in form of a sequence of [[UserDataV1]]. Returns an empty sequence if
  * no users are found. Administration permission checking is skipped.
  *
  */
case class UsersGetV1() extends UsersResponderRequestV1


/**
  * A message that requests basic user data. A successful response will be a [[UserDataV1]].
  *
  * @param userIri the IRI of the user to be queried.
  * @param short   denotes if all information should be returned. If short == true, then token and password are not returned.
  */
case class UserDataByIriGetV1(userIri: IRI, short: Boolean = true) extends UsersResponderRequestV1


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
  * @param userIri           the IRI of the user to be updated.
  * @param changeUserRequest the data which needs to be update.
  * @param userProfile       the user profile of the user requesting the update.
  * @param apiRequestID      the ID of the API request.
  */
case class UserChangeBasicUserDataRequestV1(userIri: IRI,
                                            changeUserRequest: ChangeUserApiRequestV1,
                                            userProfile: UserProfileV1,
                                            apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Request updating the users password.
  *
  * @param userIri           the IRI of the user to be updated.
  * @param changeUserRequest the [[ChangeUserApiRequestV1]] object containing the old and new password.
  * @param userProfile       the user profile of the user requesting the update.
  * @param apiRequestID      the ID of the API request.
  */
case class UserChangePasswordRequestV1(userIri: IRI,
                                       changeUserRequest: ChangeUserApiRequestV1,
                                       userProfile: UserProfileV1,
                                       apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Request updating the users status ('knora-base:isActiveUser' property)
  *
  * @param userIri           the IRI of the user to be updated.
  * @param changeUserRequest the [[ChangeUserApiRequestV1]] containing the new status (true / false).
  * @param userProfile       the user profile of the user requesting the update.
  * @param apiRequestID      the ID of the API request.
  */
case class UserChangeStatusRequestV1(userIri: IRI,
                                     changeUserRequest: ChangeUserApiRequestV1,
                                     userProfile: UserProfileV1,
                                     apiRequestID: UUID) extends UsersResponderRequestV1


/**
  * Request updating the users system admin status ('knora-base:isInSystemAdminGroup' property)
  *
  * @param userIri           the IRI of the user to be updated.
  * @param changeUserRequest the [[ChangeUserApiRequestV1]] containing
  *                          the new system admin membership status (true / false).
  * @param userProfile       the user profile of the user requesting the update.
  * @param apiRequestID      the ID of the API request.
  */
case class UserChangeSystemAdminMembershipStatusRequestV1(userIri: IRI,
                                                          changeUserRequest: ChangeUserApiRequestV1,
                                                          userProfile: UserProfileV1,
                                                          apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests user's project memberships.
  *
  * @param userIri       the IRI of the user.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserProjectMembershipsGetRequestV1(userIri: IRI,
                                              userProfileV1: UserProfileV1,
                                              apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests adding the user to a project.
  *
  * @param userIri       the IRI of the user to be updated.
  * @param projectIri    the IRI of the project.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserProjectMembershipAddRequestV1(userIri: IRI,
                                             projectIri: IRI,
                                             userProfileV1: UserProfileV1,
                                             apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests removing the user from a project.
  *
  * @param userIri       the IRI of the user to be updated.
  * @param projectIri    the IRI of the project.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserProjectMembershipRemoveRequestV1(userIri: IRI,
                                                projectIri: IRI,
                                                userProfileV1: UserProfileV1,
                                                apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests user's project admin memberships.
  *
  * @param userIri       the IRI of the user.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserProjectAdminMembershipsGetRequestV1(userIri: IRI,
                                                   userProfileV1: UserProfileV1,
                                                   apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests adding the user to a project as project admin.
  *
  * @param userIri       the IRI of the user to be updated.
  * @param projectIri    the IRI of the project.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserProjectAdminMembershipAddRequestV1(userIri: IRI,
                                                  projectIri: IRI,
                                                  userProfileV1: UserProfileV1,
                                                  apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests removing the user from a project as project admin.
  *
  * @param userIri       the IRI of the user to be updated.
  * @param projectIri    the IRI of the project.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserProjectAdminMembershipRemoveRequestV1(userIri: IRI,
                                                     projectIri: IRI,
                                                     userProfileV1: UserProfileV1,
                                                     apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests user's group memberships.
  *
  * @param userIri       the IRI of the user.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserGroupMembershipsGetRequestV1(userIri: IRI,
                                            userProfileV1: UserProfileV1,
                                            apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests adding the user to a group.
  *
  * @param userIri       the IRI of the user to be updated.
  * @param groupIri      the IRI of the group.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserGroupMembershipAddRequestV1(userIri: IRI,
                                           groupIri: IRI,
                                           userProfileV1: UserProfileV1,
                                           apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests removing the user from a group.
  *
  * @param userIri       the IRI of the user to be updated.
  * @param groupIri      the IRI of the group.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserGroupMembershipRemoveRequestV1(userIri: IRI,
                                              groupIri: IRI,
                                              userProfileV1: UserProfileV1,
                                              apiRequestID: UUID) extends UsersResponderRequestV1


// Responses

/**
  * Represents an answer to a request for a list of all users.
  *
  * @param users a sequence of user profiles of the requested type.
  */
case class UsersGetResponseV1(users: Seq[UserDataV1]) extends KnoraResponseV1 {
    def toJsValue = UserV1JsonProtocol.usersGetResponseV1Format.write(this)
}

/**
  * Represents an answer to a user profile request.
  *
  * @param userProfile the user's profile of the requested type.
  */
case class UserProfileResponseV1(userProfile: UserProfileV1) extends KnoraResponseV1 {
    def toJsValue: JsValue = UserV1JsonProtocol.userProfileResponseV1Format.write(this)
}

/**
  * Represents an answer to a request for a list of all projects the user is member of.
  *
  * @param projects a sequence of projects the user is member of.
  */
case class UserProjectMembershipsGetResponseV1(projects: Seq[IRI]) extends KnoraResponseV1 {
    def toJsValue: JsValue = UserV1JsonProtocol.userProjectMembershipsGetResponseV1Format.write(this)
}

/**
  * Represents an answer to a request for a list of all projects the user is member of the project admin group.
  *
  * @param projects a sequence of projects the user is member of the project admin group.
  */
case class UserProjectAdminMembershipsGetResponseV1(projects: Seq[IRI]) extends KnoraResponseV1 {
    def toJsValue: JsValue = UserV1JsonProtocol.userProjectAdminMembershipsGetResponseV1Format.write(this)
}

/**
  * Represents an answer to a request for a list of all groups the user is member of.
  *
  * @param groups a sequence of groups the user is member of.
  */
case class UserGroupMembershipsGetResponseV1(groups: Seq[IRI]) extends KnoraResponseV1 {
    def toJsValue: JsValue = UserV1JsonProtocol.userGroupMembershipsGetResponseV1Format.write(this)
}

/**
  * Represents an answer to a user creating/modifying operation.
  *
  * @param userProfile the new user profile of the created/modified user.
  */
case class UserOperationResponseV1(userProfile: UserProfileV1) extends KnoraResponseV1 {
    def toJsValue: JsValue = UserV1JsonProtocol.userOperationResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents a user's profile.
  *
  * @param userData       basic information about the user.
  * @param groups         the groups that the user belongs to.
  * @param projects_info  the projects that the user belongs to.
  * @param sessionId      the sessionId,.
  * @param permissionData the user's permission data.
  */
case class UserProfileV1(userData: UserDataV1 = UserDataV1(lang = "en"),
                         groups: Seq[IRI] = Vector.empty[IRI],
                         projects_info: Map[IRI, ProjectInfoV1] = Map.empty[IRI, ProjectInfoV1],
                         sessionId: Option[String] = None,
                         isSystemUser: Boolean = false,
                         permissionData: PermissionDataV1 = PermissionDataV1(anonymousUser = true)) {

    /**
      * Check password using either SHA-1 or SCrypt.
      * The SCrypt password always starts with '$e0801$' (spring.framework implementation)
      *
      * @param password the password to check.
      * @return true if password matches and false if password doesn't match.
      */
    def passwordMatch(password: String): Boolean = {
        userData.password.exists {
            hashedPassword =>
                if (hashedPassword.startsWith("$e0801$")) {
                    //println(s"UserProfileV1 - passwordMatch - password: $password, hashedPassword: hashedPassword")
                    import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder
                    val encoder = new SCryptPasswordEncoder
                    encoder.matches(password, hashedPassword)
                } else {
                    val md = java.security.MessageDigest.getInstance("SHA-1")
                    md.digest(password.getBytes("UTF-8")).map("%02x".format(_)).mkString.equals(hashedPassword)
                }
        }
    }

    /**
      * Creating a [[UserProfileV1]] of the requested type.
      *
      * @return a [[UserProfileV1]]
      */
    def ofType(userProfileType: UserProfileType): UserProfileV1 = {

        userProfileType match {
            case UserProfileTypeV1.SHORT => {
                val oldUserData = userData
                val newUserData = UserDataV1(
                    user_id = oldUserData.user_id,
                    token = None, // remove token
                    firstname = oldUserData.firstname,
                    lastname = oldUserData.lastname,
                    email = oldUserData.email,
                    password = None, // remove password
                    status = oldUserData.status,
                    lang = oldUserData.lang
                )

                UserProfileV1(
                    userData = newUserData,
                    groups = Vector.empty[IRI], // removed groups
                    projects_info = Map.empty[IRI, ProjectInfoV1], // removed projects
                    permissionData = PermissionDataV1(anonymousUser = false),
                    sessionId = None // removed sessionId
                )
            }
            case UserProfileTypeV1.RESTRICTED => {
                val oldUserData = userData
                val newUserData = UserDataV1(
                    lang = oldUserData.lang,
                    user_id = oldUserData.user_id,
                    token = None, // remove token
                    firstname = oldUserData.firstname,
                    lastname = oldUserData.lastname,
                    email = oldUserData.email,
                    password = None, // remove password
                    status = oldUserData.status
                )

                UserProfileV1(
                    userData = newUserData,
                    groups = groups,
                    projects_info = projects_info,
                    permissionData = permissionData,
                    sessionId = None // removed sessionId
                )
            }
            case UserProfileTypeV1.FULL => {
                UserProfileV1(
                    userData = userData,
                    groups = groups,
                    projects_info = projects_info,
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
        val value = (time + userData.toString).getBytes("UTF-8")
        md.digest(value).map("%02x".format(_)).mkString
    }

    def setSessionId(sessionId: String): UserProfileV1 = {
        UserProfileV1(
            userData = userData,
            groups = groups,
            permissionData = permissionData,
            sessionId = Some(sessionId)
        )
    }

    def isAnonymousUser: Boolean = {
        permissionData.anonymousUser
    }

    def isActive: Boolean = {
        userData.status.getOrElse(false)
    }

    def toJsValue: JsValue = UserV1JsonProtocol.userProfileV1Format.write(this)

}


/**
  * Represents basic information about a user.
  *
  * @param user_id   The user's IRI.
  * @param email     The user's email address.
  * @param password  The user's hashed password.
  * @param token     The API token. Can be used instead of email/password for authentication.
  * @param firstname The user's given name.
  * @param lastname  The user's surname.
  * @param status    The user's status.
  * @param lang      The ISO 639-1 code of the user's preferred language.
  */
case class UserDataV1(user_id: Option[IRI] = None,
                      email: Option[String] = None,
                      password: Option[String] = None,
                      token: Option[String] = None,
                      firstname: Option[String] = None,
                      lastname: Option[String] = None,
                      status: Option[Boolean] = Some(true),
                      lang: String) {

    def fullname: Option[String] = {
        (firstname, lastname) match {
            case (Some(firstnameStr), Some(lastnameStr)) => Some(firstnameStr + " " + lastnameStr)
            case (Some(firstnameStr), None) => Some(firstnameStr)
            case (None, Some(lastnameStr)) => Some(lastnameStr)
            case (None, None) => None
        }
    }

    def toJsValue: JsValue = UserV1JsonProtocol.userDataV1Format.write(this)

}

/**
  * UserProfile types:
  * restricted: everything without sensitive information, i.e. token, password.
  * full: everything.
  *
  * Mainly used in combination with the 'ofType' method, to make sure that a request receiving this information
  * also returns the user profile of the correct type. Should be used in cases where we don't want to expose
  * sensitive information to the outside world. Since in API V1 [[UserDataV1]] is returned with some responses,
  * we use 'restricted' in those cases.
  */
object UserProfileTypeV1 extends Enumeration {
    /* TODO: Extend to incorporate user privacy wishes */

    type UserProfileType = Value

    val SHORT = Value(0, "short") // only userdata
    val RESTRICTED = Value(1, "restricted") // without sensitive information
    val FULL = Value(2, "full") // everything, including sensitive information

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


/**
  * Payload used for updating of an existing user.
  *
  * @param email         the new email address. Needs to be unique on the server.
  * @param givenName     the new given name.
  * @param familyName    the new family name.
  * @param password      the new password.
  * @param status        the new status.
  * @param lang          the new language.
  * @param projects      the new project memberships list.
  * @param projectsAdmin the new projects admin membership list.
  * @param groups        the new group memberships list.
  * @param systemAdmin   the new system admin membership
  */
case class UserUpdatePayloadV1(email: Option[String] = None,
                               givenName: Option[String] = None,
                               familyName: Option[String] = None,
                               password: Option[String] = None,
                               status: Option[Boolean] = None,
                               lang: Option[String] = None,
                               projects: Option[Seq[IRI]] = None,
                               projectsAdmin: Option[Seq[IRI]] = None,
                               groups: Option[Seq[IRI]] = None,
                               systemAdmin: Option[Boolean] = None) {

    val parametersCount: Int = List(
        email,
        givenName,
        familyName,
        password,
        status,
        lang,
        projects,
        projectsAdmin,
        groups,
        systemAdmin
    ).flatten.size

    // something needs to be sent, i.e. everything 'None' is not allowed
    if (parametersCount == 0) {
        throw BadRequestException("No data sent in API request.")
    }

    /* check that only allowed information for the 4 cases is send and not more. */

    // change password case
    if (password.isDefined && parametersCount > 1) {
        throw BadRequestException("To many parameters sent for password change.")
    }

    // change status case
    if (status.isDefined && parametersCount > 1) {
        throw BadRequestException("To many parameters sent for user status change.")
    }

    // change system admin membership case
    if (systemAdmin.isDefined && parametersCount > 1) {
        throw BadRequestException("To many parameters sent for system admin membership change.")
    }

    // change project memberships
    if (projects.isDefined && parametersCount > 1) {
        throw BadRequestException("To many parameters sent for project membership change.")
    }

    // change projectAdmin memberships
    if (projectsAdmin.isDefined && parametersCount > 1) {
        throw BadRequestException("To many parameters sent for projectAdmin membership change.")
    }

    // change group memberships
    if (groups.isDefined && parametersCount > 1) {
        throw BadRequestException("To many parameters sent for group membership change.")
    }

    // change basic user information case
    if (parametersCount > 4) {
        throw BadRequestException("To many parameters sent for basic user information change.")
    }

}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for formatting objects as JSON.
  */
object UserV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions with ProjectV1JsonProtocol with GroupV1JsonProtocol with PermissionV1JsonProtocol {

    implicit val userDataV1Format: JsonFormat[UserDataV1] = lazyFormat(jsonFormat8(UserDataV1))
    implicit val userProfileV1Format: JsonFormat[UserProfileV1] = jsonFormat6(UserProfileV1)
    implicit val createUserApiRequestV1Format: RootJsonFormat[CreateUserApiRequestV1] = jsonFormat7(CreateUserApiRequestV1)
    implicit val changeUserApiRequestV1Format: RootJsonFormat[ChangeUserApiRequestV1] = jsonFormat(ChangeUserApiRequestV1, "email", "givenName", "familyName", "lang", "oldPassword", "newPassword", "status", "systemAdmin")
    implicit val usersGetResponseV1Format: RootJsonFormat[UsersGetResponseV1] = jsonFormat1(UsersGetResponseV1)
    implicit val userProfileResponseV1Format: RootJsonFormat[UserProfileResponseV1] = jsonFormat1(UserProfileResponseV1)
    implicit val userProjectMembershipsGetResponseV1Format: RootJsonFormat[UserProjectMembershipsGetResponseV1] = jsonFormat1(UserProjectMembershipsGetResponseV1)
    implicit val userProjectAdminMembershipsGetResponseV1Format: RootJsonFormat[UserProjectAdminMembershipsGetResponseV1] = jsonFormat1(UserProjectAdminMembershipsGetResponseV1)
    implicit val userGroupMembershipsGetResponseV1Format: RootJsonFormat[UserGroupMembershipsGetResponseV1] = jsonFormat1(UserGroupMembershipsGetResponseV1)
    implicit val userOperationResponseV1Format: RootJsonFormat[UserOperationResponseV1] = jsonFormat1(UserOperationResponseV1)
}