/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.igl.jwt._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.{CoreSpec, SharedTestDataV1}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

object JWTHelperSpec {
    val config = ConfigFactory.parseString(
        """
        app {

        }
        """.stripMargin)
}

class JWTHelperSpec extends CoreSpec("AuthenticationTestSystem") with ImplicitSender {

    implicit val timeout: Timeout = Duration(5, SECONDS)

    val rootUserProfileV1: UserProfileV1 = SharedTestDataV1.rootUser
    val rootUserEmail: String = rootUserProfileV1.userData.email.get
    val rootUserPassword: String = "test"

    val secretKey: String = "super-secret-key"

    "The JWTHelper" should {

        val secret = "123456"

        "create token" in {
            val token = JWTHelper.createToken("userIri", secret, 1 day)

            val decodedJwt: Try[Jwt] = DecodedJwt.validateEncodedJwt(
                token,
                secret,
                JWTHelper.algorithm,
                JWTHelper.requiredHeaders,
                JWTHelper.requiredClaims,
                iss = Some(Iss("webapi")),
                aud = Some(Aud("webapi"))
            )

            decodedJwt.isSuccess should be(true)
            decodedJwt.get.getClaim[Sub].map(_.value) should be(Some("userIri"))
        }
        "validate token" in {
            val token = JWTHelper.createToken("userIri", secret, 1 day)
            JWTHelper.validateToken(token, secret) should be(true)
        }
        "extract user's IRI" in {
            val token = JWTHelper.createToken("userIri", secret, 1 day)
            JWTHelper.extractUserIriFromToken(token, secret) should be(Some("userIri"))
        }
    }
}
