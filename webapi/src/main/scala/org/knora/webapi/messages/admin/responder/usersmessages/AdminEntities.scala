package org.knora.webapi.messages.admin.responder.usersmessages

import org.knora.webapi.IRI

sealed trait ValidationError
case object InvalidUsername extends ValidationError
case object InvalidEmail extends ValidationError
case object InvalidGivenOrFamilyName extends ValidationError
case object InvalidPassword extends ValidationError
case object InvalidLanguageCode extends ValidationError

/**
  * User entity representing the payload for the create and update user request
  */
sealed case class UserEntity(id: Option[IRI],
                             username: Username,
                             email: Email,
                             givenName: GivenName,
                             familyName: FamilyName,
                             password: Password,
                             status: Status,
                             lang: LanguageCode,
                             systemAdmin: SystemAdmin)
