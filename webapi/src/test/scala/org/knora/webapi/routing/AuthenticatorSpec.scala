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

import akka.actor.ActorDSL._
import akka.testkit.ImplicitSender
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.igl.jwt._
import org.knora.webapi.messages.v1.responder.authenticatemessages.{KnoraCredentialsV1, SessionV1}
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_NAME
import org.knora.webapi.util.ActorUtil
import org.knora.webapi.{BadCredentialsException, CoreSpec, SharedAdminTestData}
import org.scalatest.PrivateMethodTester

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

object AuthenticatorSpec {
    val config = ConfigFactory.parseString(
        """
        app {

        }
        """.stripMargin)
}

class AuthenticatorSpec extends CoreSpec("AuthenticationTestSystem") with ImplicitSender with PrivateMethodTester {

    implicit val executionContext = system.dispatcher
    implicit val timeout: Timeout = Duration(5, SECONDS)

    val rootUserProfileV1 = SharedAdminTestData.rootUser
    val rootUserEmail = rootUserProfileV1.userData.email.get
    val rootUserPassword = "test"


    val secretKey = "super-secret-key"
    val algorithm = Algorithm.HS256
    val requiredHeaders = Set[HeaderField](Typ)
    val requiredClaims = Set[ClaimField](Iss, Sub, Aud)


    val headers = Seq[HeaderValue](Typ("JWT"), Alg(algorithm))
    val claims = Seq[ClaimValue](Iss("webapi"), Sub(rootUserProfileV1.userData.user_id.get), Aud("webapi"))

    val jwt = new DecodedJwt(headers, claims)

    val encodedJwt = jwt.encodedAndSigned(secretKey)




    val mockUsersActor = actor(RESPONDER_MANAGER_ACTOR_NAME)(new Act {
        become {
            case UserProfileByEmailGetV1(submittedEmail, userProfileType) => {
                if (submittedEmail == "root@example.com") {
                    ActorUtil.future2Message(sender, Future(Some(rootUserProfileV1)), logger)
                } else {
                    ActorUtil.future2Message(sender, Future(None), logger)
                }
            }
        }
    })

    val getUserProfileV1ByEmail = PrivateMethod[Try[UserProfileV1]]('getUserProfileV1ByEmail)
    val authenticateCredentialsV1 = PrivateMethod[Try[SessionV1]]('authenticateCredentialsV1)

    "During Authentication" when {
        "called, the 'getUserProfileV1ByEmail' method " should {
            "succeed with the correct 'email' " in {
                Authenticator invokePrivate getUserProfileV1ByEmail(rootUserEmail, system, timeout, executionContext) should be(rootUserProfileV1)
            }

            "fail with the wrong 'email' " in {
                an [BadCredentialsException] should be thrownBy {
                    Authenticator invokePrivate getUserProfileV1ByEmail("wronguser@example.com", system, timeout, executionContext)
                }
            }

            "fail when not providing a email " in {
                an [BadCredentialsException] should be thrownBy {
                    Authenticator invokePrivate getUserProfileV1ByEmail("", system, timeout, executionContext)
                }
            }
        }
        "called, the 'authenticateCredentialsV1' method " should {
            "succeed with the correct 'email' / correct 'password' " in {
                Authenticator invokePrivate authenticateCredentialsV1(KnoraCredentialsV1(Some(rootUserEmail), Some(rootUserPassword), None), false, system, executionContext) should be(SessionV1(encodedJwt, rootUserProfileV1))
            }
            "fail with correct 'email' / wrong 'password' " in {
                an [BadCredentialsException] should be thrownBy {
                    Authenticator invokePrivate authenticateCredentialsV1(KnoraCredentialsV1(Some(rootUserEmail), Some("wrongpassword"), None), false, system, executionContext)
                }
            }
        }
    }
}
