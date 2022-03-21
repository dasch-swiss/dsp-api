/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.testkit.ImplicitSender
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.CoreSpec
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import spray.json.JsString

object JWTHelperSpec {
  val config: Config = ConfigFactory.parseString("""
                                                   |app {
                                                   |    akka.loglevel = "DEBUG"
                                                   |
                                                   |    jwt-secret-key = "UP 4888, nice 4-8-4 steam engine"
                                                   |    jwt-longevity = 36500 days
                                                   |}
        """.stripMargin)
}

class JWTHelperSpec extends CoreSpec(JWTHelperSpec.config) with ImplicitSender {

  private val validToken: String =
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiIwLjAuMC4wOjMzMzMiLCJzdWIiOiJodHRwOi8vcmRmaC5jaC91c2Vycy85WEJDckRWM1NSYTdrUzFXd3luQjRRIiwiYXVkIjpbIktub3JhIiwiU2lwaSJdLCJleHAiOjQ4MDE0Njg1MTEsImlhdCI6MTY0Nzg2ODUxMSwianRpIjoiYXVVVUh1aDlUanF2SnBYUXVuOVVfZyIsImZvbyI6ImJhciJ9.6yHse3pNGdDqkC4PXdkm2ZtRqITqSwo0gvCZ__4jzHQ"

  "The JWTHelper" should {

    "create a token" in {
      val token: String = JWTHelper.createToken(
        userIri = SharedTestDataADM.anythingUser1.id,
        secret = settings.jwtSecretKey,
        longevity = settings.jwtLongevity,
        issuer = settings.externalKnoraApiHostPort,
        content = Map("foo" -> JsString("bar"))
      )

      JWTHelper.extractUserIriFromToken(
        token = token,
        secret = settings.jwtSecretKey,
        issuer = settings.externalKnoraApiHostPort
      ) should be(Some(SharedTestDataADM.anythingUser1.id))

      JWTHelper.extractContentFromToken(
        token = token,
        secret = settings.jwtSecretKey,
        contentName = "foo",
        issuer = settings.externalKnoraApiHostPort
      ) should be(Some("bar"))
    }

    "validate a token" in {
      JWTHelper.validateToken(
        token = validToken,
        secret = settings.jwtSecretKey,
        issuer = settings.externalKnoraApiHostPort
      ) should be(true)
    }

    "extract the user's IRI" in {
      JWTHelper.extractUserIriFromToken(
        token = validToken,
        secret = settings.jwtSecretKey,
        issuer = settings.externalKnoraApiHostPort
      ) should be(Some(SharedTestDataADM.anythingUser1.id))
    }

    "extract application-specific content" in {
      JWTHelper.extractContentFromToken(
        token = validToken,
        secret = settings.jwtSecretKey,
        contentName = "foo",
        issuer = settings.externalKnoraApiHostPort
      ) should be(Some("bar"))
    }

    "not decode an invalid token" in {
      val invalidToken: String =
        "foobareyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6Imh0dHA6Ly9yZGZoLmNoL3VzZXJzLzlYQkNyRFYzU1JhN2tTMVd3eW5CNFEiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImV4cCI6NDY5NTE5MzYwNSwiaWF0IjoxNTQxNTkzNjA1LCJqdGkiOiJsZmdreWJqRlM5Q1NiV19NeVA0SGV3IiwiZm9vIjoiYmFyIn0.qPMJjv8tVOM7KKDxR4Dmdz_kB0FzTOtJBYHSp62Dilk"

      JWTHelper.extractUserIriFromToken(
        token = invalidToken,
        secret = settings.jwtSecretKey,
        issuer = settings.externalKnoraApiHostPort
      ) should be(None)
    }

    "not decode a token with an invalid user IRI" in {
      val tokenWithInvalidSubject =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6ImludmFsaWQiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImV4cCI6NDY5NTE5MzYwNSwiaWF0IjoxNTQxNTkzNjA1LCJqdGkiOiJsZmdreWJqRlM5Q1NiV19NeVA0SGV3IiwiZm9vIjoiYmFyIn0.9uPJahn_KtCCZrnr5e4OHbEh3DsSIiX_b3ZB6H3ptY4"

      JWTHelper.extractUserIriFromToken(
        token = tokenWithInvalidSubject,
        secret = settings.jwtSecretKey,
        issuer = settings.externalKnoraApiHostPort
      ) should be(None)
    }

    "not decode a token with missing required content" in {
      val tokenWithMissingExp =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6Imh0dHA6Ly9yZGZoLmNoL3VzZXJzLzlYQkNyRFYzU1JhN2tTMVd3eW5CNFEiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImlhdCI6MTU0MTU5MzYwNSwianRpIjoibGZna3liakZTOUNTYldfTXlQNEhldyIsImZvbyI6ImJhciJ9.-ugb7OCoQq1JvBSso2HlfqVRBWM97b8burJTp3J9WeQ"

      JWTHelper.extractUserIriFromToken(
        token = tokenWithMissingExp,
        secret = settings.jwtSecretKey,
        issuer = settings.externalKnoraApiHostPort
      ) should be(None)
    }

    "not decode an expired token" in {
      val expiredToken =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6Imh0dHA6Ly9yZGZoLmNoL3VzZXJzLzlYQkNyRFYzU1JhN2tTMVd3eW5CNFEiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImV4cCI6MTU0MTU5MzYwNiwiaWF0IjoxNTQxNTkzNjA1LCJqdGkiOiJsZmdreWJqRlM5Q1NiV19NeVA0SGV3IiwiZm9vIjoiYmFyIn0.gahFI5-xg_gKLAwHRkKNbF0p_PzBTWC2m36vAYJPkz4"

      JWTHelper.extractUserIriFromToken(
        token = expiredToken,
        secret = settings.jwtSecretKey,
        issuer = settings.externalKnoraApiHostPort
      ) should be(None)
    }
  }
}
