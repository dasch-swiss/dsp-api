/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.admin.responder.usersmessages

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.groupsmessages.{GroupADM, GroupsADMJsonProtocol}
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionsADMJsonProtocol, PermissionsDataADM}
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectsADMJsonProtocol}
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM.UserInformationTypeADM
import org.knora.webapi.messages.admin.responder.{KnoraRequestADM, KnoraResponseADM}
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoV1
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.util.StringFormatter
import spray.json._


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new user.
  *
  * @param username    the username of the user to be created (unique).
  * @param email       the email of the user to be created (unique).
  * @param givenName   the given name of the user to be created.
  * @param familyName  the family name of the user to be created
  * @param password    the password of the user to be created.
  * @param status      the status of the user to be created (active = true, inactive = false).
  * @param lang        the default language of the user to be created.
  * @param systemAdmin the system admin membership.
  */
case class CreateUserApiRequestADM(username: String,
                                   email: String,
                                   givenName: String,
                                   familyName: String,
                                   password: String,
                                   status: Boolean,
                                   lang: String,
                                   systemAdmin: Boolean) {

    def toJsValue: JsValue = UsersADMJsonProtocol.createUserApiRequestADMFormat.write(this)

    // check for required information
    if (username.isEmpty) throw BadRequestException("Username cannot be empty")
    if (email.isEmpty) throw BadRequestException("Email cannot be empty")
    if (password.isEmpty) throw BadRequestException("Password cannot be empty")
    if (givenName.isEmpty) throw BadRequestException("Given name cannot be empty")
    if (familyName.isEmpty) throw BadRequestException("Family name cannot be empty")
}

/**
  * Represents an API request payload that asks the Knora API server to update an existing user. Information that can
  * be changed include the user's email, given name, family name, language, password, user status, and system admin
  * membership.
  *
  * @param username          the new username. Needs to be unique on the server.
  * @param email             the new email address. Needs to be unique on the server.
  * @param givenName         the new given name.
  * @param familyName        the new family name.
  * @param lang              the new ISO 639-1 code of the new preferred language.
  * @param requesterPassword the password of the user making the request.
  * @param newPassword       the new password.
  * @param status            the new user status (active = true, inactive = false).
  * @param systemAdmin       the new system admin membership status.
  */
