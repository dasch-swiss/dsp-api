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
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
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

/**
  * Representing user's credentials
  *
  * @param user  the user's information.
  */
case class CredentialsADM(user: UserADM, password: String) {

    def iri = user.id

    def urlEncodedIri = java.net.URLEncoder.encode(iri, "utf-8")

    def email = user.email

    def urlEncodedEmail = java.net.URLEncoder.encode(email, "utf-8")

    def basicHttpCredentials = BasicHttpCredentials(email, password)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON for property values.
  */
object AuthenticateV1JsonProtocol extends DefaultJsonProtocol with NullOptions with SprayJsonSupport {

    implicit val createSessionApiRequestV1Format: RootJsonFormat[CreateSessionApiRequestV1] = jsonFormat2(CreateSessionApiRequestV1)
}