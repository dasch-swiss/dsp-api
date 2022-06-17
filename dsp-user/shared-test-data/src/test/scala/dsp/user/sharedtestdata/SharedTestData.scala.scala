/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.sharedtestdata

import dsp.valueobjects.User._
import dsp.user.domain.User

object SharedTestData {
  val testUser1 = (for {
    givenName  <- GivenName.make("GivenName1")
    familyName <- FamilyName.make("familyName1")
    username   <- Username.make("username1")
    email      <- Email.make("email1@email.com")
    password   <- PasswordHash.make("password1")
    language   <- LanguageCode.make("en")
    status     <- UserStatus.make(true)
    user = User.make(
             givenName,
             familyName,
             username,
             email,
             password,
             language,
             status
           )
  } yield (user)).toZIO

  val testUser2 = (for {
    givenName  <- GivenName.make("GivenName2")
    familyName <- FamilyName.make("familyName2")
    username   <- Username.make("username2")
    email      <- Email.make("email2@email.com")
    password   <- PasswordHash.make("password2")
    language   <- LanguageCode.make("en")
    status     <- UserStatus.make(true)
    user = User.make(
             givenName,
             familyName,
             username,
             email,
             password,
             language,
             status
           )
  } yield (user)).toZIO
}
