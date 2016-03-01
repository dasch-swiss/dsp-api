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

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef}
import akka.util.Timeout
import akka.pattern._
import org.knora.webapi.store._
import org.knora.webapi.{NotFoundException, LiveActorMaker, IRI, CoreSpec}
import org.knora.webapi.messages.v1respondermessages.triplestoremessages._
import org.knora.webapi.messages.v1respondermessages.usermessages.{UserProfileGetRequestV1, UserDataV1, UserProfileByUsernameGetRequestV1, UserProfileV1}
import org.mindrot.jbcrypt.BCrypt

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Failure


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

    val usernameCorrect = "root"
    val usernameWrong = "wrong"
    val usernameEmpty = ""

    val passwordCorrect = "test"
    val passwordCorrectHashed = "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3" // hashed with sha-1
    val passwordWrong = "wrong"
    val passwordEmpty = ""


    val lang = "de"
    val user_id = Some("http://data.knora.org/users/91e19f1e01")
    val token = None
    val username = Some(usernameCorrect)
    val firstname = Some("Administrator")
    val lastname = Some("Admin")
    val email = Some("test@test.ch")
    val password = Some(passwordCorrectHashed)
    val projects = List[IRI]("http://data.knora.org/projects/77275339", "http://data.knora.org/projects/images")

    val rootUserProfileV1 = UserProfileV1(UserDataV1(lang, user_id, token, username, firstname, lastname, email, password), Vector.empty[IRI], projects)

    val user_id_url_encoded: IRI = "http://data.knora.org/users/91e19f1e01"
    val user_id_not_existing_url_encoded: IRI = "http://data.knora.org/users/notexisting"

    val storeResponseUserIdNotFound = SparqlSelectResponse(
        SparqlSelectResponseHeader(Vector("p", "o")),
        SparqlSelectResponseBody(Nil)
    )

    val actorUnderTest = TestActorRef[UsersResponderV1]
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)


    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/images-demo-onto.ttl", name = "http://www.knora.org/ontology/images"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images")
    )

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())
    }

    "The UsersResponder " when {
        "asked about an user identified by 'iri' " should {
            "return a profile if the user is known " in {
                actorUnderTest ! UserProfileGetRequestV1(user_id_url_encoded)
                expectMsg(Some(rootUserProfileV1))
            }
            "return 'None' when the user is unknown " in {
                actorUnderTest ! UserProfileGetRequestV1(user_id_not_existing_url_encoded)
                expectMsg(Failure(NotFoundException(s"User '$user_id_not_existing_url_encoded' not found")))
            }
        }
        "asked about an user identified by 'username' " should {
            "return a profile if the user is known " in {
                actorUnderTest ! UserProfileByUsernameGetRequestV1(usernameCorrect)
                expectMsg(Some(rootUserProfileV1))
            }

            "return 'None' when the user is unknown " in {
                actorUnderTest ! UserProfileByUsernameGetRequestV1(usernameWrong)
                expectMsg((Failure(NotFoundException(s"User '$usernameWrong' not found"))))
            }
        }
    }

}
