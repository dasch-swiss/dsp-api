/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.usersmessages

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.messages.admin.responder.AdminKnoraResponseADM
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.api.model.UserDto
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.Group

/**
 * Represents an answer to a request for a list of all projects the user is member of.
 *
 * @param projects a sequence of projects the user is member of.
 */
case class UserProjectMembershipsGetResponseADM(projects: Seq[Project]) extends AdminKnoraResponseADM
object UserProjectMembershipsGetResponseADM {
  implicit val codec: JsonCodec[UserProjectMembershipsGetResponseADM] =
    DeriveJsonCodec.gen[UserProjectMembershipsGetResponseADM]
}

/**
 * Represents an answer to a request for a list of all projects the user is member of the project admin group.
 *
 * @param projects a sequence of projects the user is member of the project admin group.
 */
case class UserProjectAdminMembershipsGetResponseADM(projects: Seq[Project]) extends AdminKnoraResponseADM
object UserProjectAdminMembershipsGetResponseADM {
  implicit val codec: JsonCodec[UserProjectAdminMembershipsGetResponseADM] =
    DeriveJsonCodec.gen[UserProjectAdminMembershipsGetResponseADM]
}

/**
 * Represents an answer to a request for a list of all groups the user is member of.
 *
 * @param groups a sequence of groups the user is member of.
 */
case class UserGroupMembershipsGetResponseADM(groups: Seq[Group]) extends AdminKnoraResponseADM
object UserGroupMembershipsGetResponseADM {
  implicit val codec: JsonCodec[UserGroupMembershipsGetResponseADM] =
    DeriveJsonCodec.gen[UserGroupMembershipsGetResponseADM]
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
case class GroupMembersGetResponseADM(members: Seq[UserDto]) extends AdminKnoraResponseADM
object GroupMembersGetResponseADM {
  given JsonCodec[GroupMembersGetResponseADM] = DeriveJsonCodec.gen[GroupMembersGetResponseADM]

  def from(users: Seq[User]): GroupMembersGetResponseADM = GroupMembersGetResponseADM(users.map(UserDto.from))
}
