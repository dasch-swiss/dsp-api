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
