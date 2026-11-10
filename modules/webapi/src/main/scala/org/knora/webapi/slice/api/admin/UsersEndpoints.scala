/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.tapir.*
import sttp.tapir.codec.refined.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.*
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.json.interop.refined.*

import org.knora.webapi.messages.admin.responder.usersmessages.UserGroupMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectAdminMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectMembershipsGetResponseADM
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.api.admin.PathVars.emailPathVar
import org.knora.webapi.slice.api.admin.PathVars.usernamePathVar
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.BasicUserInformationChangeRequest
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.PasswordChangeRequest
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.StatusChangeRequest
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.SystemAdminChangeRequest
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.UserCreateRequest
import org.knora.webapi.slice.api.admin.service.UserRestService.UserResponse
import org.knora.webapi.slice.api.admin.service.UserRestService.UsersResponse
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.Policy
import org.knora.webapi.slice.common.domain.LanguageCode

object PathVars {
  import Codecs.TapirCodec.*
  val userIriPathVar: EndpointInput.PathCapture[UserIri] =
    path[UserIri]
      .name("userIri")
      .description("The user IRI. Must be URL-encoded.")
      .example(UserIri.makeNew)

  val emailPathVar: EndpointInput.PathCapture[Email] =
    path[Email]
      .name("email")
      .description("The user email. Must be URL-encoded.")
      .example(Email.unsafeFrom("jane@example.com"))

  val usernamePathVar: EndpointInput.PathCapture[Username] =
    path[Username]
      .name("username")
      .description("The user name. Must be URL-encoded.")
      .example(Username.unsafeFrom("JaneDoe"))
}

final class UsersEndpoints(baseEndpoints: BaseEndpoints) {

  private val base = "admin" / "users"

  private val pathProjectIri          = "path projectIri"
  private val projectAdminRequirement = Policy.ProjectAdmin(pathProjectIri).requirement
  private val pathGroupProject        = "path groupIri's project"
  private val groupAdminRequirement   = Policy.ProjectAdmin(pathGroupProject).requirement
  private val pathUserIri             = "path userIri"
  private val selfOrAdminRequirement  = Policy.SelfOrSystemAdmin(pathUserIri).requirement
  private val userByIriPath           = base / "iri" / PathVars.userIriPathVar
  private val publicProfileReason     = "answers the public profile; private fields only to the user and SystemAdmin"
  private def selfOrAdmin             = baseEndpoints.selfOrSystemAdminPolicyEndpoint(pathUserIri, userByIriPath)

  object get {
    val users = baseEndpoints.projectAdminOfAnyProjectPolicyEndpoint.get
      .in(base)
      .out(jsonBody[UsersResponse])
      .description(s"Returns all users. ${Policy.ProjectAdminOfAnyProject.requirement}")

    val userByIri = baseEndpoints
      .publicPolicyEndpointWithUser(publicProfileReason)
      .get
      .in(base / "iri" / PathVars.userIriPathVar)
      .out(jsonBody[UserResponse])
      .description(
        s"Returns a user identified by their IRI. ${Policy.Public(publicProfileReason).requirement} Returns detailed information to SystemAdmin and the user themselves, restricted information to others.",
      )

    val userByEmail = baseEndpoints
      .publicPolicyEndpointWithUser(publicProfileReason)
      .get
      .in(base / "email" / emailPathVar)
      .out(jsonBody[UserResponse])
      .description(
        s"Returns a user identified by their Email. ${Policy.Public(publicProfileReason).requirement} Returns detailed information to SystemAdmin and the user themselves, restricted information to others.",
      )

    val userByUsername = baseEndpoints
      .publicPolicyEndpointWithUser(publicProfileReason)
      .get
      .in(base / "username" / usernamePathVar)
      .out(jsonBody[UserResponse])
      .description(
        s"Returns a user identified by their Username. ${Policy.Public(publicProfileReason).requirement} Returns detailed information to SystemAdmin and the user themselves, restricted information to others.",
      )

    val usersByIriProjectMemberShips = selfOrAdmin.get
      .in("project-memberships")
      .out(jsonBody[UserProjectMembershipsGetResponseADM])
      .description(
        s"Returns the user's project memberships for a user identified by their IRI. $selfOrAdminRequirement",
      )

    val usersByIriProjectAdminMemberShips = selfOrAdmin.get
      .in("project-admin-memberships")
      .out(jsonBody[UserProjectAdminMembershipsGetResponseADM])
      .description(
        s"Returns the user's project admin memberships for a user identified by their IRI. $selfOrAdminRequirement",
      )

    val usersByIriGroupMemberships = selfOrAdmin.get
      .in("group-memberships")
      .out(jsonBody[UserGroupMembershipsGetResponseADM])
      .description(s"Returns the user's group memberships for a user identified by their IRI. $selfOrAdminRequirement")
  }

  object post {
    val users = baseEndpoints.projectAdminOfAnyProjectPolicyEndpoint.post
      .in(base)
      .in(jsonBody[UserCreateRequest])
      .out(jsonBody[UserResponse])
      .description(
        s"Create a new user. ${Policy.ProjectAdminOfAnyProject.requirement} Creating a system administrator requires SystemAdmin permissions.",
      )

    val usersByIriProjectMemberShips = baseEndpoints
      .projectAdminPolicyEndpoint(pathProjectIri)
      .post
      .in(base / "iri" / PathVars.userIriPathVar / "project-memberships" / AdminPathVariables.projectIri)
      .out(jsonBody[UserResponse])
      .description(s"Add a user to a project identified by IRI. $projectAdminRequirement")

