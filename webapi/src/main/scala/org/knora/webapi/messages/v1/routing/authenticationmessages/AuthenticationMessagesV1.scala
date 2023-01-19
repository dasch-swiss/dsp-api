/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v1.routing.authenticationmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import spray.json._

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

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
 * @param user the user's information.
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

  implicit val createSessionApiRequestV1Format: RootJsonFormat[CreateSessionApiRequestV1] = jsonFormat2(
    CreateSessionApiRequestV1
  )
}
