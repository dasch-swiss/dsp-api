/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.testkit.ImplicitSender
import spray.json.JsString

import org.knora.webapi.CoreSpec
import org.knora.webapi.sharedtestdata.SharedTestDataADM

class JwtServiceSpec extends CoreSpec with ImplicitSender {

  private val validToken: String =
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiIwLjAuMC4wOjMzMzMiLCJzdWIiOiJodHRwOi8vcmRmaC5jaC91c2Vycy85WEJDckRWM1NSYTdrUzFXd3luQjRRIiwiYXVkIjpbIktub3JhIiwiU2lwaSJdLCJleHAiOjQ4MDE0Njg1MTEsImlhdCI6MTY0Nzg2ODUxMSwianRpIjoiYXVVVUh1aDlUanF2SnBYUXVuOVVfZyIsImZvbyI6ImJhciJ9.6yHse3pNGdDqkC4PXdkm2ZtRqITqSwo0gvCZ__4jzHQ"

  "The JWTHelper" should {

    "create a token" in {
      val runZio = for {
        token   <- JwtService.createToken(SharedTestDataADM.anythingUser1.id, Map("foo" -> JsString("bar")))
        useriri <- JwtService.extractUserIriFromToken(token)
      } yield useriri

      UnsafeZioRun.runOrThrow(runZio) should be(Some(SharedTestDataADM.anythingUser1.id))
    }

    "validate a token" in {
      val tokenValid = JwtService.validateToken(validToken)
      UnsafeZioRun.runOrThrow(tokenValid) should be(true)
    }

    "extract the user's IRI" in {
      val runZio = JwtService.extractUserIriFromToken(validToken)
      UnsafeZioRun.runOrThrow(runZio) should be(Some(SharedTestDataADM.anythingUser1.id))
    }

    "not decode an invalid token" in {
      val invalidToken: String =
        "foobareyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6Imh0dHA6Ly9yZGZoLmNoL3VzZXJzLzlYQkNyRFYzU1JhN2tTMVd3eW5CNFEiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImV4cCI6NDY5NTE5MzYwNSwiaWF0IjoxNTQxNTkzNjA1LCJqdGkiOiJsZmdreWJqRlM5Q1NiV19NeVA0SGV3IiwiZm9vIjoiYmFyIn0.qPMJjv8tVOM7KKDxR4Dmdz_kB0FzTOtJBYHSp62Dilk"
      val runZio = JwtService.extractUserIriFromToken(invalidToken)
      UnsafeZioRun.runOrThrow(runZio) should be(None)
    }

    "not decode a token with an invalid user IRI" in {
      val tokenWithInvalidSubject =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6ImludmFsaWQiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImV4cCI6NDY5NTE5MzYwNSwiaWF0IjoxNTQxNTkzNjA1LCJqdGkiOiJsZmdreWJqRlM5Q1NiV19NeVA0SGV3IiwiZm9vIjoiYmFyIn0.9uPJahn_KtCCZrnr5e4OHbEh3DsSIiX_b3ZB6H3ptY4"
      val runZio = JwtService.extractUserIriFromToken(tokenWithInvalidSubject)
      UnsafeZioRun.runOrThrow(runZio) should be(None)
    }

    "not decode a token with missing required content" in {
      val tokenWithMissingExp =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6Imh0dHA6Ly9yZGZoLmNoL3VzZXJzLzlYQkNyRFYzU1JhN2tTMVd3eW5CNFEiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImlhdCI6MTU0MTU5MzYwNSwianRpIjoibGZna3liakZTOUNTYldfTXlQNEhldyIsImZvbyI6ImJhciJ9.-ugb7OCoQq1JvBSso2HlfqVRBWM97b8burJTp3J9WeQ"
      val runZio = JwtService.extractUserIriFromToken(tokenWithMissingExp)
      UnsafeZioRun.runOrThrow(runZio) should be(None)
    }

    "not decode an expired token" in {
      val expiredToken =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6Imh0dHA6Ly9yZGZoLmNoL3VzZXJzLzlYQkNyRFYzU1JhN2tTMVd3eW5CNFEiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImV4cCI6MTU0MTU5MzYwNiwiaWF0IjoxNTQxNTkzNjA1LCJqdGkiOiJsZmdreWJqRlM5Q1NiV19NeVA0SGV3IiwiZm9vIjoiYmFyIn0.gahFI5-xg_gKLAwHRkKNbF0p_PzBTWC2m36vAYJPkz4"
      val runZio = JwtService.extractUserIriFromToken(expiredToken)
      UnsafeZioRun.runOrThrow(runZio) should be(None)
    }

    "reject a token with a different issuer than the one who created the token" in {
      val tokenWithDifferentIssuer =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJibGFibGEiLCJzdWIiOiJodHRwOi8vcmRmaC5jaC91c2Vycy85WEJDckRWM1NSYTdrUzFXd3luQjRRIiwiYXVkIjpbIktub3JhIiwiU2lwaSJdLCJleHAiOjQ4MDE0NjkyNzgsImlhdCI6MTY0Nzg2OTI3OCwianRpIjoiYU9HRExCYnJUbi1iQUIwVXZzTDZMZyIsImZvbyI6ImJhciJ9.ewFp0uXjPkn6GSGvDcph1MZRPpip669IrpXQ8Qv3Vpw"
      val runZio = JwtService.validateToken(tokenWithDifferentIssuer)
      UnsafeZioRun.runOrThrow(runZio) should be(false)
    }
  }
}
