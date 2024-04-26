/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.usersmessages

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import org.knora.webapi.messages.admin.responder.AdminKnoraResponseADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.projectsmessages.Project
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsADMJsonProtocol
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model._

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
case class UserProjectMembershipsGetResponseADM(projects: Seq[Project]) extends AdminKnoraResponseADM {
  def toJsValue: JsValue = UsersADMJsonProtocol.userProjectMembershipsGetResponseADMFormat.write(this)
}

/**
 * Represents an answer to a request for a list of all projects the user is member of the project admin group.
 *
 * @param projects a sequence of projects the user is member of the project admin group.
 */
case class UserProjectAdminMembershipsGetResponseADM(projects: Seq[Project]) extends AdminKnoraResponseADM {
  def toJsValue: JsValue = UsersADMJsonProtocol.userProjectAdminMembershipsGetResponseADMFormat.write(this)
}

/**
 * Represents an answer to a request for a list of all groups the user is member of.
 *
 * @param groups a sequence of groups the user is member of.
 */
case class UserGroupMembershipsGetResponseADM(groups: Seq[Group]) extends AdminKnoraResponseADM {
  def toJsValue: JsValue = UsersADMJsonProtocol.userGroupMembershipsGetResponseADMFormat.write(this)
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
sealed trait UserInformationType
object UserInformationType {
  case object Public     extends UserInformationType
  case object Short      extends UserInformationType
  case object Restricted extends UserInformationType
  case object Full       extends UserInformationType

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

  implicit val userADMFormat: JsonFormat[User] =
    jsonFormat11(User.apply)
  implicit val groupMembersGetResponseADMFormat: RootJsonFormat[GroupMembersGetResponseADM] =
    jsonFormat(GroupMembersGetResponseADM.apply, "members")
  implicit val usersGetResponseADMFormat: RootJsonFormat[UsersGetResponseADM] =
    jsonFormat1(UsersGetResponseADM.apply)
  implicit val userProfileResponseADMFormat: RootJsonFormat[UserResponseADM] =
    jsonFormat1(UserResponseADM.apply)
  implicit val userProjectMembershipsGetResponseADMFormat: RootJsonFormat[UserProjectMembershipsGetResponseADM] =
    jsonFormat1(UserProjectMembershipsGetResponseADM.apply)
  implicit val userProjectAdminMembershipsGetResponseADMFormat
    : RootJsonFormat[UserProjectAdminMembershipsGetResponseADM] =
    jsonFormat1(UserProjectAdminMembershipsGetResponseADM.apply)
  implicit val userGroupMembershipsGetResponseADMFormat: RootJsonFormat[UserGroupMembershipsGetResponseADM] =
    jsonFormat1(UserGroupMembershipsGetResponseADM.apply)
}
