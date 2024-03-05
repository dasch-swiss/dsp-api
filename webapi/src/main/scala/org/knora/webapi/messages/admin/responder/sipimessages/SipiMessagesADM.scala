/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.sipimessages

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.messages.admin.responder.AdminResponse
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectRestrictedViewSettingsADM

/**
 * Represents the JSON response to a request for a information about a `FileValue`.
 *
 * @param permissionCode         a code representing the user's maximum permission on the file.
 * @param restrictedViewSettings the project's restricted view settings.
 */
case class PermissionCodeAndProjectRestrictedViewSettings(
  permissionCode: Int,
  restrictedViewSettings: Option[ProjectRestrictedViewSettingsADM],
) extends AdminResponse
object PermissionCodeAndProjectRestrictedViewSettings {
  implicit val codec: JsonCodec[PermissionCodeAndProjectRestrictedViewSettings] =
    DeriveJsonCodec.gen[PermissionCodeAndProjectRestrictedViewSettings]
}
