/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v1.responder.sessionmessages

import spray.json.DefaultJsonProtocol
import spray.json.RootJsonFormat

/**
 * Represents a response Knora returns when communicating with the 'v1/session' route during the 'login' operation.
 *
 * @param status is the returned status code.
 * @param message is the returned message.
 * @param sid is the returned session id.
 */
case class SessionResponse(status: Int, message: String, sid: String)

/**
 * A spray-json protocol used for turning the JSON responses from the 'login' operation during communication with the
 * 'v1/session' route into a case classes for easier testing.
 */
trait SessionJsonProtocol extends DefaultJsonProtocol {
  implicit val SessionResponseFormat: RootJsonFormat[SessionResponse] = jsonFormat3(SessionResponse.apply)
}
