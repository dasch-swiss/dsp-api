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
import org.knora.webapi.slice.api.admin.PathVars.userIriPathVar
import org.knora.webapi.slice.api.admin.PathVars.usernamePathVar
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.BasicUserInformationChangeRequest
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.PasswordChangeRequest
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.StatusChangeRequest
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.SystemAdminChangeRequest
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.UserCreateRequest
import org.knora.webapi.slice.api.admin.service.UserRestService.UserResponse
import org.knora.webapi.slice.api.admin.service.UserRestService.UsersResponse
import org.knora.webapi.slice.common.api.BaseEndpoints
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

  object get {
    val users = baseEndpoints.securedEndpoint.get
      .in(base)
      .out(jsonBody[UsersResponse])
      .description("Returns all users. Requires SystemAdmin or ProjectAdmin permissions in any project.")

    val userByIri = baseEndpoints.withUserEndpoint.get
      .in(base / "iri" / PathVars.userIriPathVar)
      .out(jsonBody[UserResponse])
      .description(
        "Returns a user identified by their IRI. Publicly accessible. Returns detailed information to SystemAdmin and the user themselves, restricted information to others.",
      )

    val userByEmail = baseEndpoints.withUserEndpoint.get
      .in(base / "email" / emailPathVar)
      .out(jsonBody[UserResponse])
      .description(
        "Returns a user identified by their Email. Publicly accessible. Returns detailed information to SystemAdmin and the user themselves, restricted information to others.",
      )

    val userByUsername = baseEndpoints.withUserEndpoint.get
      .in(base / "username" / usernamePathVar)
      .out(jsonBody[UserResponse])
      .description(
        "Returns a user identified by their Username. Publicly accessible. Returns detailed information to SystemAdmin and the user themselves, restricted information to others.",
      )

    val usersByIriProjectMemberShips = baseEndpoints.publicEndpoint.get
      .in(base / "iri" / userIriPathVar / "project-memberships")
      .out(jsonBody[UserProjectMembershipsGetResponseADM])
      .description("Returns the user's project memberships for a user identified by their IRI. Publicly accessible.")

    val usersByIriProjectAdminMemberShips = baseEndpoints.publicEndpoint.get
      .in(base / "iri" / userIriPathVar / "project-admin-memberships")
      .out(jsonBody[UserProjectAdminMembershipsGetResponseADM])
      .description(
        "Returns the user's project admin memberships for a user identified by their IRI. Publicly accessible.",
      )

    val usersByIriGroupMemberships = baseEndpoints.publicEndpoint.get
      .in(base / "iri" / userIriPathVar / "group-memberships")
      .out(jsonBody[UserGroupMembershipsGetResponseADM])
      .description("Returns the user's group memberships for a user identified by their IRI. Publicly accessible.")
  }

  object post {
    val users = baseEndpoints.securedEndpoint.post
      .in(base)
      .in(jsonBody[UserCreateRequest])
      .out(jsonBody[UserResponse])
      .description(
        "Create a new user. Requires SystemAdmin permissions to create a system administrator. Requires SystemAdmin or ProjectAdmin permissions in any project to create a regular user.",
      )

    val usersByIriProjectMemberShips = baseEndpoints.securedEndpoint.post
      .in(base / "iri" / PathVars.userIriPathVar / "project-memberships" / AdminPathVariables.projectIri)
      .out(jsonBody[UserResponse])
      .description(
        "Add a user to a project identified by IRI. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    val usersByIriProjectAdminMemberShips = baseEndpoints.securedEndpoint.post
      .in(base / "iri" / PathVars.userIriPathVar / "project-admin-memberships" / AdminPathVariables.projectIri)
      .out(jsonBody[UserResponse])
      .description(
        "Add a user as an admin to a project identified by IRI. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    val usersByIriGroupMemberShips = baseEndpoints.securedEndpoint.post
      .in(base / "iri" / PathVars.userIriPathVar / "group-memberships" / AdminPathVariables.groupIriPathVar)
      .out(jsonBody[UserResponse])
      .description(
        "Add a user to a group identified by IRI. Requires SystemAdmin or ProjectAdmin permissions for the group's project.",
      )
  }

  object put {
    val usersIriBasicInformation = baseEndpoints.securedEndpoint.put
      .in(base / "iri" / PathVars.userIriPathVar / "BasicUserInformation")
      .in(jsonBody[BasicUserInformationChangeRequest])
      .out(jsonBody[UserResponse])
      .description(
        "Update a user's basic information identified by IRI. Users can update their own information. SystemAdmin can update any user.",
      )

    val usersIriPassword = baseEndpoints.securedEndpoint.put
      .in(base / "iri" / PathVars.userIriPathVar / "Password")
      .in(jsonBody[PasswordChangeRequest])
      .out(jsonBody[UserResponse])
      .description(
        "Change a user's password identified by IRI. Users can change their own password. SystemAdmin can change any user's password.",
      )

    val usersIriStatus = baseEndpoints.securedEndpoint.put
      .in(base / "iri" / PathVars.userIriPathVar / "Status")
      .in(jsonBody[StatusChangeRequest])
      .out(jsonBody[UserResponse])
      .description(
        "Change a user's status identified by IRI. Users can update their own status. SystemAdmin can update any user's status.",
      )

    val usersIriSystemAdmin = baseEndpoints.securedEndpoint.put
      .in(base / "iri" / PathVars.userIriPathVar / "SystemAdmin")
      .in(jsonBody[SystemAdminChangeRequest])
      .out(jsonBody[UserResponse])
      .description("Change a user's SystemAdmin status identified by IRI. Requires SystemAdmin permissions.")
  }

  object delete {
    val deleteUser = baseEndpoints.securedEndpoint.delete
      .in(base / "iri" / PathVars.userIriPathVar)
      .out(jsonBody[UserResponse])
      .description(
        "Delete a user identified by IRI (change status to false). Users can delete themselves. SystemAdmin can delete any user.",
      )

    val usersByIriProjectMemberShips = baseEndpoints.securedEndpoint.delete
      .in(base / "iri" / PathVars.userIriPathVar / "project-memberships" / AdminPathVariables.projectIri)
      .out(jsonBody[UserResponse])
      .description(
        "Remove a user from a project membership identified by IRI. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    val usersByIriProjectAdminMemberShips = baseEndpoints.securedEndpoint.delete
      .in(base / "iri" / PathVars.userIriPathVar / "project-admin-memberships" / AdminPathVariables.projectIri)
      .out(jsonBody[UserResponse])
      .description(
        "Remove a user form an admin project membership identified by IRI. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    val usersByIriGroupMemberShips = baseEndpoints.securedEndpoint.delete
      .in(base / "iri" / PathVars.userIriPathVar / "group-memberships" / AdminPathVariables.groupIriPathVar)
      .out(jsonBody[UserResponse])
      .description(
        "Remove a user form an group membership identified by IRI. Requires SystemAdmin or ProjectAdmin permissions for the group's project.",
      )
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
