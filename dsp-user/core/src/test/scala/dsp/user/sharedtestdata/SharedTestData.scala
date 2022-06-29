/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.sharedtestdata

import dsp.valueobjects.User._
import dsp.user.domain.User
import dsp.errors.BadRequestException
import zio.prelude.Validation

object SharedTestData {

  val givenName1  = GivenName.make("GivenName1")
  val familyName1 = FamilyName.make("FamilyName1")
  val username1   = Username.make("username1")
  val email1      = Email.make("email1@email.com")

  val password1 = for {
    passwordStrength <- PasswordStrength.make(12)
    password         <- PasswordHash.make("password1", passwordStrength)
  } yield password

  val givenName2  = GivenName.make("GivenName2")
  val familyName2 = FamilyName.make("FamilyName2")
  val username2   = Username.make("username2")
  val email2      = Email.make("email2@email.com")

  val password2 = for {
    passwordStrength <- PasswordStrength.make(12)
    password         <- PasswordHash.make("password2", passwordStrength)
  } yield password

  val givenName3  = GivenName.make("GivenName3")
  val familyName3 = FamilyName.make("FamilyName3")
  val username3   = Username.make("username3")
  val email3      = Email.make("email3@email.com")

  val password3 = for {
    passwordStrength <- PasswordStrength.make(12)
    password         <- PasswordHash.make("password3", passwordStrength)
  } yield password

  val languageEn = LanguageCode.make("en")
  val languageFr = LanguageCode.make("fr")
  val languageDe = LanguageCode.make("de")

  val statusTrue  = UserStatus.make(true)
  val statusFalse = UserStatus.make(false)

  val user1 = for {
    givenName  <- givenName1
    familyName <- familyName1
    username   <- username1
    email      <- email1
    password   <- password1
    language   <- languageEn
    status     <- statusTrue
    user <- User
              .make(
                givenName,
                familyName,
                username,
                email,
                password,
                language,
                status
              )
  } yield user

  val user2 = for {
    givenName  <- givenName2
    familyName <- familyName2
    username   <- username2
    email      <- email2
    password   <- password2
    language   <- languageEn
    status     <- statusTrue
    user <- User
              .make(
                givenName,
                familyName,
                username,
                email,
                password,
                language,
                status
              )
  } yield user
}
