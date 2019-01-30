/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing

import akka.testkit.ImplicitSender
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.{CoreSpec, SharedTestDataADM}
import spray.json.JsString

object JWTHelperSpec {
    val config: Config = ConfigFactory.parseString(
        """
          |app {
          |    akka.loglevel = "DEBUG"
          |
          |    jwt-secret-key = "UP 4888, nice 4-8-4 steam engine"
          |    jwt-longevity = 36500 days
          |}
        """.stripMargin)
}

class JWTHelperSpec extends CoreSpec(JWTHelperSpec.config) with ImplicitSender {

    private val validToken: String = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6Imh0dHA6Ly9yZGZoLmNoL3VzZXJzLzlYQkNyRFYzU1JhN2tTMVd3eW5CNFEiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImV4cCI6NDY5NTE5MzYwNSwiaWF0IjoxNTQxNTkzNjA1LCJqdGkiOiJsZmdreWJqRlM5Q1NiV19NeVA0SGV3IiwiZm9vIjoiYmFyIn0.qPMJjv8tVOM7KKDxR4Dmdz_kB0FzTOtJBYHSp62Dilk"

    "The JWTHelper" should {

        "create a token" in {
            val token: String = JWTHelper.createToken(
                userIri = SharedTestDataADM.anythingUser1.id,
                secret = settings.jwtSecretKey,
                longevity = settings.jwtLongevity,
                content = Map("foo" -> JsString("bar"))
            )

            JWTHelper.extractUserIriFromToken(
                token = token,
                secret = settings.jwtSecretKey
            ) should be(Some(SharedTestDataADM.anythingUser1.id))

            JWTHelper.extractContentFromToken(
                token = token,
                secret = settings.jwtSecretKey,
                contentName = "foo"
            ) should be(Some("bar"))
        }

        "validate a token" in {
            JWTHelper.validateToken(
                token = validToken,
                secret = settings.jwtSecretKey
            ) should be(true)
        }

        "extract the user's IRI" in {
            JWTHelper.extractUserIriFromToken(
                token = validToken,
                secret = settings.jwtSecretKey
            ) should be(Some(SharedTestDataADM.anythingUser1.id))
        }

        "extract application-specific content" in {
            JWTHelper.extractContentFromToken(
                token = validToken,
                secret = settings.jwtSecretKey,
                contentName = "foo"
            ) should be(Some("bar"))
        }

        "not decode an invalid token" in {
            val invalidToken: String = "foobareyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6Imh0dHA6Ly9yZGZoLmNoL3VzZXJzLzlYQkNyRFYzU1JhN2tTMVd3eW5CNFEiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImV4cCI6NDY5NTE5MzYwNSwiaWF0IjoxNTQxNTkzNjA1LCJqdGkiOiJsZmdreWJqRlM5Q1NiV19NeVA0SGV3IiwiZm9vIjoiYmFyIn0.qPMJjv8tVOM7KKDxR4Dmdz_kB0FzTOtJBYHSp62Dilk"

            JWTHelper.extractUserIriFromToken(
                token = invalidToken,
                secret = settings.jwtSecretKey
            ) should be(None)
        }

        "not decode a token with an invalid user IRI" in {
            val tokenWithInvalidSubject = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6ImludmFsaWQiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImV4cCI6NDY5NTE5MzYwNSwiaWF0IjoxNTQxNTkzNjA1LCJqdGkiOiJsZmdreWJqRlM5Q1NiV19NeVA0SGV3IiwiZm9vIjoiYmFyIn0.9uPJahn_KtCCZrnr5e4OHbEh3DsSIiX_b3ZB6H3ptY4"

            JWTHelper.extractUserIriFromToken(
                token = tokenWithInvalidSubject,
                secret = settings.jwtSecretKey
            ) should be(None)
        }

        "not decode a token with missing required content" in {
            val tokenWithMissingExp = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6Imh0dHA6Ly9yZGZoLmNoL3VzZXJzLzlYQkNyRFYzU1JhN2tTMVd3eW5CNFEiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImlhdCI6MTU0MTU5MzYwNSwianRpIjoibGZna3liakZTOUNTYldfTXlQNEhldyIsImZvbyI6ImJhciJ9.-ugb7OCoQq1JvBSso2HlfqVRBWM97b8burJTp3J9WeQ"

            JWTHelper.extractUserIriFromToken(
                token = tokenWithMissingExp,
                secret = settings.jwtSecretKey
            ) should be(None)
        }

        "not decode an expired token" in {
            val expiredToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6Imh0dHA6Ly9yZGZoLmNoL3VzZXJzLzlYQkNyRFYzU1JhN2tTMVd3eW5CNFEiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImV4cCI6MTU0MTU5MzYwNiwiaWF0IjoxNTQxNTkzNjA1LCJqdGkiOiJsZmdreWJqRlM5Q1NiV19NeVA0SGV3IiwiZm9vIjoiYmFyIn0.gahFI5-xg_gKLAwHRkKNbF0p_PzBTWC2m36vAYJPkz4"

            JWTHelper.extractUserIriFromToken(
                token = expiredToken,
                secret = settings.jwtSecretKey
            ) should be(None)
        }
    }
}
