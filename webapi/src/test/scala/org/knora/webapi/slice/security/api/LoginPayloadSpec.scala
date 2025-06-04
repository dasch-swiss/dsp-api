/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security.api
import zio.json.*
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload

object LoginPayloadSpec extends ZIOSpecDefault {

  val spec = suiteAll("LoginPayload") {

    suiteAll("iri password payload") {
      val userIri            = UserIri.makeNew
      val iriPasswordJson    = s"{\"iri\":\"${userIri.value}\",\"password\":\"secret\"}"
      val iriPasswordPayload = LoginPayload.IriPassword(userIri, "secret")
      test("encode iri password") {
        assertTrue(iriPasswordPayload.toJson == iriPasswordJson)
      }
      test("decode iri password") {
        assertTrue(iriPasswordJson.fromJson[LoginPayload] == Right(iriPasswordPayload))
      }
    }: @annotation.nowarn

    suiteAll("email password payload") {
      val emailPasswordJson    = "{\"email\":\"email@example.com\",\"password\":\"secret\"}"
      val emailPasswordPayload = LoginPayload.EmailPassword(Email.unsafeFrom("email@example.com"), "secret")
      test("encode email password") {
        assertTrue(emailPasswordPayload.toJson == emailPasswordJson)
      }
      test("decode email password") {
        assertTrue(emailPasswordJson.fromJson[LoginPayload] == Right(emailPasswordPayload))
      }
    }: @annotation.nowarn

    suiteAll("username password payload") {
      val usernamePasswordJson    = "{\"username\":\"some.user\",\"password\":\"secret\"}"
      val usernamePasswordPayload = LoginPayload.UsernamePassword(Username.unsafeFrom("some.user"), "secret")
      test("encode username password") {
        assertTrue(usernamePasswordPayload.toJson == usernamePasswordJson)
      }
      test("decode username password") {
        assertTrue(usernamePasswordJson.fromJson[LoginPayload] == Right(usernamePasswordPayload))
      }
    }: @annotation.nowarn
  }
}
