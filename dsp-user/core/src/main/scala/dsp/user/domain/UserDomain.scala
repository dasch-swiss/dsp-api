/*
 * Copyright © 2021 - 2022 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.domain

import dsp.valueobjects.User._
import dsp.valueobjects.UserId
import zio.prelude.Validation

import java.util.UUID

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
 * @param status      the user's status
 * @param role        the user's role
 */
sealed abstract case class User private (
  id: UserId,
  givenName: GivenName,
  familyName: FamilyName,
  username: Username,
  email: Email,
  password: Option[Password],
  language: LanguageCode,
  status: UserStatus
  //role: Role
) extends Ordered[User] { self =>

  /**
   * Allows to sort collections of [[User]]s. Sorting is done by the IRI.
   */
  def compare(that: User): Int = self.id.iri.toString().compareTo(that.id.iri.toString())

  /**
   * Update the username of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new username
   *  @return the updated [[User]]
   */
  def updateUsername(newValue: Username): User =
    new User(
      self.id,
      self.givenName,
      self.familyName,
      newValue,
      self.email,
      self.password,
      self.language,
      self.status
    ) {}

  /**
   * Update the email of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new email
   *  @return the updated [[User]]
   */
  def updateEmail(newValue: Email): User =
    new User(
      self.id,
      self.givenName,
      self.familyName,
      self.username,
      newValue,
      self.password,
      self.language,
      self.status
    ) {}

  /**
   * Update the given name of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new given name
   *  @return the updated [[User]]
   */
  def updateGivenName(newValue: GivenName): User =
    new User(
      self.id,
      newValue,
      self.familyName,
      self.username,
      self.email,
      self.password,
      self.language,
      self.status
    ) {}

  /**
   * Update the family name of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new family name
   *  @return the updated [[User]]
   */
  def updateFamilyName(newValue: FamilyName): User =
    new User(
      self.id,
      self.givenName,
      newValue,
      self.username,
      self.email,
      self.password,
      self.language,
      self.status
    ) {}

  /**
   * Update the password of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new password
   *  @return the updated [[User]]
   */
  def updatePassword(newPassword: Password): User =
    new User(
      self.id,
      self.givenName,
      self.familyName,
      self.username,
      self.email,
      Some(newPassword),
      self.language,
      self.status
    ) {}

  /**
   * Update the language of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new language
   *  @return the updated [[User]]
   */
  def updateLanguage(newValue: LanguageCode): User =
    new User(
      self.id,
      self.givenName,
      self.familyName,
      self.username,
      self.email,
      self.password,
      newValue,
      self.status
    ) {}

  /**
   * Update the status of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new status
   *  @return the updated [[User]]
   */
  def updateStatus(newValue: UserStatus): User =
    new User(
      self.id,
      self.givenName,
      self.familyName,
      self.username,
      self.email,
      self.password,
      self.language,
      newValue
    ) {}

  /**
   * Check password using SCrypt. The password supplied (its value as string) is hashed and
   * compared against the stored hash.
   *
   * @param password the password to check.
   * @return true if password matches and false if password doesn't match.
   */
  def passwordMatch(password: Password): Boolean =
    this.password.exists { hashedPassword =>
      // check which type of hash we have
      if (hashedPassword.value.startsWith("$e0801$")) {
        // SCrypt
        import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder
        val encoder = new SCryptPasswordEncoder()
        encoder.matches(password.value, hashedPassword.value)
      } else if (hashedPassword.value.startsWith("$2a$")) {
        // BCrypt
        import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
        val encoder = new BCryptPasswordEncoder()
        encoder.matches(password.value, hashedPassword.value)
      } else { // TODO do we still need this SHA-1 validation?
        // SHA-1
        val md = java.security.MessageDigest.getInstance("SHA-1")
        md.digest(password.value.getBytes("UTF-8")).map("%02x".format(_)).mkString.equals(hashedPassword)
      }
    }
}
object User {
  def make(
    givenName: GivenName,
    familyName: FamilyName,
    username: Username,
    email: Email,
    password: Password,
    language: LanguageCode,
    status: UserStatus
    //role: Role
  ): User = {
    val id = UserId.make()
    new User(id, givenName, familyName, username, email, Some(password), language, status) {}
  }

}
