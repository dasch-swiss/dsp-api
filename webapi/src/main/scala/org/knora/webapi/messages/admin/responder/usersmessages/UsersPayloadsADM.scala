/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.usersmessages

import org.knora.webapi.messages.admin.responder.valueObjects._

/**
 * User creation payload
 */
final case class UserCreatePayloadADM(
  id: Option[UserIRI] = None,
  username: Username,
  email: Email,
  givenName: GivenName,
  familyName: FamilyName,
  password: Password,
  status: UserStatus,
  lang: LanguageCode,
  systemAdmin: SystemAdmin
)
