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

/**
  * To be able to test UsersResponder, we need to be able to start UsersResponder isolated. Now the UsersResponder
  * extend ResponderV1 which messes up testing, as we cannot inject the TestActor system.
  */
package org.knora.webapi.responders.v1

import akka.testkit.{ImplicitSender, TestActorRef}
import akka.util.Timeout
import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileByUsernameGetRequestV1, UserProfileV1}
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectResponse, SparqlSelectResponseBody, SparqlSelectResponseHeader}
import org.mindrot.jbcrypt.BCrypt

import scala.concurrent.duration._


/*
 *  This test needs a running http layer, so that different api access authentication schemes can be tested
 *  - Browser basic auth
 *  - Basic auth over API
 *  - Username/password over API
 *  - API Key based authentication
 */

class UsersResponderV1Spec extends CoreSpec() with ImplicitSender {

    implicit val timeout: Timeout = Duration(5, SECONDS)
    implicit val executionContext = system.dispatcher

    val usernameCorrect = "isubotic"
    val usernameWrong = "usernamewrong"
    val usernameEmpty = ""

    val passUnhashed = "123456"
    // gensalt's log_rounds parameter determines the complexity
    // the work factor is 2**log_rounds, and the default is 10
    val passHashed: String = BCrypt.hashpw(passUnhashed, BCrypt.gensalt(12));
    val passEmpty = ""

    val lang = "en"
    val user_id = Some("http://data.knora.org/users/b83acc5f05")
    val token = None
    val username = Some(usernameCorrect)
    val firstname = Some("Ivan")
    val lastname = Some("Subotic")
    val email = Some("ivan.subotic@unibas.ch")
    val password = Some(passHashed)

    val mockUserProfileV1 = UserProfileV1(UserDataV1(lang, user_id, token, username, firstname, lastname, email, password), Nil, Nil)

    /*
    val gaga = new ErrorHandlingMap(Map("x" -> "y"), "No such foo found: {{ key }}")

    gaga("bar")

    val storeResponseUserIdFound = SparqlSelectResponse(
        SparqlSelectJsonResponse(SparqlSelectJsonResponseHeader(Vector("")), SparqlSelectJsonResponseBody(Vector(VariableResultsRow(gaga)))),
        "",
        ""
    )

    */

    val storeResponseUserIdNotFound = SparqlSelectResponse(
        SparqlSelectResponseHeader(Vector("p", "o")),
        SparqlSelectResponseBody(Nil)
    )

    /*
        val mockStoreManagerActor = actor(STORE_MANAGER_ACTOR_NAME)(new Act {
            become {
                case SparqlPebbleSelectRequest(templatename, templateContext) => {
                  if (templatename == "get-user-by-username" && templateContext == Map("userId" -> usernameWrong)) {
                      println(templateContext)
                      sender ! storeResponseUserIdNotFound
                  }
                }
            }
        })
    */
    val actorUnderTest = TestActorRef[UsersResponderV1]

    "The UsersResponder " when {
        "asked about an existing user identified by 'username' " must {
            "return a profile if user is known " ignore {
                actorUnderTest ! UserProfileByUsernameGetRequestV1(usernameCorrect)
                expectMsg(Some(mockUserProfileV1))
            }

            "return 'None' when user is unknown " ignore {
                actorUnderTest ! UserProfileByUsernameGetRequestV1(usernameWrong)
                expectMsg(None)
            }
        }
    }

}
