/*
 * Copyright © 2021 - 2022 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package dsp.user.domain

import java.util.UUID

// move this to shared value objects project once we have it
sealed trait Iri
object Iri {

  /**
   * UserIri value object.
   */
  sealed abstract case class UserIri private (value: String) extends Iri
  object UserIri { self =>

    def make(value: String): UserIri = new UserIri(value) {}
  }
  // ...

}

/**
 * Stores the user ID, i.e. UUID and IRI of the user
 *
 * @param uuid the UUID of the user
 * @param iri the IRI of the user
 */
abstract case class UserId private (
  uuid: UUID,
  iri: Iri.UserIri
)

/**
 * Companion object for UserId. Contains factory methods for creating UserId instances.
 */
object UserId {

  /**
   * Generates a UserId instance from a given string (either UUID or IRI).
   *
   * @param value the string to parse (either UUID or IRI)
   * @return a new UserId instance
   */
  // TODO not sure if we need this
  // def fromString(value: String): UserId = {
  //   val uuidPattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$".r
  //   val iriPattern  = "^http*".r

  //   value match {
  //     case uuidPattern(value) => new UserId(UUID.fromString(value), Iri.UserIri.make(value)) {}
  //     case iriPattern(value) =>
  //       new UserId(UUID.fromString(value.substring(value.lastIndexOf("/") + 1)), Iri.UserIri.make(value)) {}
  //     //case _                  => ???
  //   }
  // }

  /**
   * Generates a UserId instance with a new (random) UUID and an IRI which is created from a prefix and the UUID.
   *
   * @return a new UserId instance
   */
  def fromIri(iri: Iri.UserIri): UserId = {
    val uuid: UUID = UUID.fromString(iri.value.split("/").last)
    new UserId(uuid, iri) {}
  }

  /**
   * Generates a UserId instance with a new (random) UUID and an IRI which is created from a prefix and the UUID.
   *
   * @return a new UserId instance
   */
  def fromUuid(uuid: UUID): UserId = {
    val iri: Iri.UserIri = Iri.UserIri.make("http://rdfh.ch/users/" + uuid.toString)
    new UserId(uuid, iri) {}
  }

  /**
   * Generates a UserId instance with a new (random) UUID and an IRI which is created from a prefix and the UUID.
   *
   * @return a new UserId instance
   */
  def make(): UserId = {
    val uuid: UUID       = UUID.randomUUID()
    val iri: Iri.UserIri = Iri.UserIri.make("http://rdfh.ch/users/" + uuid.toString)
    new UserId(uuid, iri) {}
  }
}

/**
 * Username value object.
 */
sealed abstract case class Username private (value: String)
object Username {
  def make(value: String): Username =
    new Username(value) {}
}

/**
 * Email value object.
 */
sealed abstract case class Email private (value: String)
object Email {
  def make(value: String): Email =
    new Email(value) {}
}

// These are just placeholders for now. Replace this with the real value objects once we have them.
object UserValueObjects {
  type GivenName  = String
  type FamilyName = String
  type Email      = String
  type Password   = String
  type Language   = String
  type Role       = String
}

import UserValueObjects._

/**
 * Represents the user domain object.
 *
 * @param id          the ID of the user
 * @param givenName   the given name of the user
 * @param familyName  the family name of the user
 * @param username    the username of the user
 * @param email       the email of the user
 * @param password    the password of the user
 * @param language    the user's preferred language
 * @param role        the user's role
 */
final case class User(
  id: UserId,
  givenName: GivenName,
  familyName: FamilyName,
  username: Username,
  email: Email,
  password: Option[Password],
  language: Language,
  role: Role
) extends Ordered[User] { self =>

  /**
   * Allows to sort collections of [[User]]s. Sorting is done by the IRI.
   */
  def compare(that: User): Int = self.id.iri.toString().compareTo(that.id.iri.toString())
}

/**
 * UserInformationTypeADM types:
 * full: everything
 * restricted: everything without sensitive information, i.e. token, password, session.
 * short: like restricted and additionally without groups, projects and permissions.
 * public: temporary: givenName, familyName
 *
 * Mainly used in combination with the 'ofType' method, to make sure that a request receiving this information
 * also returns the user profile of the correct type. Should be used in cases where we don't want to expose
 * sensitive information to the outside world. Since in API Admin [[UserADM]] is returned with some responses,
 * we use 'restricted' in those cases.
 */
sealed trait UserInformationType
object UserInformationType {
  //case object Public     extends UserInformationType // not sure if we need those
  //case object Short      extends UserInformationType
  case object Restricted extends UserInformationType
  case object Full       extends UserInformationType
}
