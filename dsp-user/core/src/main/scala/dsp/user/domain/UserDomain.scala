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
  abstract case class UserIri private (iri: String) extends Iri
  object UserIri {
    def make(iri: String): UserIri = new UserIri(iri) {}
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
  def fromString(value: String): UserId =
    ??? // check if String parameter is a  UUID or an IRI, according to what it is, create the UserId, like so: new UserId(uuid, iri) {}

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

// These are just placeholders for now. Replace this with the real value objects once we have them.
object UserValueObjects {
  type UserIri    = String // IRI of the user that can be used by UserId (don't use it directly!)
  type UserId     = String // the UserId which contains both UUID and IRI
  type GivenName  = String
  type FamilyName = String
  type Username   = String
  type Email      = String
  type Password   = String
  type Language   = String
  type Role       = String
}

import UserValueObjects._

/**
 * Represents the user domain object.
 *
 * @param id          the ID of a user which contains both UUID and IRI of the user
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
  password: Password,
  language: Language,
  role: Role
)
