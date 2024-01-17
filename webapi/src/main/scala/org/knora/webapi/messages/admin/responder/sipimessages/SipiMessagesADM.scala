/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.sipimessages

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json.JsValue
import spray.json.NullOptions
import spray.json.RootJsonFormat

import org.knora.webapi.messages.admin.responder.AdminKnoraResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectRestrictedViewSettingsADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsADMJsonProtocol

/**
 * Represents the JSON response to a request for a information about a `FileValue`.
 *
 * @param permissionCode         a code representing the user's maximum permission on the file.
 * @param restrictedViewSettings the project's restricted view settings.
 */
case class SipiFileInfoGetResponseADM(
  permissionCode: Int,
  restrictedViewSettings: Option[ProjectRestrictedViewSettingsADM]
) extends AdminKnoraResponseADM {
  def toJsValue: JsValue = SipiResponderResponseADMJsonProtocol.sipiFileInfoGetResponseADMFormat.write(this)
}

/**
 * A spray-json protocol for generating Knora API v1 JSON providing data about representations of a resource.
 */
object SipiResponderResponseADMJsonProtocol
    extends SprayJsonSupport
    with DefaultJsonProtocol
    with NullOptions
    with ProjectsADMJsonProtocol {

  implicit val sipiFileInfoGetResponseADMFormat: RootJsonFormat[SipiFileInfoGetResponseADM] =
    jsonFormat2(SipiFileInfoGetResponseADM)
}
