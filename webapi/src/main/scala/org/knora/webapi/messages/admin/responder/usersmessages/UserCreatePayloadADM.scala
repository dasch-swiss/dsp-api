/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.usersmessages

import zio.prelude.Validation

import dsp.valueobjects.Iri.UserIri
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.User._

final case class UserCreatePayloadADM(
  id: Option[UserIri] = None,
  username: Username,
  email: Email,
  givenName: GivenName,
  familyName: FamilyName,
  password: Password,
  status: UserStatus,
  lang: LanguageCode,
  systemAdmin: SystemAdmin
)

object UserCreatePayloadADM {
  def make(apiRequest: CreateUserApiRequestADM): Validation[String, UserCreatePayloadADM] =
    Validation
      .validateWith(
        apiRequest.id.map(UserIri.make(_).map(Some(_))).getOrElse(Validation.succeed(None)),
        Username.make(apiRequest.username),
        Email.make(apiRequest.email),
        GivenName.make(apiRequest.givenName),
        FamilyName.make(apiRequest.familyName),
        Password.make(apiRequest.password),
        Validation.succeed(UserStatus.make(apiRequest.status)),
        LanguageCode.make(apiRequest.lang),
        Validation.succeed(SystemAdmin.make(apiRequest.systemAdmin))
      )(UserCreatePayloadADM.apply)
      .mapError(_.getMessage)
}
