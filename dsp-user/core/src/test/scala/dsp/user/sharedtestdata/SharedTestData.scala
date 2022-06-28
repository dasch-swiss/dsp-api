/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.sharedtestdata

import dsp.valueobjects.User._
import dsp.user.domain.User

object SharedTestData {
  val givenName1  = GivenName.make("GivenName1").fold(e => throw e.head, v => v)
  val familyName1 = FamilyName.make("FamilyName1").fold(e => throw e.head, v => v)
  val username1   = Username.make("username1").fold(e => throw e.head, v => v)
  //val username1   = Username.unsafeMake("username1")
  val email1    = Email.make("email1@email.com").fold(e => throw e.head, v => v)
  val password1 = PasswordHash.make("password1", 12).fold(e => throw e.head, v => v)

  val givenName2  = GivenName.make("GivenName2").fold(e => throw e.head, v => v)
  val familyName2 = FamilyName.make("FamilyName2").fold(e => throw e.head, v => v)
  val username2   = Username.make("username2").fold(e => throw e.head, v => v)
  //val username2   = Username.unsafeMake("username2")
  val email2    = Email.make("email2@email.com").fold(e => throw e.head, v => v)
  val password2 = PasswordHash.make("password2", 12).fold(e => throw e.head, v => v)

  val givenName3  = GivenName.make("GivenName3").fold(e => throw e.head, v => v)
  val familyName3 = FamilyName.make("FamilyName3").fold(e => throw e.head, v => v)
  val username3   = Username.make("username3").fold(e => throw e.head, v => v)
  val email3      = Email.make("email3@email.com").fold(e => throw e.head, v => v)
  val password3   = PasswordHash.make("password3", 12).fold(e => throw e.head, v => v)

  val languageEn = LanguageCode.make("en").fold(e => throw e.head, v => v)
  val languageFr = LanguageCode.make("fr").fold(e => throw e.head, v => v)
  val languageDe = LanguageCode.make("de").fold(e => throw e.head, v => v)

  val statusTrue  = UserStatus.make(true).fold(e => throw e.head, v => v)
  val statusFalse = UserStatus.make(false).fold(e => throw e.head, v => v)

  val normalUser1 =
    User.make(
      givenName1,
      familyName1,
      username1,
      email1,
      password1,
      languageEn,
      statusTrue
    )

  val normalUser2 =
    User.make(
      givenName2,
      familyName2,
      username2,
      email2,
      password2,
      languageEn,
      statusTrue
    )

}
