/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.usersmessages

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.*
import zio.prelude.Validation

import java.util.UUID
import dsp.errors.BadRequestException
import dsp.errors.DataConversionException
import dsp.errors.ValidationException
import dsp.valueobjects.Iri
import dsp.valueobjects.LanguageCode
import org.knora.webapi.*
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.ResponderRequest.KnoraRequestADM
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.{AdminKnoraResponseADM, KnoraResponseADM}
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsADMJsonProtocol
import org.knora.webapi.slice.admin.domain.model.*

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
 * Get all information about all users in form of a sequence of [[User]]. Returns an empty sequence if
 * no users are found. Administration permission checking is skipped.
 *
 * @param userInformationTypeADM the extent of the information returned.
 * @param requestingUser         the user that is making the request.
 */
case class UsersGetADM(
  userInformationTypeADM: UserInformationTypeADM = UserInformationTypeADM.Short,
  requestingUser: User
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
  requestingUser: User
) extends UsersResponderRequestADM

/**
 * A message that requests a user's profile either by IRI, username, or email. A successful response will be a [[User]].
 *
 * @param identifier             the IRI, email, or username of the user to be queried.
 * @param userInformationTypeADM the extent of the information returned.
 * @param requestingUser         the user initiating the request.
 */
case class UserGetADM(
  identifier: UserIdentifierADM,
  userInformationTypeADM: UserInformationTypeADM = UserInformationTypeADM.Short,
  requestingUser: User
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
  requestingUser: User
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
  requestingUser: User,
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
  requestingUser: User,
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
  requestingUser: User,
  apiRequestID: UUID
) extends UsersResponderRequestADM

/**
 * Request updating the users status ('knora-base:isActiveUser' property)
 *
 * @param userIri              the IRI of the user to be updated.
 * @param status               the [[UserStatus]] containing the new status (true / false).
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class UserChangeStatusRequestADM(
  userIri: IRI,
  status: UserStatus,
  requestingUser: User,
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
  requestingUser: User,
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
  requestingUser: User
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
  requestingUser: User,
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
  requestingUser: User,
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
  requestingUser: User,
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
  requestingUser: User,
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
  requestingUser: User,
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
  requestingUser: User
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
  requestingUser: User,
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
  requestingUser: User,
  apiRequestID: UUID
) extends UsersResponderRequestADM

// Responses

/**
 * Represents an answer to a request for a list of all users.
 *
 * @param users a sequence of user profiles of the requested type.
 */
case class UsersGetResponseADM(users: Seq[User]) extends KnoraResponseADM {
  def toJsValue: JsValue = UsersADMJsonProtocol.usersGetResponseADMFormat.write(this)
}

/**
 * Represents an answer to a user profile request.
 *
 * @param user the user's information of the requested type.
 */
