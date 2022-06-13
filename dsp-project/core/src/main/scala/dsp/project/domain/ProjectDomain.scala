/*
 * Copyright © 2021 - 2022 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.domain

import zio.prelude.Validation

// import java.util.UUID

// TODO: all the following needs to be adjusted

// // move this to shared value objects project once we have it
// sealed trait Iri
// object Iri {

//   /**
//    * UserIri value object.
//    */
//   sealed abstract case class UserIri private (value: String) extends Iri
//   object UserIri { self =>

//     def make(value: String): UserIri = new UserIri(value) {}
//   }
//   // ...

// }

// /**
//  * Stores the user ID, i.e. UUID and IRI of the user
//  *
//  * @param uuid the UUID of the user
//  * @param iri the IRI of the user
//  */
// abstract case class UserId private (
//   uuid: UUID,
//   iri: Iri.UserIri
// )

// /**
//  * Companion object for UserId. Contains factory methods for creating UserId instances.
//  */
// object UserId {

//   /**
//    * Generates a UserId instance from a given string (either UUID or IRI).
//    *
//    * @param value the string to parse (either UUID or IRI)
//    * @return a new UserId instance
//    */
//   // TODO not sure if we need this
//   // def fromString(value: String): UserId = {
//   //   val uuidPattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$".r
//   //   val iriPattern  = "^http*".r

//   //   value match {
//   //     case uuidPattern(value) => new UserId(UUID.fromString(value), Iri.UserIri.make(value)) {}
//   //     case iriPattern(value) =>
//   //       new UserId(UUID.fromString(value.substring(value.lastIndexOf("/") + 1)), Iri.UserIri.make(value)) {}
//   //     //case _                  => ???
//   //   }
//   // }

//   /**
//    * Generates a UserId instance with a new (random) UUID and an IRI which is created from a prefix and the UUID.
//    *
//    * @return a new UserId instance
//    */
//   def fromIri(iri: Iri.UserIri): UserId = {
//     val uuid: UUID = UUID.fromString(iri.value.split("/").last)
//     new UserId(uuid, iri) {}
//   }

//   /**
//    * Generates a UserId instance with a new (random) UUID and an IRI which is created from a prefix and the UUID.
//    *
//    * @return a new UserId instance
//    */
//   def fromUuid(uuid: UUID): UserId = {
//     val iri: Iri.UserIri = Iri.UserIri.make("http://rdfh.ch/users/" + uuid.toString)
//     new UserId(uuid, iri) {}
//   }

//   /**
//    * Generates a UserId instance with a new (random) UUID and an IRI which is created from a prefix and the UUID.
//    *
//    * @return a new UserId instance
//    */
//   // TODO should this return a Validation[Throwable, UserId]
//   def make(): UserId = {
//     val uuid: UUID       = UUID.randomUUID()
//     val iri: Iri.UserIri = Iri.UserIri.make("http://rdfh.ch/users/" + uuid.toString)
//     new UserId(uuid, iri) {}
//   }
// }

// /**
//  * Represents the user domain object.
//  *
//  * @param id          the ID of the user
//  * @param givenName   the given name of the user
//  * @param familyName  the family name of the user
//  * @param username    the username of the user
//  * @param email       the email of the user
//  * @param password    the password of the user
//  * @param language    the user's preferred language
//  * @param role        the user's role
//  */
// sealed abstract case class User private (
//   id: UserId,
//   givenName: GivenName,
//   familyName: FamilyName,
//   username: Username,
//   email: Email,
//   password: Option[Password],
//   language: LanguageCode
//   //role: Role
// ) extends Ordered[User] { self =>

//   /**
//    * Allows to sort collections of [[User]]s. Sorting is done by the IRI.
//    */
//   def compare(that: User): Int = self.id.iri.toString().compareTo(that.id.iri.toString())

//   def updateUsername(value: Username): User =
//     new User(self.id, self.givenName, self.familyName, value, self.email, self.password, self.language) {}
// }
// object User {
//   def make(
//     givenName: GivenName,
//     familyName: FamilyName,
//     username: Username,
//     email: Email,
//     password: Password,
//     language: LanguageCode
//     //role: Role
//   ): User = {
//     val id = UserId.make()
//     new User(id, givenName, familyName, username, email, Some(password), language) {}
//   }

// }
