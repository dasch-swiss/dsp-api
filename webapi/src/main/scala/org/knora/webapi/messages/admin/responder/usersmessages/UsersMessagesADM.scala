/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.usersmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import java.util.UUID

import dsp.errors.BadRequestException
import dsp.errors.DataConversionException
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.User._
import org.knora.webapi._
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.ResponderRequest.KnoraRequestADM
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.KnoraResponseADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsADMJsonProtocol
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoV1
import org.knora.webapi.messages.v1.responder.usermessages._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
 * Represents an API request payload that asks the Knora API server to create a new user.
 *
 * @param id          the optional IRI of the user to be created (unique).
 * @param username    the username of the user to be created (unique).
 * @param email       the email of the user to be created (unique).
 * @param givenName   the given name of the user to be created.
 * @param familyName  the family name of the user to be created
 * @param password    the password of the user to be created.
 * @param status      the status of the user to be created (active = true, inactive = false).
 * @param lang        the default language of the user to be created.
 * @param systemAdmin the system admin membership.
 */
case class CreateUserApiRequestADM(
  id: Option[IRI] = None,
  username: String,
  email: String,
  givenName: String,
  familyName: String,
  password: String,
  status: Boolean,
  lang: String,
  systemAdmin: Boolean
) {

  def toJsValue: JsValue = UsersADMJsonProtocol.createUserApiRequestADMFormat.write(this)
}

/**
 * Represents an API request payload that asks the Knora API server to update an existing user. Information that can
 * be changed are: user's username, email, given name, family name, language, user status, and system admin membership.
 *
 * @param username          the new username. Needs to be unique on the server.
 * @param email             the new email address. Needs to be unique on the server.
 * @param givenName         the new given name.
 * @param familyName        the new family name.
 * @param lang              the new ISO 639-1 code of the new preferred language.
 * @param status            the new user status (active = true, inactive = false).
 * @param systemAdmin       the new system admin membership status.
 */
case class ChangeUserApiRequestADM(
  username: Option[String] = None,
  email: Option[String] = None,
  givenName: Option[String] = None,
  familyName: Option[String] = None,
  lang: Option[String] = None,
  status: Option[Boolean] = None,
  systemAdmin: Option[Boolean] = None
) {

  val parametersCount: Int = List(
    username,
    email,
    givenName,
    familyName,
    lang,
    status,
    systemAdmin
  ).flatten.size

  // something needs to be sent, i.e. everything 'None' is not allowed
  if (parametersCount == 0) throw BadRequestException("No data sent in API request.")

  /* check that only allowed information for the 3 cases (changing status, systemAdmin and basic information) is sent and not more. */

  // change status case
  if (status.isDefined) {
    if (parametersCount > 1) throw BadRequestException("Too many parameters sent for change request.")
  }

  // change system admin membership case
  if (systemAdmin.isDefined) {
    if (parametersCount > 1) throw BadRequestException("Too many parameters sent for change request.")
  }

  // change basic user information case
  if (parametersCount > 5) throw BadRequestException("Too many parameters sent for change request.")

  def toJsValue: JsValue = UsersADMJsonProtocol.changeUserApiRequestADMFormat.write(this)
}