case class UserResponseADM(user: User) extends KnoraResponseADM {
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
case class UserOperationResponseADM(user: User) extends KnoraResponseADM {
  def toJsValue: JsValue = UsersADMJsonProtocol.userOperationResponseADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
 * UserInformationTypeADM types:
 * full: everything
 * restricted: everything without sensitive information, i.e. token, password, session.
 * short: like restricted and additionally without groups, projects and permissions.
 * public: temporary: givenName, familyName
 *
 * Mainly used in combination with the 'ofType' method, to make sure that a request receiving this information
 * also returns the user profile of the correct type. Should be used in cases where we don't want to expose
 * sensitive information to the outside world. Since in API Admin [[User]] is returned with some responses,
 * we use 'restricted' in those cases.
 */
sealed trait UserInformationTypeADM
object UserInformationTypeADM {
  case object Public     extends UserInformationTypeADM
  case object Short      extends UserInformationTypeADM
  case object Restricted extends UserInformationTypeADM
  case object Full       extends UserInformationTypeADM

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

    if (parametersCount == 0) throw BadRequestException("Empty user identifier is not allowed.")
    if (parametersCount > 1) throw BadRequestException("Only one option allowed for user identifier.")

    val userIri = maybeIri.map(iri =>
      Iri.validateAndEscapeUserIri(iri).getOrElse(throw BadRequestException(s"Invalid user IRI $iri"))
    )

    val userEmail =
      maybeEmail
        .map(e => Email.from(e).getOrElse(throw BadRequestException(s"Invalid email $e")))
        .map(_.value)
    val username =
      maybeUsername.map(u => Username.from(u).getOrElse(throw BadRequestException(s"Invalid username $u")).value)

    new UserIdentifierADM(
      maybeIri = userIri,
      maybeEmail = userEmail,
      maybeUsername = username
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
    projects.isDefined && projectsAdmin.isEmpty && parametersCount > 1
  ) {
    throw BadRequestException("Too many parameters sent for project membership change.")
  }

  // change projectAdmin memberships only (without changing project memberships)
  if (projectsAdmin.isDefined && projects.isEmpty && parametersCount > 1) {
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
  def isAtLeastOneParamSet: Boolean = Seq(username, email, givenName, familyName, lang).flatten.nonEmpty
}

object UserUpdateBasicInformationPayloadADM {

  private def validateWithOptionOrNone[I, O, E](opt: Option[I], f: I => Validation[E, O]): Validation[E, Option[O]] =
    opt.map(f(_).map(Some(_))).getOrElse(Validation.succeed(None))

  def make(req: ChangeUserApiRequestADM): Validation[ValidationException, UserUpdateBasicInformationPayloadADM] =
    Validation.validateWith(
      validateWithOptionOrNone(req.username, Username.validationFrom).mapError(ValidationException(_)),
      validateWithOptionOrNone(req.email, Email.validationFrom).mapError(ValidationException(_)),
      validateWithOptionOrNone(req.givenName, GivenName.validationFrom).mapError(ValidationException(_)),
      validateWithOptionOrNone(req.familyName, FamilyName.validationFrom).mapError(ValidationException(_)),
      validateWithOptionOrNone(req.lang, LanguageCode.make)
    )(UserUpdateBasicInformationPayloadADM.apply)
}

case class UserUpdatePasswordPayloadADM(requesterPassword: Password, newPassword: Password)
object UserUpdatePasswordPayloadADM {
  def make(apiRequest: ChangeUserPasswordApiRequestADM): Validation[String, UserUpdatePasswordPayloadADM] = {
    val requesterPasswordValidation = apiRequest.requesterPassword
      .map(Password.validationFrom)
      .getOrElse(Validation.fail("The requester's password is missing."))
    val newPasswordValidation = apiRequest.newPassword
      .map(Password.validationFrom)
      .getOrElse(Validation.fail("The new password is missing."))
    Validation.validateWith(requesterPasswordValidation, newPasswordValidation)(UserUpdatePasswordPayloadADM.apply)
  }
}

/**
 * Represents an answer to a group membership request.
 *
 * @param members the group's members.
 */
case class GroupMembersGetResponseADM(members: Seq[User]) extends AdminKnoraResponseADM {
  def toJsValue = UsersADMJsonProtocol.groupMembersGetResponseADMFormat.write(this)
}

final case class UserCreatePayloadADM(
  id: Option[UserIri] = None,
  username: Username,
  email: Email,
  givenName: GivenName,
  familyName: FamilyName,
  password: Password,
  status: UserStatus,
  lang: LanguageCode,
  systemAdmin: SystemAdmin
)

object UserCreatePayloadADM {
  def make(apiRequest: CreateUserApiRequestADM): Validation[String, UserCreatePayloadADM] =
    Validation
      .validateWith(
        apiRequest.id
          .map(UserIri.validationFrom(_).map(Some(_)).mapError(ValidationException(_)))
          .getOrElse(Validation.succeed(None)),
        Username.validationFrom(apiRequest.username).mapError(ValidationException(_)),
        Email.validationFrom(apiRequest.email).mapError(ValidationException(_)),
        GivenName.validationFrom(apiRequest.givenName).mapError(ValidationException(_)),
        FamilyName.validationFrom(apiRequest.familyName).mapError(ValidationException(_)),
        Password.validationFrom(apiRequest.password).mapError(ValidationException(_)),
        Validation.succeed(UserStatus.from(apiRequest.status)),
        LanguageCode.make(apiRequest.lang),
        Validation.succeed(SystemAdmin.from(apiRequest.systemAdmin))
      )(UserCreatePayloadADM.apply)
      .mapError(_.getMessage)
}

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

  implicit val userADMFormat: JsonFormat[User] = jsonFormat12(User)
  implicit val groupMembersGetResponseADMFormat: RootJsonFormat[GroupMembersGetResponseADM] =
    jsonFormat(GroupMembersGetResponseADM, "members")
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