case class ChangeUserApiRequestADM(username: Option[String] = None,
                                   email: Option[String] = None,
                                   givenName: Option[String] = None,
                                   familyName: Option[String] = None,
                                   lang: Option[String] = None,
                                   requesterPassword: Option[String] = None,
                                   newPassword: Option[String] = None,
                                   status: Option[Boolean] = None,
                                   systemAdmin: Option[Boolean] = None) {

    val parametersCount: Int = List(
        username,
        email,
        givenName,
        familyName,
        lang,
        requesterPassword,
        newPassword,
        status,
        systemAdmin
    ).flatten.size

    // println(requesterPassword + " " + newPassword)

    // something needs to be sent, i.e. everything 'None' is not allowed
    if (parametersCount == 0) throw BadRequestException("No data sent in API request.")


    /* check that only allowed information for the 4 cases is send and not more. */

    // change password case
    if (requesterPassword.isDefined || newPassword.isDefined) {
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
    if (parametersCount > 5) throw BadRequestException("To many parameters sent for basic user information change.")

    def toJsValue: JsValue = UsersADMJsonProtocol.changeUserApiRequestADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing message that can be sent to `UsersResponderV1`.
  */
sealed trait UsersResponderRequestADM extends KnoraRequestADM

/**
  * Get all information about all users in form of a sequence of [[UserADM]]. Returns an empty sequence if
  * no users are found. Administration permission checking is skipped.
  *
  * @param userInformationTypeADM the extent of the information returned.
  * @param requestingUser         the user that is making the request.
  */
case class UsersGetADM(userInformationTypeADM: UserInformationTypeADM = UserInformationTypeADM.SHORT,
                       requestingUser: UserADM) extends UsersResponderRequestADM

/**
  * Get all information about all users in form of [[UsersGetResponseV1]]. The UsersGetRequestV1 returns either
  * something or a NotFound exception if there are no users found. Administration permission checking is performed.
  *
  * @param userInformationTypeADM the extent of the information returned.
  * @param requestingUser         the user initiating the request.
  */
case class UsersGetRequestADM(userInformationTypeADM: UserInformationTypeADM = UserInformationTypeADM.SHORT,
                              requestingUser: UserADM) extends UsersResponderRequestADM

/**
  * A message that requests a user's profile either by IRI, username, or email. A successful response will be a [[UserADM]].
  *
  * @param identifier             the IRI, email, or username of the user to be queried.
  * @param userInformationTypeADM the extent of the information returned.
  * @param requestingUser         the user initiating the request.
  */
case class UserGetADM(identifier: UserIdentifierADM,
                      userInformationTypeADM: UserInformationTypeADM = UserInformationTypeADM.SHORT,
                      requestingUser: UserADM) extends UsersResponderRequestADM {

    // need either user IRI username, or email
    if (identifier.isEmpty) {
        throw BadRequestException("Need to provide the user IRI, username, and/or email.")
    }
}

/**
  * A message that requests a user's profile either by IRI, username, or email. A successful response will be a [[UserResponseADM]].
  *
  * @param identifier             the IRI, email, or username of the user to be queried.
  * @param userInformationTypeADM the extent of the information returned.
  * @param requestingUser         the user initiating the request.
  */
case class UserGetRequestADM(identifier: UserIdentifierADM,
                             userInformationTypeADM: UserInformationTypeADM = UserInformationTypeADM.SHORT,
                             requestingUser: UserADM) extends UsersResponderRequestADM {

    // need either user IRI, username, or email
    if (identifier.isEmpty) {
        throw BadRequestException("Need to provide the user IRI, username, and/or email.")
    }
}

/**
  * Requests the creation of a new user.
  *
  * @param createRequest  the [[CreateUserApiRequestADM]] information used for creating the new user.
  * @param requestingUser the user creating the new user.
  * @param apiRequestID   the ID of the API request.
  */
case class UserCreateRequestADM(createRequest: CreateUserApiRequestADM,
                                requestingUser: UserADM,
                                apiRequestID: UUID) extends UsersResponderRequestADM

/**
  * Request updating of an existing user.
  *
  * @param userIri           the IRI of the user to be updated.
  * @param changeUserRequest the data which needs to be update.
  * @param requestingUser    the user initiating the request.
  * @param apiRequestID      the ID of the API request.
  */
case class UserChangeBasicUserInformationRequestADM(userIri: IRI,
                                                    changeUserRequest: ChangeUserApiRequestADM,
                                                    requestingUser: UserADM,
                                                    apiRequestID: UUID) extends UsersResponderRequestADM

/**
  * Request updating the users password.
  *
  * @param userIri           the IRI of the user to be updated.
  * @param changeUserRequest the [[ChangeUserApiRequestADM]] object containing the old and new password.
  * @param requestingUser    the user initiating the request.
  * @param apiRequestID      the ID of the API request.
  */
case class UserChangePasswordRequestADM(userIri: IRI,
                                        changeUserRequest: ChangeUserApiRequestADM,
                                        requestingUser: UserADM,
                                        apiRequestID: UUID) extends UsersResponderRequestADM

/**
  * Request updating the users status ('knora-base:isActiveUser' property)
  *
  * @param userIri           the IRI of the user to be updated.
  * @param changeUserRequest the [[ChangeUserApiRequestADM]] containing the new status (true / false).
  * @param requestingUser    the user initiating the request.
  * @param apiRequestID      the ID of the API request.
  */
case class UserChangeStatusRequestADM(userIri: IRI,
                                      changeUserRequest: ChangeUserApiRequestADM,
                                      requestingUser: UserADM,
                                      apiRequestID: UUID) extends UsersResponderRequestADM


/**
  * Request updating the users system admin status ('knora-base:isInSystemAdminGroup' property)
  *
  * @param userIri           the IRI of the user to be updated.
  * @param changeUserRequest the [[ChangeUserApiRequestADM]] containing
  *                          the new system admin membership status (true / false).
  * @param requestingUser    the user initiating the request.
  * @param apiRequestID      the ID of the API request.
  */
case class UserChangeSystemAdminMembershipStatusRequestADM(userIri: IRI,
                                                           changeUserRequest: ChangeUserApiRequestADM,
                                                           requestingUser: UserADM,
                                                           apiRequestID: UUID) extends UsersResponderRequestADM

/**
  * Requests user's project memberships.
  *
  * @param userIri        the IRI of the user.
  * @param requestingUser the user initiating the request.
  * @param apiRequestID   the ID of the API request.
  */
case class UserProjectMembershipsGetRequestADM(userIri: IRI,
                                               requestingUser: UserADM,
                                               apiRequestID: UUID) extends UsersResponderRequestADM

/**
  * Requests adding the user to a project.
  *
  * @param userIri        the IRI of the user to be updated.
  * @param projectIri     the IRI of the project.
  * @param requestingUser the user initiating the request.
  * @param apiRequestID   the ID of the API request.
  */
case class UserProjectMembershipAddRequestADM(userIri: IRI,
                                              projectIri: IRI,
                                              requestingUser: UserADM,
                                              apiRequestID: UUID) extends UsersResponderRequestADM

/**
  * Requests removing the user from a project.
  *
  * @param userIri        the IRI of the user to be updated.
  * @param projectIri     the IRI of the project.
  * @param requestingUser the user initiating the request.
  * @param apiRequestID   the ID of the API request.
  */
case class UserProjectMembershipRemoveRequestADM(userIri: IRI,
                                                 projectIri: IRI,
                                                 requestingUser: UserADM,
                                                 apiRequestID: UUID) extends UsersResponderRequestADM

/**
  * Requests user's project admin memberships.
  *
  * @param userIri        the IRI of the user.
  * @param requestingUser the user initiating the request.
  * @param apiRequestID   the ID of the API request.
  */
case class UserProjectAdminMembershipsGetRequestADM(userIri: IRI,
                                                    requestingUser: UserADM,
                                                    apiRequestID: UUID) extends UsersResponderRequestADM

/**
  * Requests adding the user to a project as project admin.
  *
  * @param userIri        the IRI of the user to be updated.
  * @param projectIri     the IRI of the project.
  * @param requestingUser the user initiating the request.
  * @param apiRequestID   the ID of the API request.
  */
case class UserProjectAdminMembershipAddRequestADM(userIri: IRI,
                                                   projectIri: IRI,
                                                   requestingUser: UserADM,
                                                   apiRequestID: UUID) extends UsersResponderRequestADM

/**
  * Requests removing the user from a project as project admin.
  *
  * @param userIri        the IRI of the user to be updated.
  * @param projectIri     the IRI of the project.
  * @param requestingUser the user initiating the request.
  * @param apiRequestID   the ID of the API request.
  */
case class UserProjectAdminMembershipRemoveRequestADM(userIri: IRI,
                                                      projectIri: IRI,
                                                      requestingUser: UserADM,
                                                      apiRequestID: UUID) extends UsersResponderRequestADM

/**
  * Requests user's group memberships.
  *
  * @param userIri        the IRI of the user.
  * @param requestingUser the user initiating the request.
  * @param apiRequestID   the ID of the API request.
  */
case class UserGroupMembershipsGetRequestADM(userIri: IRI,
                                             requestingUser: UserADM,
                                             apiRequestID: UUID) extends UsersResponderRequestADM

/**
  * Requests adding the user to a group.
  *
  * @param userIri        the IRI of the user to be updated.
  * @param groupIri       the IRI of the group.
  * @param requestingUser the user initiating the request.
  * @param apiRequestID   the ID of the API request.
  */
case class UserGroupMembershipAddRequestADM(userIri: IRI,
                                            groupIri: IRI,
                                            requestingUser: UserADM,
                                            apiRequestID: UUID) extends UsersResponderRequestADM

/**
  * Requests removing the user from a group.
  *
  * @param userIri        the IRI of the user to be updated.
  * @param groupIri       the IRI of the group.
  * @param requestingUser the user initiating the request.
  * @param apiRequestID   the ID of the API request.
  */
case class UserGroupMembershipRemoveRequestADM(userIri: IRI,
                                               groupIri: IRI,
                                               requestingUser: UserADM,
                                               apiRequestID: UUID) extends UsersResponderRequestADM


// Responses

/**
  * Represents an answer to a request for a list of all users.
  *
  * @param users a sequence of user profiles of the requested type.
  */
case class UsersGetResponseADM(users: Seq[UserADM]) extends KnoraResponseADM {
    def toJsValue = UsersADMJsonProtocol.usersGetResponseADMFormat.write(this)
}

/**
  * Represents an answer to a user profile request.
  *
  * @param user the user's information of the requested type.
  */
case class UserResponseADM(user: UserADM) extends KnoraResponseADM {
    def toJsValue: JsValue = UsersADMJsonProtocol.userProfileResponseADMFormat.write(this)
}

/**
  * Represents an answer to a request for a list of all projects the user is member of.
  *
  * @param projects a sequence of projects the user is member of.
  */
case class UserProjectMembershipsGetResponseADM(projects: Seq[ProjectADM]) extends KnoraResponseADM {
    def toJsValue: JsValue = UsersADMJsonProtocol.userProjectMembershipsGetResponseADMFormat.write(this)
}

/**
  * Represents an answer to a request for a list of all projects the user is member of the project admin group.
  *
  * @param projects a sequence of projects the user is member of the project admin group.
  */
case class UserProjectAdminMembershipsGetResponseADM(projects: Seq[ProjectADM]) extends KnoraResponseADM {
    def toJsValue: JsValue = UsersADMJsonProtocol.userProjectAdminMembershipsGetResponseADMFormat.write(this)
}

/**
  * Represents an answer to a request for a list of all groups the user is member of.
  *
  * @param groups a sequence of groups the user is member of.
  */
case class UserGroupMembershipsGetResponseADM(groups: Seq[GroupADM]) extends KnoraResponseADM {
    def toJsValue: JsValue = UsersADMJsonProtocol.userGroupMembershipsGetResponseADMFormat.write(this)
}

/**
  * Represents an answer to a user creating/modifying operation.
  *
  * @param user the new user profile of the created/modified user.
  */
case class UserOperationResponseADM(user: UserADM) extends KnoraResponseADM {
    def toJsValue: JsValue = UsersADMJsonProtocol.userOperationResponseADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents a user's profile.
  *
  * @param id          The user's IRI.
  * @param username    The user's username (unique).
  * @param email       The user's email address.
  * @param password    The user's hashed password.
  * @param token       The API token. Can be used instead of email/password for authentication.
  * @param givenName   The user's given name.
  * @param familyName  The user's surname.
  * @param status      The user's status.
  * @param lang        The ISO 639-1 code of the user's preferred language.
  * @param groups      The groups that the user belongs to.
  * @param projects    The projects that the user belongs to.
  * @param sessionId   The sessionId,.
  * @param permissions The user's permissions.
  */
case class UserADM(id: IRI,
                   username: String,
                   email: String,
                   password: Option[String] = None,
                   token: Option[String] = None,
                   givenName: String,
                   familyName: String,
                   status: Boolean,
                   lang: String,
                   groups: Seq[GroupADM] = Vector.empty[GroupADM],
                   projects: Seq[ProjectADM] = Seq.empty[ProjectADM],
                   sessionId: Option[String] = None,
                   permissions: PermissionsDataADM = PermissionsDataADM()
                  ) extends Ordered[UserADM] {

    /**
      * Allows to sort collections of UserADM. Sorting is done by the id.
      */
    def compare(that: UserADM): Int = this.id.compareTo(that.id)

    /**
      * Check password (in clear text) using SCrypt. The password supplied in clear text is hashed and
      * compared against the stored hash.
      *
      * @param password the password to check.
      * @return true if password matches and false if password doesn't match.
      */
    def passwordMatch(password: String): Boolean = {
        this.password.exists {
            hashedPassword =>
                import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder
                val encoder = new SCryptPasswordEncoder
                encoder.matches(password, hashedPassword.toString)
        }
    }


    /**
      * Creating a [[UserADM]] of the requested type.
      *
      * @return a [[UserADM]]
      */
    def ofType(userTemplateType: UserInformationTypeADM): UserADM = {

        userTemplateType match {
            case UserInformationTypeADM.SHORT => {

                UserADM(
                    id = id,
                    username = username,
                    email = email,
                    password = None,
                    token = None,
                    givenName = givenName,
                    familyName = familyName,
                    status = status,
                    lang = lang,
                    groups = Seq.empty[GroupADM],
                    projects = Seq.empty[ProjectADM],
                    sessionId = None,
                    permissions = PermissionsDataADM()
                )
            }
            case UserInformationTypeADM.RESTRICTED => {

                UserADM(
                    id = id,
                    username = username,
                    email = email,
                    password = None,
                    token = None,
                    givenName = givenName,
                    familyName = familyName,
                    status = status,
                    lang = lang,
                    groups = groups,
                    projects = projects,
                    sessionId = None,
                    permissions = permissions
                )
            }
            case UserInformationTypeADM.FULL => {
                UserADM(
                    id = id,
                    username = username,
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
            }
            case _ => throw BadRequestException(s"The requested userTemplateType: $userTemplateType is invalid.")
        }
    }

    /* Is the user a member of the SystemAdmin group */
    def isSystemAdmin: Boolean = {
        permissions.groupsPerProject.getOrElse(OntologyConstants.KnoraBase.SystemProject, List.empty[IRI]).contains(OntologyConstants.KnoraBase.SystemAdmin)
    }

    def isSystemUser: Boolean = id.equalsIgnoreCase(OntologyConstants.KnoraBase.SystemUser)

    def isAnonymousUser: Boolean = id.equalsIgnoreCase(OntologyConstants.KnoraBase.AnonymousUser)

    def fullname: String = givenName + " " + familyName

    def getDigest: String = {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        val time = System.currentTimeMillis().toString
        val value = (time + this.toString).getBytes("UTF-8")
        md.digest(value).map("%02x".format(_)).mkString
    }

    def setSessionId(sessionId: String): UserADM = {
        UserADM(
            id = id,
            username = username,
            email = email,
            password = password,
            token = token,
            givenName = givenName,
            familyName = familyName,
            status = status,
            lang = lang,
            groups = groups,
            projects = projects,
            sessionId = Some(sessionId),
            permissions = permissions
        )
    }

    def isActive: Boolean = {
        status
    }

    def toJsValue: JsValue = UsersADMJsonProtocol.userADMFormat.write(this)


    // ToDo: Refactor by using implicit conversions (when I manage to understand them)
    // and probably value classes: https://docs.scala-lang.org/overviews/core/value-classes.html
    def asUserProfileV1: UserProfileV1 = {

        if (this.isAnonymousUser) {
            UserProfileV1()
        } else {

            val v1Groups: Seq[IRI] = groups.map(_.id)

            val projectsWithoutBuiltinProjects = projects.filter(_.id != OntologyConstants.KnoraBase.SystemProject).filter(_.id != OntologyConstants.KnoraBase.DefaultSharedOntologiesProject)
            val projectInfosV1 = projectsWithoutBuiltinProjects.map(_.asProjectInfoV1)
            val projects_info_v1: Map[IRI, ProjectInfoV1] = projectInfosV1.map(_.id).zip(projectInfosV1).toMap[IRI, ProjectInfoV1]

            UserProfileV1(
                userData = this.asUserDataV1,
                groups = v1Groups,
                projects_info = projects_info_v1,
                permissionData = PermissionsDataADM(
                    groupsPerProject = permissions.groupsPerProject,
                    administrativePermissionsPerProject = permissions.administrativePermissionsPerProject
                ),
                sessionId = sessionId
            )
        }
    }

    def asUserDataV1: UserDataV1 = {
        UserDataV1(
            user_id = if (this.isAnonymousUser) {
                None
            } else {
                Some(id)
            },
            email = Some(email),
            password = password,
            token = token,
            firstname = Some(givenName),
            lastname = Some(familyName),
            status = Some(status),
            lang = lang
        )
    }
}


/**
  * UserInformationTypeADM types:
  * full: everything
  * restricted: everything without sensitive information, i.e. token, password, session.
  * short: like restricted and additionally without groups, projects and permissions.
  *
  * Mainly used in combination with the 'ofType' method, to make sure that a request receiving this information
  * also returns the user profile of the correct type. Should be used in cases where we don't want to expose
  * sensitive information to the outside world. Since in API Admin [[UserADM]] is returned with some responses,
  * we use 'restricted' in those cases.
  */
object UserInformationTypeADM extends Enumeration {
    /* TODO: Extend to incorporate user privacy wishes */

    type UserInformationTypeADM = Value

    val SHORT = Value(0, "short") // only basic user information (restricted and additionally without grpuos
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

object UserIdentifierType extends Enumeration {

    type UserIdentifierType

    val IRI = Value(0, "iri")
    val EMAIL = Value(1, "email")
    val USERNAME = Value(3, "username")
}


/**
  * Represents the user's identifier. It can be an IRI, email, or username.
  * @param value the user's identifier.
  */
case class UserIdentifierADM(value: String) {

    // throws an exception if an empty string is used as an identifier value
    if (value.isEmpty) {
        throw BadRequestException("Empty user identifier is not allowed.")
    }

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    def nonEmpty: Boolean = value.nonEmpty

    def isEmpty: Boolean = value.isEmpty

    def hasType: UserIdentifierType.Value = {

        if (stringFormatter.isKnoraUserIriStr(value)) {
            UserIdentifierType.IRI
        } else if (stringFormatter.validateEmail(value).isDefined) {
            UserIdentifierType.EMAIL
        } else if (value.nonEmpty) {
            UserIdentifierType.USERNAME
        } else {
            // this can actually never happen
            throw BadRequestException("Empty user identifier is not allowed.")
        }
    }

    /**
      * Tries to return the value as an IRI.
      */
    def toIri: IRI = {
        if (this.hasType == UserIdentifierType.IRI) {
            stringFormatter.validateAndEscapeIri(value, throw DataConversionException(s"Could not convert $value to an IRI."))
        } else {
            throw DataConversionException(s"Identifier $value is not of the required 'UserIdentifierType.IRI' type.")
        }
    }

    /**
      * Returns an optional value of the identifier.
      */
    def toIriOption: Option[IRI] = {
        if (this.hasType == UserIdentifierType.IRI) {
            Some(stringFormatter.validateAndEscapeIri(value, throw DataConversionException(s"Could not convert $value to an IRI.")))
        } else {
            None
        }
    }

    /**
      * Returns an optional value of the identifier.
      */
    def toEmailOption: Option[IRI] = {
        if (this.hasType == UserIdentifierType.EMAIL) {
            Some(value)
        } else {
            None
        }
    }

    /**
      * Returns an optional value of the identifier.
      */
    def toUsernameOption: Option[IRI] = {
        if (this.hasType == UserIdentifierType.USERNAME) {
            Some(value)
        } else {
            None
        }
    }

}

/**
  * Payload used for updating of an existing user.
  *
  * @param email         the new email address. Needs to be unique on the server.
  * @param username      the new username.
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
case class UserUpdatePayloadADM(username: Option[String] = None,
                                email: Option[String] = None,
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
object UsersADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with ProjectsADMJsonProtocol with GroupsADMJsonProtocol with PermissionsADMJsonProtocol {

    implicit val userADMFormat: JsonFormat[UserADM] = jsonFormat13(UserADM)
    implicit val createUserApiRequestADMFormat: RootJsonFormat[CreateUserApiRequestADM] = jsonFormat8(CreateUserApiRequestADM)
    implicit val changeUserApiRequestADMFormat: RootJsonFormat[ChangeUserApiRequestADM] = jsonFormat(ChangeUserApiRequestADM, "username", "email", "givenName", "familyName", "lang", "requesterPassword", "newPassword", "status", "systemAdmin")
    implicit val usersGetResponseADMFormat: RootJsonFormat[UsersGetResponseADM] = jsonFormat1(UsersGetResponseADM)
    implicit val userProfileResponseADMFormat: RootJsonFormat[UserResponseADM] = jsonFormat1(UserResponseADM)
    implicit val userProjectMembershipsGetResponseADMFormat: RootJsonFormat[UserProjectMembershipsGetResponseADM] = jsonFormat1(UserProjectMembershipsGetResponseADM)
    implicit val userProjectAdminMembershipsGetResponseADMFormat: RootJsonFormat[UserProjectAdminMembershipsGetResponseADM] = jsonFormat1(UserProjectAdminMembershipsGetResponseADM)
    implicit val userGroupMembershipsGetResponseADMFormat: RootJsonFormat[UserGroupMembershipsGetResponseADM] = jsonFormat1(UserGroupMembershipsGetResponseADM)
    implicit val userOperationResponseADMFormat: RootJsonFormat[UserOperationResponseADM] = jsonFormat1(UserOperationResponseADM)
}
