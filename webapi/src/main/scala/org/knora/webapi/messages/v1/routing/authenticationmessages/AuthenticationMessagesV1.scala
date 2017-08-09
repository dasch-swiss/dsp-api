/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.v1.routing.authenticationmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import spray.json._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new session
  *
  * @param username
  * @param password
  */
case class CreateSessionApiRequestV1(username: String, password: String)


/**
  * Represents credentials that a user can supply.
  *
  * @param passwordCredentials the optionally supplied email/password credentials.
  * @param sessionCredentials  the optionally supplied session credentials.
  */
case class KnoraCredentialsV1(passwordCredentials: Option[KnoraPasswordCredentialsV1] = None,
                              sessionCredentials: Option[KnoraSessionCredentialsV1] = None) {

    def isEmpty: Boolean = this.passwordCredentials.isEmpty && this.sessionCredentials.isEmpty

    def nonEmpty: Boolean = this.passwordCredentials.nonEmpty || this.sessionCredentials.nonEmpty
}

/**
  * Represents email/password credentials that a user can supply.
  *
  * @param email    the supplied email.
  * @param password the supplied password.
  */
case class KnoraPasswordCredentialsV1(email: String, password: String)

/**
  * Represents session credentials that a user can supply.
  *
  * @param token the supplied session token.
  */
case class KnoraSessionCredentialsV1(token: String)

/**
  * Represents the session containing the token and the user's profile.
  *
  * @param token         the session token  (a true JSON web token).
  * @param userProfileV1 the [[UserProfileV1]] the session token belongs to.
  */
case class SessionV1(token: String, userProfileV1: UserProfileV1)


/**
  * Representing user's credentials (iri, email, password)
  *
  * @param userIri  the user's IRI.
  * @param email    the user's email.
  * @param password the user's password.
  */
case class CredentialsV1(userIri: IRI, email: String, password: String) {

    def urlEncodedIri = java.net.URLEncoder.encode(userIri, "utf-8")

    def urlEncodedEmail = java.net.URLEncoder.encode(email, "utf-8")
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON for property values.
  */
object AuthenticateV1JsonProtocol extends DefaultJsonProtocol with NullOptions with SprayJsonSupport {

    implicit val createSessionApiRequestV1Format: RootJsonFormat[CreateSessionApiRequestV1] = jsonFormat2(CreateSessionApiRequestV1)
}