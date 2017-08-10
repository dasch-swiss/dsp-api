/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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
import org.knora.webapi.routing.JWTHelper.{algorithm, requiredClaims, requiredHeaders}
import org.knora.webapi.{CoreSpec, SharedAdminTestData}

import scala.concurrent.duration._
import scala.util.Try

object JWTHelperSpec {
    val config = ConfigFactory.parseString(
        """
        app {

        }
        """.stripMargin)
}

class JWTHelperSpec extends CoreSpec("AuthenticationTestSystem") with ImplicitSender {

    implicit val executionContext = system.dispatcher
    implicit val timeout: Timeout = Duration(5, SECONDS)

    val rootUserProfileV1 = SharedAdminTestData.rootUser
    val rootUserEmail = rootUserProfileV1.userData.email.get
    val rootUserPassword = "test"


    val secretKey = "super-secret-key"
    val algorithm = Algorithm.HS256
    val requiredHeaders = Set[HeaderField](Typ)
    val requiredClaims = Set[ClaimField](Iss, Sub, Aud, Iat, Exp)


    val headers = Seq[HeaderValue](Typ("JWT"), Alg(algorithm))
    val claims = Seq[ClaimValue](Iss("webapi"), Sub(rootUserProfileV1.userData.user_id.get), Aud("webapi"))

    val jwt = new DecodedJwt(headers, claims)

    val encodedJwt = jwt.encodedAndSigned(secretKey)

    "The JWTHelper" should {

        val secret = "123456"

        "create token" in {
            val token = JWTHelper.createToken("userIri", secret, 1)

            val decodedJwt: Try[Jwt] = DecodedJwt.validateEncodedJwt(
                token,
                secret,
                algorithm,
                requiredHeaders,
                requiredClaims,
                iss = Some(Iss("webapi")),
                aud = Some(Aud("webapi"))
            )

            decodedJwt.isSuccess should be(true)
            decodedJwt.get.getClaim[Sub].map(_.value) should be(Some("userIri"))
        }
        "validate token" in {
            val token = JWTHelper.createToken("userIri", secret, 1)
            JWTHelper.validateToken(token, secret) should be(true)
        }
        "extract user's IRI" in {
            val token = JWTHelper.createToken("userIri", secret, 1)
            JWTHelper.extractUserIriFromToken(token, secret) should be(Some("userIri"))
        }
    }
}