    val usersByIriProjectAdminMemberShips = baseEndpoints
      .projectAdminPolicyEndpoint(pathProjectIri)
      .post
      .in(base / "iri" / PathVars.userIriPathVar / "project-admin-memberships" / AdminPathVariables.projectIri)
      .out(jsonBody[UserResponse])
      .description(s"Add a user as an admin to a project identified by IRI. $projectAdminRequirement")

    val usersByIriGroupMemberShips = baseEndpoints
      .projectAdminPolicyEndpoint(pathGroupProject)
      .post
      .in(base / "iri" / PathVars.userIriPathVar / "group-memberships" / AdminPathVariables.groupIriPathVar)
      .out(jsonBody[UserResponse])
      .description(s"Add a user to a group identified by IRI. $groupAdminRequirement")
  }

  object put {
    val usersIriBasicInformation = selfOrAdmin.put
      .in("BasicUserInformation")
      .in(jsonBody[BasicUserInformationChangeRequest])
      .out(jsonBody[UserResponse])
      .description(s"Update a user's basic information identified by IRI. $selfOrAdminRequirement")

    val usersIriPassword = selfOrAdmin.put
      .in("Password")
      .in(jsonBody[PasswordChangeRequest])
      .out(jsonBody[UserResponse])
      .description(s"Change a user's password identified by IRI. $selfOrAdminRequirement")

    val usersIriStatus = selfOrAdmin.put
      .in("Status")
      .in(jsonBody[StatusChangeRequest])
      .out(jsonBody[UserResponse])
      .description(s"Change a user's status identified by IRI. $selfOrAdminRequirement")

    val usersIriSystemAdmin = baseEndpoints.systemAdminPolicyEndpoint.put
      .in(base / "iri" / PathVars.userIriPathVar / "SystemAdmin")
      .in(jsonBody[SystemAdminChangeRequest])
      .out(jsonBody[UserResponse])
      .description(s"Change a user's SystemAdmin status identified by IRI. ${Policy.SystemAdmin.requirement}")
  }

  object delete {
    val deleteUser = selfOrAdmin.delete
      .out(jsonBody[UserResponse])
      .description(s"Delete a user identified by IRI (change status to false). $selfOrAdminRequirement")

    val usersByIriProjectMemberShips = baseEndpoints
      .projectAdminPolicyEndpoint(pathProjectIri)
      .delete
      .in(base / "iri" / PathVars.userIriPathVar / "project-memberships" / AdminPathVariables.projectIri)
      .out(jsonBody[UserResponse])
      .description(s"Remove a user from a project membership identified by IRI. $projectAdminRequirement")

    val usersByIriProjectAdminMemberShips = baseEndpoints
      .projectAdminPolicyEndpoint(pathProjectIri)
      .delete
      .in(base / "iri" / PathVars.userIriPathVar / "project-admin-memberships" / AdminPathVariables.projectIri)
      .out(jsonBody[UserResponse])
      .description(s"Remove a user form an admin project membership identified by IRI. $projectAdminRequirement")

    val usersByIriGroupMemberShips = baseEndpoints
      .projectAdminPolicyEndpoint(pathGroupProject)
      .delete
      .in(base / "iri" / PathVars.userIriPathVar / "group-memberships" / AdminPathVariables.groupIriPathVar)
      .out(jsonBody[UserResponse])
      .description(s"Remove a user form an group membership identified by IRI. $groupAdminRequirement")
  }
}

object UsersEndpoints {
  import Codecs.ZioJsonCodec.*
  object Requests {
    final case class UserCreateRequest(
      id: Option[UserIri] = None,
      username: Username,
      email: Email,
      givenName: GivenName,
      familyName: FamilyName,
      password: Password,
      status: UserStatus,
      lang: LanguageCode,
      systemAdmin: SystemAdmin,
    )
    object UserCreateRequest {
      implicit val jsonCodec: JsonCodec[UserCreateRequest] = DeriveJsonCodec.gen[UserCreateRequest]
    }

    final case class BasicUserInformationChangeRequest(
      username: Option[Username] = None,
      email: Option[Email] = None,
      givenName: Option[GivenName] = None,
      familyName: Option[FamilyName] = None,
      lang: Option[LanguageCode] = None,
    )
    object BasicUserInformationChangeRequest {
      implicit val jsonCodec: JsonCodec[BasicUserInformationChangeRequest] =
        DeriveJsonCodec.gen[BasicUserInformationChangeRequest]
    }

    final case class PasswordChangeRequest(requesterPassword: Password, newPassword: Password)
    object PasswordChangeRequest {
      implicit val jsonCodec: JsonCodec[PasswordChangeRequest] = DeriveJsonCodec.gen[PasswordChangeRequest]
    }

    final case class StatusChangeRequest(status: UserStatus)
    object StatusChangeRequest {
      implicit val jsonCodec: JsonCodec[StatusChangeRequest] = DeriveJsonCodec.gen[StatusChangeRequest]
    }

    final case class SystemAdminChangeRequest(systemAdmin: SystemAdmin)
    object SystemAdminChangeRequest {
      implicit val jsonCodec: JsonCodec[SystemAdminChangeRequest] = DeriveJsonCodec.gen[SystemAdminChangeRequest]
    }
  }

  val layer = ZLayer.derive[UsersEndpoints]
}
