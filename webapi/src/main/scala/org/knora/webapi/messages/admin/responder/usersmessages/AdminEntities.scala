package org.knora.webapi.messages.admin.responder.usersmessages

import org.knora.webapi.IRI

sealed trait ValidationError
case object InvalidUsername extends ValidationError
case object InvalidEmail extends ValidationError
case object InvalidGivenOrFamilyName extends ValidationError
case object InvalidPassword extends ValidationError
case object InvalidLanguageCode extends ValidationError

trait UserEntityTrait {
  def create(id: Option[IRI],
             username: Username,
             email: Email,
             givenName: GivenName,
             familyName: FamilyName,
             password: Password,
             status: Status,
             lang: LanguageCode,
             systemAdmin: SystemAdmin): UserEntity
//
//  def update(username: Option[Username],
//             email: Option[Email],
//             givenName: Option[GivenName],
//             familyName: Option[FamilyName],
//             status: Option[Status],
//             lang: Option[LanguageCode],
//             systemAdmin: Option[SystemAdmin]): UserEntity
}

/**
  * User entity representing the payload for the create and update user request
  */
sealed abstract case class UserEntity(id: Option[IRI],
                                      username: Option[Username],
                                      email: Option[Email],
                                      givenName: Option[GivenName],
                                      familyName: Option[FamilyName],
                                      password: Option[Password],
                                      status: Option[Status],
                                      lang: Option[LanguageCode],
                                      projects: Option[Seq[IRI]],
                                      projectsAdmin: Option[Seq[IRI]],
                                      groups: Option[Seq[IRI]],
                                      systemAdmin: Option[SystemAdmin])

object UserEntity extends UserEntityTrait {

  /** The create constructor needs all attributes but id which is optional */
  override def create(id: Option[IRI] = None,
                      username: Username,
                      email: Email,
                      givenName: GivenName,
                      familyName: FamilyName,
                      password: Password,
                      status: Status,
                      lang: LanguageCode,
                      systemAdmin: SystemAdmin): UserEntity =
    new UserEntity(
      id = id,
      username = Some(username),
      email = Some(email),
      givenName = Some(givenName),
      familyName = Some(familyName),
      password = Some(password),
      status = Some(status),
      lang = Some(lang),
      projects = None,
      projectsAdmin = None,
      groups = None,
      systemAdmin = Some(systemAdmin)
    ) {}
}