case class ChangeUserPasswordApiRequestADM(requesterPassword: Option[String], newPassword: Option[String]) {
  def toJsValue: JsValue = UsersADMJsonProtocol.changeUserPasswordApiRequestADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
 * An abstract trait representing message that can be sent to `UsersResponderADM`.
 */
sealed trait UsersResponderRequestADM extends KnoraRequestADM with RelayedMessage

/**
 * Get all information about all users in form of a sequence of [[UserADM]]. Returns an empty sequence if
 * no users are found. Administration permission checking is skipped.
 *
 * @param userInformationTypeADM the extent of the information returned.
 * @param requestingUser         the user that is making the request.
 */
case class UsersGetADM(
  userInformationTypeADM: UserInformationTypeADM = UserInformationTypeADM.Short,
  requestingUser: UserADM
) extends UsersResponderRequestADM

/**
 * Get all information about all users in form of [[UsersGetResponseADM]]. The UsersResponderRequestADM returns either
 * something or a NotFound exception if there are no users found. Administration permission checking is performed.
 *
 * @param userInformationTypeADM the extent of the information returned.
 * @param requestingUser         the user initiating the request.
 */
case class UsersGetRequestADM(
  userInformationTypeADM: UserInformationTypeADM = UserInformationTypeADM.Short,
  requestingUser: UserADM
) extends UsersResponderRequestADM

/**
 * A message that requests a user's profile either by IRI, username, or email. A successful response will be a [[UserADM]].
 *
 * @param identifier             the IRI, email, or username of the user to be queried.
 * @param userInformationTypeADM the extent of the information returned.
 * @param requestingUser         the user initiating the request.
 */
case class UserGetADM(
  identifier: UserIdentifierADM,
  userInformationTypeADM: UserInformationTypeADM = UserInformationTypeADM.Short,
  requestingUser: UserADM
) extends UsersResponderRequestADM {}

/**
 * A message that requests a user's profile either by IRI, username, or email. A successful response will be a [[UserResponseADM]].
 *
 * @param identifier             the IRI, email, or username of the user to be queried.
 * @param userInformationTypeADM the extent of the information returned.
 * @param requestingUser         the user initiating the request.
 */
case class UserGetRequestADM(
  identifier: UserIdentifierADM,
  userInformationTypeADM: UserInformationTypeADM = UserInformationTypeADM.Short,
  requestingUser: UserADM
) extends UsersResponderRequestADM {}

/**
 * Requests the creation of a new user.
 *
 * @param userCreatePayloadADM    the [[UserCreatePayloadADM]] information used for creating the new user.
 * @param requestingUser       the user creating the new user.
 * @param apiRequestID         the ID of the API request.
 */
case class UserCreateRequestADM(
  userCreatePayloadADM: UserCreatePayloadADM,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends UsersResponderRequestADM

/**
 * Request updating of an existing user.
 *
 * @param userIri              the IRI of the user to be updated.
 * @param userUpdateBasicInformationPayload    the [[UserUpdateBasicInformationPayloadADM]] object containing the data to be updated.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class UserChangeBasicInformationRequestADM(
  userIri: IRI,
  userUpdateBasicInformationPayload: UserUpdateBasicInformationPayloadADM,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends UsersResponderRequestADM

/**
 * Request updating the users password.
 *
 * @param userIri              the IRI of the user to be updated.
 * @param userUpdatePasswordPayload    the [[UserUpdatePasswordPayloadADM]] object containing the old and new password.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class UserChangePasswordRequestADM(
  userIri: IRI,
  userUpdatePasswordPayload: UserUpdatePasswordPayloadADM,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends UsersResponderRequestADM

/**
 * Request updating the users status ('knora-base:isActiveUser' property)
 *
 * @param userIri              the IRI of the user to be updated.
 * @param status               the [[Status]] containing the new status (true / false).
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class UserChangeStatusRequestADM(
  userIri: IRI,
  status: UserStatus,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends UsersResponderRequestADM

/**
 * Request updating the users system admin status ('knora-base:isInSystemAdminGroup' property)
 *
 * @param userIri              the IRI of the user to be updated.
 * @param systemAdmin          the [[SystemAdmin]] value object containing the new system admin membership status (true / false).
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class UserChangeSystemAdminMembershipStatusRequestADM(
  userIri: IRI,
  systemAdmin: SystemAdmin,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends UsersResponderRequestADM

/**
 * Requests user's project memberships.
 *
 * @param userIri              the IRI of the user.
 * @param requestingUser       the user initiating the request.
 */
case class UserProjectMembershipsGetRequestADM(
  userIri: IRI,
  requestingUser: UserADM
) extends UsersResponderRequestADM

/**
 * Requests adding the user to a project.
 *
 * @param userIri              the IRI of the user to be updated.
 * @param projectIri           the IRI of the project.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class UserProjectMembershipAddRequestADM(
  userIri: IRI,
  projectIri: IRI,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends UsersResponderRequestADM

/**
 * Requests removing the user from a project.
 *
 * @param userIri              the IRI of the user to be updated.
 * @param projectIri           the IRI of the project.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class UserProjectMembershipRemoveRequestADM(
  userIri: IRI,
  projectIri: IRI,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends UsersResponderRequestADM

/**
 * Requests user's project admin memberships.
 *
 * @param userIri              the IRI of the user.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class UserProjectAdminMembershipsGetRequestADM(
  userIri: IRI,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends UsersResponderRequestADM

/**
 * Requests adding the user to a project as project admin.
 *
 * @param userIri              the IRI of the user to be updated.
 * @param projectIri           the IRI of the project.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class UserProjectAdminMembershipAddRequestADM(
  userIri: IRI,
  projectIri: IRI,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends UsersResponderRequestADM

/**
 * Requests removing the user from a project as project admin.
 *
 * @param userIri              the IRI of the user to be updated.
 * @param projectIri           the IRI of the project.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class UserProjectAdminMembershipRemoveRequestADM(
  userIri: IRI,
  projectIri: IRI,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends UsersResponderRequestADM

/**
 * Requests user's group memberships.
 *
 * @param userIri              the IRI of the user.
 * @param requestingUser       the user initiating the request.
 */
case class UserGroupMembershipsGetRequestADM(
  userIri: IRI,
  requestingUser: UserADM
) extends UsersResponderRequestADM

/**
 * Requests adding the user to a group.
 *
 * @param userIri              the IRI of the user to be updated.
 * @param groupIri             the IRI of the group.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class UserGroupMembershipAddRequestADM(
  userIri: IRI,
  groupIri: IRI,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends UsersResponderRequestADM

/**
 * Requests removing the user from a group.
 *
 * @param userIri              the IRI of the user to be updated.
 * @param groupIri             the IRI of the group.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class UserGroupMembershipRemoveRequestADM(
  userIri: IRI,
  groupIri: IRI,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends UsersResponderRequestADM

// Responses

/**
 * Represents an answer to a request for a list of all users.
 *
 * @param users a sequence of user profiles of the requested type.
 */
case class UsersGetResponseADM(users: Seq[UserADM]) extends KnoraResponseADM {
  def toJsValue: JsValue = UsersADMJsonProtocol.usersGetResponseADMFormat.write(this)
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
final case class UserADM(
  id: IRI,
  username: String,
  email: String,
  givenName: String,
  familyName: String,
  status: Boolean,
  lang: String,
  password: Option[String] = None,
  token: Option[String] = None,
  groups: Seq[GroupADM] = Vector.empty[GroupADM],
  projects: Seq[ProjectADM] = Seq.empty[ProjectADM],
  sessionId: Option[String] = None,
  permissions: PermissionsDataADM = PermissionsDataADM()
) extends Ordered[UserADM] { self =>

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
  def passwordMatch(password: String): Boolean =
    this.password.exists { hashedPassword =>
      // check which type of hash we have
      if (hashedPassword.startsWith("$e0801$")) {
        // SCrypt
        import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder
        val encoder = new SCryptPasswordEncoder(16384, 8, 1, 32, 64)
        encoder.matches(password, hashedPassword)
      } else if (hashedPassword.startsWith("$2a$")) {
        // BCrypt
        import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
        val encoder = new BCryptPasswordEncoder()
        encoder.matches(password, hashedPassword)
      } else {
        // SHA-1
        val md = java.security.MessageDigest.getInstance("SHA-1")
        md.digest(password.getBytes("UTF-8")).map("%02x".format(_)).mkString.equals(hashedPassword)
      }
    }

  /**
   * Creating a [[UserADM]] of the requested type.
   *
   * @return a [[UserADM]]
   */
  def ofType(userTemplateType: UserInformationTypeADM): UserADM =
    userTemplateType match {
      case UserInformationTypeADM.Public =>
        self.copy(
          username = "",
          email = "",
          password = None,
          token = None,
          status = false,
          lang = "",
          groups = Seq.empty[GroupADM],
          projects = Seq.empty[ProjectADM],
          sessionId = None,
          permissions = PermissionsDataADM()
        )
      case UserInformationTypeADM.Short =>
        self.copy(
          password = None,
          token = None,
          groups = Seq.empty[GroupADM],
          projects = Seq.empty[ProjectADM],
          sessionId = None,
          permissions = PermissionsDataADM()
        )
      case UserInformationTypeADM.Restricted =>
        self.copy(
          password = None,
          token = None,
          sessionId = None
        )
      case UserInformationTypeADM.Full =>
        self
      case _ => throw BadRequestException(s"The requested userTemplateType: $userTemplateType is invalid.")
    }

  /**
   * Given an identifier, returns true if it is the same user, and false if not.
   */
  def isSelf(identifier: UserIdentifierADM): Boolean = {

    val iriEquals      = identifier.toIriOption.contains(id)
    val emailEquals    = identifier.toEmailOption.contains(email)
    val usernameEquals = identifier.toUsernameOption.contains(username)

    iriEquals || emailEquals || usernameEquals
  }

  /**
   * Is the user a member of the SystemAdmin group
   */
  def isSystemAdmin: Boolean =
    permissions.groupsPerProject
      .getOrElse(OntologyConstants.KnoraAdmin.SystemProject, List.empty[IRI])
      .contains(OntologyConstants.KnoraAdmin.SystemAdmin)

  def isSystemUser: Boolean = id.equalsIgnoreCase(OntologyConstants.KnoraAdmin.SystemUser)

  def fullname: String = givenName + " " + familyName

  def getDigest: String = {
    val md    = java.security.MessageDigest.getInstance("SHA-1")
    val time  = System.currentTimeMillis().toString
    val value = (time + this.toString).getBytes("UTF-8")
    md.digest(value).map("%02x".format(_)).mkString
  }

  def setSessionId(sessionId: String): UserADM =
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

  def isActive: Boolean =
    status

  def toJsValue: JsValue = UsersADMJsonProtocol.userADMFormat.write(this)

  // ToDo: Refactor by using implicit conversions (when I manage to understand them)
  // and probably value classes: https://docs.scala-lang.org/overviews/core/value-classes.html
  def asUserProfileV1: UserProfileV1 =
    if (this.isAnonymousUser) {
      UserProfileV1()
    } else {

      val v1Groups: Seq[IRI] = groups.map(_.id)

      val projectsWithoutBuiltinProjects = projects
        .filter(_.id != OntologyConstants.KnoraAdmin.SystemProject)
        .filter(_.id != OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject)
      val projectInfosV1 = projectsWithoutBuiltinProjects.map(_.asProjectInfoV1)
      val projects_info_v1: Map[IRI, ProjectInfoV1] =
        projectInfosV1.map(_.id).zip(projectInfosV1).toMap[IRI, ProjectInfoV1]

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

  def asUserDataV1: UserDataV1 =
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

  def isAnonymousUser: Boolean = id.equalsIgnoreCase(OntologyConstants.KnoraAdmin.AnonymousUser)
}

/**
 * UserInformationTypeADM types:
 * full: everything
 * restricted: everything without sensitive information, i.e. token, password, session.
 * short: like restricted and additionally without groups, projects and permissions.
 * public: temporary: givenName, familyName
 *
 * Mainly used in combination with the 'ofType' method, to make sure that a request receiving this information
 * also returns the user profile of the correct type. Should be used in cases where we don't want to expose
 * sensitive information to the outside world. Since in API Admin [[UserADM]] is returned with some responses,
 * we use 'restricted' in those cases.
 */
sealed trait UserInformationTypeADM
object UserInformationTypeADM {
  case object Public     extends UserInformationTypeADM
  case object Short      extends UserInformationTypeADM
  case object Restricted extends UserInformationTypeADM
  case object Full       extends UserInformationTypeADM

  // throw InconsistentRepositoryDataException(s"User profile type not supported: $name")
}

/**
 * Represents the type of a user identifier.
 */
sealed trait UserIdentifierType
object UserIdentifierType {
  case object Iri      extends UserIdentifierType
  case object Email    extends UserIdentifierType
  case object Username extends UserIdentifierType
}

/**
 * Represents the user's identifier. It can be an IRI, email, or username.
 *
 * @param maybeIri      the user's IRI.
 * @param maybeEmail    the user's email.
 * @param maybeUsername the user's username.
 */
sealed abstract case class UserIdentifierADM private (
  maybeIri: Option[IRI] = None,
  maybeEmail: Option[String] = None,
  maybeUsername: Option[String] = None
) {

  // squash and return value.
  val value: String = List(
    maybeIri,
    maybeEmail,
    maybeUsername
  ).flatten.head

  // validate and escape

  def hasType: UserIdentifierType =
    if (maybeIri.isDefined) {
      UserIdentifierType.Iri
    } else if (maybeEmail.isDefined) {
      UserIdentifierType.Email
    } else {
      UserIdentifierType.Username
    }

  /**
   * Tries to return the value as an IRI.
   */
  def toIri: IRI =
    maybeIri.getOrElse(
      throw DataConversionException(s"Identifier $value is not of the required 'UserIdentifierType.IRI' type.")
    )

  /**
   * Returns an optional value of the identifier.
   */
  def toIriOption: Option[IRI] =
    maybeIri

  /**
   * Tries to return the value as email.
   */
  def toEmail: String =
    maybeEmail.getOrElse(
      throw DataConversionException(s"Identifier $value is not of the required 'UserIdentifierType.EMAIL' type.")
    )

  /**
   * Returns an optional value of the identifier.
   */
  def toEmailOption: Option[String] =
    maybeEmail

  /**
   * Tries to return the value as username.
   */
  def toUsername: String =
    maybeUsername.getOrElse(
      throw DataConversionException(s"Identifier $value is not of the required 'UserIdentifierType.USERNAME' type.")
    )

  /**
   * Returns an optional value of the identifier.
   */
  def toUsernameOption: Option[String] =
    maybeUsername

  /**
   * Returns the string representation
   */
  override def toString: String =
    s"UserIdentifierADM(${this.value})"

}

/**
 * The UserIdentifierADM factory object, making sure that all necessary checks are performed and all inputs
 * validated and escaped.
 */
object UserIdentifierADM {
  def apply(maybeIri: Option[String] = None, maybeEmail: Option[String] = None, maybeUsername: Option[String] = None)(
    implicit sf: StringFormatter
  ): UserIdentifierADM = {

    val parametersCount: Int = List(
      maybeIri,
      maybeEmail,
      maybeUsername
    ).flatten.size

    // something needs to be set
    if (parametersCount == 0) throw BadRequestException("Empty user identifier is not allowed.")

    if (parametersCount > 1) throw BadRequestException("Only one option allowed for user identifier.")

    new UserIdentifierADM(
      maybeIri =
        sf.validateAndEscapeOptionalUserIri(maybeIri, throw BadRequestException(s"Invalid user IRI $maybeIri")),
      maybeEmail =
        sf.validateAndEscapeOptionalEmail(maybeEmail, throw BadRequestException(s"Invalid email $maybeEmail")),
      maybeUsername = sf.validateAndEscapeOptionalUsername(
        maybeUsername,
        throw BadRequestException(s"Invalid username $maybeUsername")
      )
    ) {}
  }
}

/**
 * Payload used for updating an existing user.
 *
 * @param username      the new username.
 * @param email         the new email address. Needs to be unique on the server.
 * @param givenName     the new given name.
 * @param familyName    the new family name.
 * @param status        the new status.
 * @param lang          the new language.
 * @param projects      the new project memberships list.
 * @param projectsAdmin the new projects admin membership list.
 * @param groups        the new group memberships list.
 * @param systemAdmin   the new system admin membership
 */
case class UserChangeRequestADM(
  username: Option[Username] = None,
  email: Option[Email] = None,
  givenName: Option[GivenName] = None,
  familyName: Option[FamilyName] = None,
  status: Option[UserStatus] = None,
  lang: Option[LanguageCode] = None,
  projects: Option[Seq[IRI]] = None,
  projectsAdmin: Option[Seq[IRI]] = None,
  groups: Option[Seq[IRI]] = None,
  systemAdmin: Option[SystemAdmin] = None
) {

  val parametersCount: Int = List(
    username,
    email,
    givenName,
    familyName,
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

  // change status case
  if (status.isDefined && parametersCount > 1) {
    throw BadRequestException("Too many parameters sent for user status change.")
  }

  // change system admin membership case
  if (systemAdmin.isDefined && parametersCount > 1) {
    throw BadRequestException("Too many parameters sent for system admin membership change.")
  }

  // change project memberships (could also involve changing projectAdmin memberships)
  if (
    projects.isDefined && projectsAdmin.isDefined && parametersCount > 2 ||
    projects.isDefined && !projectsAdmin.isDefined && parametersCount > 1
  ) {
    throw BadRequestException("Too many parameters sent for project membership change.")
  }

  // change projectAdmin memberships only (without changing project memberships)
  if (projectsAdmin.isDefined && !projects.isDefined && parametersCount > 1) {
    throw BadRequestException("Too many parameters sent for projectAdmin membership change.")
  }

  // change group memberships
  if (groups.isDefined && parametersCount > 1) {
    throw BadRequestException("Too many parameters sent for group membership change.")
  }

  // change basic user information case
  if (parametersCount > 5) {
    throw BadRequestException("Too many parameters sent for basic user information change.")
  }
}

/**
 * Payload used for updating basic information of an existing user.
 *
 * @param username      the new username.
 * @param email         the new email address. Needs to be unique on the server.
 * @param givenName     the new given name.
 * @param familyName    the new family name.
 * @param lang          the new language.
 */
case class UserUpdateBasicInformationPayloadADM(
  username: Option[Username] = None,
  email: Option[Email] = None,
  givenName: Option[GivenName] = None,
  familyName: Option[FamilyName] = None,
  lang: Option[LanguageCode] = None
) {

  val parametersCount: Int = List(
    username,
    email,
    givenName,
    familyName,
    lang
  ).flatten.size

  // something needs to be sent, i.e. everything 'None' is not allowed
  if (parametersCount == 0) {
    throw BadRequestException("No data sent in API request.")
  }
}

case class UserUpdatePasswordPayloadADM(requesterPassword: Password, newPassword: Password) {}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
 * A spray-json protocol for formatting objects as JSON.
 */
object UsersADMJsonProtocol
    extends SprayJsonSupport
    with DefaultJsonProtocol
    with ProjectsADMJsonProtocol
    with GroupsADMJsonProtocol
    with PermissionsADMJsonProtocol {

  implicit val userADMFormat: JsonFormat[UserADM] = jsonFormat13(UserADM)
  implicit val createUserApiRequestADMFormat: RootJsonFormat[CreateUserApiRequestADM] = jsonFormat(
    CreateUserApiRequestADM,
    "id",
    "username",
    "email",
    "givenName",
    "familyName",
    "password",
    "status",
    "lang",
    "systemAdmin"
  )
  implicit val changeUserApiRequestADMFormat: RootJsonFormat[ChangeUserApiRequestADM] =
    jsonFormat(ChangeUserApiRequestADM, "username", "email", "givenName", "familyName", "lang", "status", "systemAdmin")
  implicit val changeUserPasswordApiRequestADMFormat: RootJsonFormat[ChangeUserPasswordApiRequestADM] = jsonFormat(
    ChangeUserPasswordApiRequestADM,
    "requesterPassword",
    "newPassword"
  )
  implicit val usersGetResponseADMFormat: RootJsonFormat[UsersGetResponseADM] = jsonFormat1(UsersGetResponseADM)
  implicit val userProfileResponseADMFormat: RootJsonFormat[UserResponseADM]  = jsonFormat1(UserResponseADM)
  implicit val userProjectMembershipsGetResponseADMFormat: RootJsonFormat[UserProjectMembershipsGetResponseADM] =
    jsonFormat1(UserProjectMembershipsGetResponseADM)
  implicit val userProjectAdminMembershipsGetResponseADMFormat
    : RootJsonFormat[UserProjectAdminMembershipsGetResponseADM] = jsonFormat1(UserProjectAdminMembershipsGetResponseADM)
  implicit val userGroupMembershipsGetResponseADMFormat: RootJsonFormat[UserGroupMembershipsGetResponseADM] =
    jsonFormat1(UserGroupMembershipsGetResponseADM)
  implicit val userOperationResponseADMFormat: RootJsonFormat[UserOperationResponseADM] = jsonFormat1(
    UserOperationResponseADM
  )
}
