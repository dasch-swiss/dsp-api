/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.usersmessages

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.*

import java.util.UUID

import dsp.errors.BadRequestException
import dsp.valueobjects.LanguageCode
import org.knora.webapi.*
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.ResponderRequest.KnoraRequestADM
import org.knora.webapi.messages.admin.responder.AdminKnoraResponseADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsADMJsonProtocol
import org.knora.webapi.slice.admin.domain.model.*

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
 * An abstract trait representing message that can be sent to `UsersResponderADM`.
 */
sealed trait UsersResponderRequestADM extends KnoraRequestADM with RelayedMessage

/**
 * Get all information about all users in form of [[UsersGetResponseADM]]. The UsersResponderRequestADM returns either
 * something or a NotFound exception if there are no users found. Administration permission checking is performed.
 *
 * @param requestingUser         the user initiating the request.
 */
case class UsersGetRequestADM(requestingUser: User) extends UsersResponderRequestADM

/**
 * A message that requests a user's profile by IRI. A successful response will be a [[User]].
 *
 * @param identifier             the IRI of the user to be queried.
 * @param userInformationTypeADM the extent of the information returned.
 * @param requestingUser         the user initiating the request.
 */
case class UserGetByIriADM(
  identifier: UserIri,
  userInformationTypeADM: UserInformationTypeADM = UserInformationTypeADM.Short,
  requestingUser: User
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
case class UsersGetResponseADM(users: Seq[User]) extends AdminKnoraResponseADM {
  def toJsValue: JsValue = UsersADMJsonProtocol.usersGetResponseADMFormat.write(this)
}

/**
 * Represents an answer to a user profile request.
 *
 * @param user the user's information of the requested type.
 */
case class UserResponseADM(user: User) extends AdminKnoraResponseADM {
  def toJsValue: JsValue = UsersADMJsonProtocol.userProfileResponseADMFormat.write(this)
}

/**
 * Represents an answer to a request for a list of all projects the user is member of.
 *
 * @param projects a sequence of projects the user is member of.
 */
case class UserProjectMembershipsGetResponseADM(projects: Seq[ProjectADM]) extends AdminKnoraResponseADM {
  def toJsValue: JsValue = UsersADMJsonProtocol.userProjectMembershipsGetResponseADMFormat.write(this)
}

/**
 * Represents an answer to a request for a list of all projects the user is member of the project admin group.
 *
 * @param projects a sequence of projects the user is member of the project admin group.
 */
case class UserProjectAdminMembershipsGetResponseADM(projects: Seq[ProjectADM]) extends AdminKnoraResponseADM {
  def toJsValue: JsValue = UsersADMJsonProtocol.userProjectAdminMembershipsGetResponseADMFormat.write(this)
}

/**
 * Represents an answer to a request for a list of all groups the user is member of.
 *
 * @param groups a sequence of groups the user is member of.
 */
case class UserGroupMembershipsGetResponseADM(groups: Seq[GroupADM]) extends AdminKnoraResponseADM {
  def toJsValue: JsValue = UsersADMJsonProtocol.userGroupMembershipsGetResponseADMFormat.write(this)
}

/**
 * Represents an answer to a user creating/modifying operation.
 *
 * @param user the new user profile of the created/modified user.
 */
case class UserOperationResponseADM(user: User) extends AdminKnoraResponseADM {
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
 * Represents an answer to a group membership request.
 *
 * @param members the group's members.
 */
case class GroupMembersGetResponseADM(members: Seq[User]) extends AdminKnoraResponseADM {
  def toJsValue = UsersADMJsonProtocol.groupMembersGetResponseADMFormat.write(this)
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

  implicit val userADMFormat: JsonFormat[User] = jsonFormat11(User)
  implicit val groupMembersGetResponseADMFormat: RootJsonFormat[GroupMembersGetResponseADM] =
    jsonFormat(GroupMembersGetResponseADM, "members")
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
