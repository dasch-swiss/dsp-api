/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody as sprayJsonBody
import zio.*

import org.knora.webapi.messages.admin.responder.usersmessages.UserOperationResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol.*
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.slice.admin.api.Codecs.TapirCodec.userIri
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.api.BaseEndpoints

object PathVars {
  val userIriPathVar: EndpointInput.PathCapture[UserIri] =
    path[UserIri].description("The user IRI. Must be URL-encoded.")
}
final case class UsersEndpoints(baseEndpoints: BaseEndpoints) {
  private val base = "admin" / "users"
  private val tags = List("Users", "Admin API")

  val getUsers = baseEndpoints.securedEndpoint.get
    .in(base)
    .out(sprayJsonBody[UsersGetResponseADM])
    .description("Returns all users.")
    .tags(tags)

  val getUserByIri = baseEndpoints.withUserEndpoint.get
    .in(base / "iri" / PathVars.userIriPathVar)
    .out(sprayJsonBody[UserResponseADM])
    .description("Returns a user identified by IRI.")
    .tags(tags)

  val deleteUser = baseEndpoints.securedEndpoint.delete
    .in(base / "iri" / PathVars.userIriPathVar)
    .out(sprayJsonBody[UserOperationResponseADM])
    .description("Delete a user identified by IRI (change status to false).")
    .tags(tags)

  val endpoints: Seq[AnyEndpoint] = Seq(getUsers, getUserByIri, deleteUser).map(_.endpoint)
}

object UsersEndpoints {
  val layer = ZLayer.derive[UsersEndpoints]
}
