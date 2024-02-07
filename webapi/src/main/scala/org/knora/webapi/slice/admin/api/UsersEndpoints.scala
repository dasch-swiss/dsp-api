/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody as sprayJsonBody
import sttp.tapir.json.zio.jsonBody as zioJsonBody
import zio.*
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import dsp.valueobjects.LanguageCode
import org.knora.webapi.messages.admin.responder.usersmessages.UserGroupMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserOperationResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectAdminMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol.*
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.slice.admin.api.PathVars.emailPathVar
import org.knora.webapi.slice.admin.api.PathVars.userIriPathVar
import org.knora.webapi.slice.admin.api.PathVars.usernamePathVar
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

  val getUsers = baseEndpoints.securedEndpoint.get
    .in(base)
    .out(sprayJsonBody[UsersGetResponseADM])
    .description("Returns all users.")

  val getUserByIri = baseEndpoints.withUserEndpoint.get
    .in(base / "iri" / PathVars.userIriPathVar)
    .out(sprayJsonBody[UserResponseADM])
    .description("Returns a user identified by their IRI.")

  val getUserByEmail = baseEndpoints.withUserEndpoint.get
    .in(base / "email" / emailPathVar)
    .out(sprayJsonBody[UserResponseADM])
    .description("Returns a user identified by their Email.")

  val getUserByUsername = baseEndpoints.withUserEndpoint.get
    .in(base / "username" / usernamePathVar)
    .out(sprayJsonBody[UserResponseADM])
    .description("Returns a user identified by their Username.")

  val getUsersByIriProjectMemberShips = baseEndpoints.publicEndpoint.get
    .in(base / "iri" / userIriPathVar / "project-memberships")
    .out(sprayJsonBody[UserProjectMembershipsGetResponseADM])
    .description("Returns the user's project memberships for a user identified by their IRI.")

  val getUsersByIriProjectAdminMemberShips = baseEndpoints.publicEndpoint.get
    .in(base / "iri" / userIriPathVar / "project-admin-memberships")
    .out(sprayJsonBody[UserProjectAdminMembershipsGetResponseADM])
    .description("Returns the user's project admin memberships for a user identified by their IRI.")

  val getUsersByIriGroupMemberships = baseEndpoints.publicEndpoint.get
    .in(base / "iri" / userIriPathVar / "group-memberships")
    .out(sprayJsonBody[UserGroupMembershipsGetResponseADM])
    .description("Returns the user's group memberships for a user identified by their IRI.")

  // Create
  val postUsers = baseEndpoints.securedEndpoint.post
    .in(base)
    .in(zioJsonBody[UserCreateRequest])
    .out(sprayJsonBody[UserOperationResponseADM])
    .description("Create a new user.")

  // Delete
  val deleteUser = baseEndpoints.securedEndpoint.delete
    .in(base / "iri" / PathVars.userIriPathVar)
    .out(sprayJsonBody[UserOperationResponseADM])
    .description("Delete a user identified by IRI (change status to false).")

  private val public =
    Seq(getUsersByIriProjectMemberShips, getUsersByIriProjectAdminMemberShips, getUsersByIriGroupMemberships)
  private val secured =
    Seq(getUsers, getUserByIri, getUserByEmail, getUserByUsername, postUsers, deleteUser).map(_.endpoint)
  val endpoints: Seq[AnyEndpoint] = (public ++ secured).map(_.tag("Admin Users"))
}

object UsersEndpoints {
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
      systemAdmin: SystemAdmin
    )
    object UserCreateRequest {
      import sttp.tapir.generic.auto.*
      import Codecs.ZioJsonCodec.*
      implicit val jsonCodec: JsonCodec[UserCreateRequest] = DeriveJsonCodec.gen[UserCreateRequest]
    }
  }

  val layer = ZLayer.derive[UsersEndpoints]
}
