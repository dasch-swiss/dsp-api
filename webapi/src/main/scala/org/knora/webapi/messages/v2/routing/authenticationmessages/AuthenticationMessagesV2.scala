/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.routing.authenticationmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.IRI
import dsp.errors.BadRequestException
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import spray.json._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
 * Represents an API request payload that asks the Knora API server to authenticate the user and create a JWT token.
 * Only one of IRI, username, or email as identifier is allowed.
 *
 * @param iri      the user's IRI.
 * @param email    the user's email.
 * @param username the user's username.
 * @param password the user's password.
 */
case class LoginApiRequestPayloadV2(
  iri: Option[IRI] = None,
  email: Option[String] = None,
  username: Option[String] = None,
  password: String
) {

  val identifyingParameterCount: Int = List(
    iri,
    email,
    username
  ).flatten.size

  // something needs to be set
  if (identifyingParameterCount == 0) throw BadRequestException("Empty user identifier is not allowed.")

  if (identifyingParameterCount > 1) throw BadRequestException("Only one option allowed for user identifier.")

  // Password needs to be supplied
  if (password.isEmpty) throw BadRequestException("Password needs to be supplied.")
}

/**
 * Sum type representing the different credential types
 */
sealed trait KnoraCredentialsV2

object KnoraCredentialsV2 {

  /**
   * Represents id/password credentials that a user can supply within the authorization header or as URL parameters.
   *
   * @param identifier the supplied id.
   * @param password   the supplied password.
   */
  final case class KnoraPasswordCredentialsV2(identifier: UserIdentifierADM, password: String)
      extends KnoraCredentialsV2

  /**
   * Represents JWT token credentials that a user can supply withing the authorization header or as URL parameters.
   *
   * @param jwtToken the supplied json web token.
   */
  case class KnoraJWTTokenCredentialsV2(jwtToken: String) extends KnoraCredentialsV2

  /**
   * Represents session credentials that a user can supply within the cookie header.
   * The session token is contents wise the same as the jwt token. The session ID
   * equals the JWT token.
   *
   * @param sessionToken the supplied session token.
   */
  case class KnoraSessionCredentialsV2(sessionToken: String) extends KnoraCredentialsV2
}

/**
 * Represents a response Knora returns when communicating with the 'v2/authentication' route during the 'login' operation.
 *
 * @param token is the returned json web token.
 */
case class LoginResponse(token: String)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
 * A spray-json protocol for generating Knora API v2 JSON for property values.
 */
trait AuthenticationV2JsonProtocol extends DefaultJsonProtocol with NullOptions with SprayJsonSupport {
  implicit val loginApiRequestPayloadV2Format: RootJsonFormat[LoginApiRequestPayloadV2] =
    jsonFormat(LoginApiRequestPayloadV2, "iri", "email", "username", "password")
  implicit val SessionResponseFormat: RootJsonFormat[LoginResponse] = jsonFormat1(LoginResponse.apply)
}
