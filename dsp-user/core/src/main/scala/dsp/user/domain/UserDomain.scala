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
 * @param role        the user's role
 */
sealed abstract case class User private (
  id: UserId,
  givenName: GivenName,
  familyName: FamilyName,
  username: Username,
  email: Email,
  password: Option[Password],
  language: LanguageCode
  //role: Role
) extends Ordered[User] { self =>

  /**
   * Allows to sort collections of [[User]]s. Sorting is done by the IRI.
   */
  def compare(that: User): Int = self.id.iri.toString().compareTo(that.id.iri.toString())

  def updateUsername(value: Username): User =
    new User(self.id, self.givenName, self.familyName, value, self.email, self.password, self.language) {}
}
object User {
  def make(
    givenName: GivenName,
    familyName: FamilyName,
    username: Username,
    email: Email,
    password: Password,
    language: LanguageCode
    //role: Role
  ): User = {
    val id = UserId.make()
    new User(id, givenName, familyName, username, email, Some(password), language) {}
  }

}
