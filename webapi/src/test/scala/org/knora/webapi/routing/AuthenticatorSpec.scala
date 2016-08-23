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
import akka.actor.Props
import akka.testkit.ImplicitSender
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.v1.responder.usermessages.{UserProfileByUsernameGetRequestV1, UserProfileV1}
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_NAME
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.routing.Authenticator.{INVALID_CREDENTIALS_NON_FOUND, INVALID_CREDENTIALS_NO_USERNAME_SUPPLIED, INVALID_CREDENTIALS_USERNAME_OR_PASSWORD}
import org.knora.webapi.store._
import org.knora.webapi.{CoreSpec, InvalidCredentialsException, LiveActorMaker, SharedTestData}
import org.scalatest.PrivateMethodTester

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object AuthenticatorSpec {
    val config = ConfigFactory.parseString(
        """
          | akka.loglevel = "DEBUG"
          | akka.stdout-loglevel = "DEBUG"
          |
        """.stripMargin)
}

/**
  * This test uses the 'PrivateMethodTester' API, to test private methods in [[Authenticator]].
  */
class AuthenticatorSpec extends CoreSpec("AuthenticationTestSystem") with ImplicitSender with PrivateMethodTester {

    implicit val executionContext = system.dispatcher
    implicit val timeout: Timeout = Duration(5, SECONDS)

    val usernameCorrect = SharedTestData.rootUserProfileV1.userData.username.get
    val usernameWrong = "wrong"
    val usernameEmpty = ""

    val passwordCorrect = "test"
    val passwordCorrectHashed = SharedTestData.rootUserProfileV1.userData.password.get
    val passwordWrong = "wrong"
    val passwordEmpty = ""

    val iriCorrect = SharedTestData.rootUserProfileV1.userData.user_id.get
    val iriWrong = "http://data.knora.org/users/wrongiri"
    val iriEmpty = ""

    val mockUserProfileV1 = SharedTestData.rootUserProfileV1

    private val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val getUserProfileByUsername = PrivateMethod[Try[UserProfileV1]]('getUserProfileByUsername)
    val getUserProfileByIri = PrivateMethod[Try[UserProfileV1]]('getUserProfileByIri)
    val authenticateCredentials = PrivateMethod[Try[String]]('authenticateCredentials)

    val rdfDataObjects = Vector()

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())
    }

    "During Authentication " when {
        "called, the 'getUserProfileByUsername' method " should {
            "succeed with the correct 'username' " in {
                Authenticator invokePrivate getUserProfileByUsername(usernameCorrect, system, timeout, executionContext) should be(Success(mockUserProfileV1))
            }
            "fail with the wrong 'username' " in {
                Authenticator invokePrivate getUserProfileByUsername(usernameWrong, system, timeout, executionContext) should be(Failure(InvalidCredentialsException(INVALID_CREDENTIALS_USERNAME_OR_PASSWORD)))
            }
            "fail when not providing a username " in {
                Authenticator invokePrivate getUserProfileByUsername(usernameEmpty, system, timeout, executionContext) should be(Failure(InvalidCredentialsException(INVALID_CREDENTIALS_NO_USERNAME_SUPPLIED)))
            }
        }
        "called, the 'getUserProfileByIri' method " should {
            "succeed with the correct 'iri' " in {
                Authenticator invokePrivate getUserProfileByIri(iriCorrect, system, timeout, executionContext) should be(Success(mockUserProfileV1))
            }
            "fail with the wrong 'iri' " in {
                Authenticator invokePrivate getUserProfileByIri(iriWrong, system, timeout, executionContext) should be(Failure(InvalidCredentialsException(INVALID_CREDENTIALS_USERNAME_OR_PASSWORD)))
            }
            "fail when not providing an 'iri' " in {
                Authenticator invokePrivate getUserProfileByUsername(iriEmpty, system, timeout, executionContext) should be(Failure(InvalidCredentialsException(INVALID_CREDENTIALS_NO_USERNAME_SUPPLIED)))
            }
        }
        "called, the 'authenticateCredentials' method " should {
            "succeed with the correct 'username' / correct 'password' " in {
                Authenticator invokePrivate authenticateCredentials(usernameCorrect, passwordCorrect, false, system) should be(Success("0"))
            }
            "fail with correct 'username' / wrong 'password' " in {
                Authenticator invokePrivate authenticateCredentials(usernameCorrect, passwordWrong, false, system) should be(Failure(InvalidCredentialsException(INVALID_CREDENTIALS_USERNAME_OR_PASSWORD)))
            }
        }
    }


}
