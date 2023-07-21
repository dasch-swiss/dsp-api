/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v1.responder.usermessages

import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoV1

/**
 * Represents a user's profile.
 *
 * @param userData       basic information about the user.
 * @param groups         the groups that the user belongs to.
 * @param projects_info  the projects that the user belongs to.
 * @param sessionId      the sessionId,.
 * @param permissionData the user's permission data.
 */
case class UserProfileV1(
  userData: UserDataV1 = UserDataV1(lang = "en"),
  groups: Seq[IRI] = Seq.empty[IRI],
  projects_info: Map[IRI, ProjectInfoV1] = Map.empty[IRI, ProjectInfoV1],
  sessionId: Option[String] = None,
  isSystemUser: Boolean = false,
  permissionData: PermissionsDataADM = PermissionsDataADM()
)

/**
 * Represents basic information about a user.
 *
 * @param user_id   The user's IRI.
 * @param email     The user's email address.
 * @param password  The user's hashed password.
 * @param token     The API token. Can be used instead of email/password for authentication.
 * @param firstname The user's given name.
 * @param lastname  The user's surname.
 * @param status    The user's status.
 * @param lang      The ISO 639-1 code of the user's preferred language.
 */
case class UserDataV1(
  user_id: Option[IRI] = None,
  email: Option[String] = None,
  password: Option[String] = None,
  token: Option[String] = None,
  firstname: Option[String] = None,
  lastname: Option[String] = None,
  status: Option[Boolean] = Some(true),
  lang: String
)
