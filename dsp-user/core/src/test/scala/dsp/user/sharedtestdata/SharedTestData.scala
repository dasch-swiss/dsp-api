/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.sharedtestdata

import dsp.valueobjects.User._
import dsp.user.domain.User

object SharedTestData {
  val simpleGivenName1  = GivenName.make("GivenName1").fold(e => throw e.head, v => v)
  val simpleFamilyName1 = FamilyName.make("FamilyName1").fold(e => throw e.head, v => v)
  val simpleUsername1   = Username.make("username1").fold(e => throw e.head, v => v)
  val simpleEmail1      = Email.make("email1@email.com").fold(e => throw e.head, v => v)
  val simplePassword1   = PasswordHash.make("password1").fold(e => throw e.head, v => v)

  val simpleGivenName2  = GivenName.make("GivenName2").fold(e => throw e.head, v => v)
  val simpleFamilyName2 = FamilyName.make("FamilyName2").fold(e => throw e.head, v => v)
  val simpleUsername2   = Username.make("username2").fold(e => throw e.head, v => v)
  val simpleEmail2      = Email.make("email2@email.com").fold(e => throw e.head, v => v)
  val simplePassword2   = PasswordHash.make("password2").fold(e => throw e.head, v => v)

  val simpleGivenName3  = GivenName.make("GivenName3").fold(e => throw e.head, v => v)
  val simpleFamilyName3 = FamilyName.make("FamilyName3").fold(e => throw e.head, v => v)
  val simpleUsername3   = Username.make("username3").fold(e => throw e.head, v => v)
  val simpleEmail3      = Email.make("email3@email.com").fold(e => throw e.head, v => v)
  val simplePassword3   = PasswordHash.make("password3").fold(e => throw e.head, v => v)

  val languageEn = LanguageCode.make("en").fold(e => throw e.head, v => v)
  val statusTrue = UserStatus.make(true).fold(e => throw e.head, v => v)

  val simpleUser1 =
    User.make(
      simpleGivenName1,
      simpleFamilyName1,
      simpleUsername1,
      simpleEmail1,
      simplePassword1,
      languageEn,
      statusTrue
    )

  val simpleUser2 =
    User.make(
      simpleGivenName2,
      simpleFamilyName2,
      simpleUsername2,
      simpleEmail2,
      simplePassword2,
      languageEn,
      statusTrue
    )

}
