/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.spray.{jsonBody => sprayJsonBody}
import sttp.tapir.json.zio.{jsonBody => zioJsonBody}
import zio._
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import dsp.valueobjects.LanguageCode
import org.knora.webapi.messages.admin.responder.usersmessages.UserGroupMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectAdminMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.slice.admin.api.PathVars.emailPathVar
import org.knora.webapi.slice.admin.api.PathVars.userIriPathVar
import org.knora.webapi.slice.admin.api.PathVars.usernamePathVar
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.BasicUserInformationChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.PasswordChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.StatusChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.SystemAdminChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.UserCreateRequest
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.Password
import org.knora.webapi.slice.admin.domain.model.SystemAdmin
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.common.api.BaseEndpoints

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

final case class UsersEndpoints(baseEndpoints: BaseEndpoints) {

  private val base = "admin" / "users"

  object get {
    val users = baseEndpoints.securedEndpoint.get
      .in(base)
      .out(sprayJsonBody[UsersGetResponseADM])
      .description("Returns all users.")

    val userByIri = baseEndpoints.withUserEndpoint.get
      .in(base / "iri" / PathVars.userIriPathVar)
      .out(sprayJsonBody[UserResponseADM])
      .description("Returns a user identified by their IRI.")

    val userByEmail = baseEndpoints.withUserEndpoint.get
      .in(base / "email" / emailPathVar)
      .out(sprayJsonBody[UserResponseADM])
      .description("Returns a user identified by their Email.")

    val userByUsername = baseEndpoints.withUserEndpoint.get
      .in(base / "username" / usernamePathVar)
      .out(sprayJsonBody[UserResponseADM])
      .description("Returns a user identified by their Username.")

    val usersByIriProjectMemberShips = baseEndpoints.publicEndpoint.get
      .in(base / "iri" / userIriPathVar / "project-memberships")
      .out(sprayJsonBody[UserProjectMembershipsGetResponseADM])
      .description("Returns the user's project memberships for a user identified by their IRI.")

    val usersByIriProjectAdminMemberShips = baseEndpoints.publicEndpoint.get
      .in(base / "iri" / userIriPathVar / "project-admin-memberships")
      .out(sprayJsonBody[UserProjectAdminMembershipsGetResponseADM])
      .description("Returns the user's project admin memberships for a user identified by their IRI.")

    val usersByIriGroupMemberships = baseEndpoints.publicEndpoint.get
      .in(base / "iri" / userIriPathVar / "group-memberships")
      .out(sprayJsonBody[UserGroupMembershipsGetResponseADM])
      .description("Returns the user's group memberships for a user identified by their IRI.")
  }

  object post {
    val users = baseEndpoints.securedEndpoint.post
      .in(base)
      .in(zioJsonBody[UserCreateRequest])
      .out(sprayJsonBody[UserResponseADM])
      .description("Create a new user.")

    val usersByIriProjectMemberShips = baseEndpoints.securedEndpoint.post
      .in(base / "iri" / PathVars.userIriPathVar / "project-memberships" / AdminPathVariables.projectIri)
      .out(sprayJsonBody[UserResponseADM])
      .description("Add a user to a project identified by IRI.")

    val usersByIriProjectAdminMemberShips = baseEndpoints.securedEndpoint.post
      .in(base / "iri" / PathVars.userIriPathVar / "project-admin-memberships" / AdminPathVariables.projectIri)
      .out(sprayJsonBody[UserResponseADM])
      .description("Add a user as an admin to a project identified by IRI.")

    val usersByIriGroupMemberShips = baseEndpoints.securedEndpoint.post
      .in(base / "iri" / PathVars.userIriPathVar / "group-memberships" / AdminPathVariables.groupIriPathVar)
      .out(sprayJsonBody[UserResponseADM])
      .description("Add a user to a group identified by IRI.")
  }

  object put {
    val usersIriBasicInformation = baseEndpoints.securedEndpoint.put
      .in(base / "iri" / PathVars.userIriPathVar / "BasicUserInformation")
      .in(zioJsonBody[BasicUserInformationChangeRequest])
      .out(sprayJsonBody[UserResponseADM])
      .description("Update a user's basic information identified by IRI.")

    val usersIriPassword = baseEndpoints.securedEndpoint.put
      .in(base / "iri" / PathVars.userIriPathVar / "Password")
      .in(zioJsonBody[PasswordChangeRequest])
      .out(sprayJsonBody[UserResponseADM])
      .description("Change a user's password identified by IRI.")

    val usersIriStatus = baseEndpoints.securedEndpoint.put
      .in(base / "iri" / PathVars.userIriPathVar / "Status")
      .in(zioJsonBody[StatusChangeRequest])
      .out(sprayJsonBody[UserResponseADM])
      .description("Change a user's status identified by IRI.")

    val usersIriSystemAdmin = baseEndpoints.securedEndpoint.put
      .in(base / "iri" / PathVars.userIriPathVar / "SystemAdmin")
      .in(zioJsonBody[SystemAdminChangeRequest])
      .out(sprayJsonBody[UserResponseADM])
      .description("Change a user's SystemAdmin status identified by IRI.")

  }

  object delete {
    val deleteUser = baseEndpoints.securedEndpoint.delete
      .in(base / "iri" / PathVars.userIriPathVar)
      .out(sprayJsonBody[UserResponseADM])
      .description("Delete a user identified by IRI (change status to false).")

    val usersByIriProjectMemberShips = baseEndpoints.securedEndpoint.delete
      .in(base / "iri" / PathVars.userIriPathVar / "project-memberships" / AdminPathVariables.projectIri)
      .out(sprayJsonBody[UserResponseADM])
      .description("Remove a user from a project membership identified by IRI.")

    val usersByIriProjectAdminMemberShips = baseEndpoints.securedEndpoint.delete
      .in(base / "iri" / PathVars.userIriPathVar / "project-admin-memberships" / AdminPathVariables.projectIri)
      .out(sprayJsonBody[UserResponseADM])
      .description("Remove a user form an admin project membership identified by IRI.")

    val usersByIriGroupMemberShips = baseEndpoints.securedEndpoint.delete
      .in(base / "iri" / PathVars.userIriPathVar / "group-memberships" / AdminPathVariables.groupIriPathVar)
      .out(sprayJsonBody[UserResponseADM])
      .description("Remove a user form an group membership identified by IRI.")
  }

  private val public =
    Seq(
      get.usersByIriProjectMemberShips,
      get.usersByIriProjectAdminMemberShips,
      get.usersByIriGroupMemberships,
    )
  private val secured =
    Seq(
      get.users,
      get.userByIri,
      get.userByEmail,
      get.userByUsername,
      post.users,
      post.usersByIriProjectMemberShips,
      post.usersByIriProjectAdminMemberShips,
      post.usersByIriGroupMemberShips,
      put.usersIriBasicInformation,
      put.usersIriPassword,
      put.usersIriStatus,
      put.usersIriSystemAdmin,
      delete.deleteUser,
      delete.usersByIriProjectMemberShips,
      delete.usersByIriProjectAdminMemberShips,
      delete.usersByIriGroupMemberShips,
    ).map(_.endpoint)
  val endpoints: Seq[AnyEndpoint] = (public ++ secured).map(_.tag("Admin Users"))
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
