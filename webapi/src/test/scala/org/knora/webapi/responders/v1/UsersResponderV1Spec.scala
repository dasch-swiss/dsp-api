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
import com.typesafe.config.ConfigFactory
import org.knora.webapi
import org.knora.webapi.messages.v1respondermessages.triplestoremessages._
import org.knora.webapi.messages.v1respondermessages.usermessages._
import org.knora.webapi.store._
import org.knora.webapi._

import scala.concurrent.duration._
import akka.actor.Status.Failure


object UsersResponderV1Spec {

    val config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test the messages received by the [[UsersResponderV1]] actor.
  */
class UsersResponderV1Spec extends CoreSpec(UsersResponderV1Spec.config) with ImplicitSender {

    implicit val timeout: Timeout = Duration(5, SECONDS)
    implicit val executionContext = system.dispatcher

    val requested_user_id_existing: IRI = "http://data.knora.org/users/91e19f1e01"
    val requested_user_id_not_existing: IRI = "http://data.knora.org/users/notexisting"

    val requested_username_existing = "root"
    val requested_username_not_existing = "userwrong"

    val lang = "de"
    val user_id = Some(requested_user_id_existing)
    val token = None
    val username = Some(requested_username_existing)
    val firstname = Some("Administrator")
    val lastname = Some("Admin")
    val email = Some("administrator.admin@example.com")
    val password = None
    val passwordSalt = None
    val projects = List[IRI]("http://data.knora.org/projects/77275339", "http://data.knora.org/projects/images")

    val rootUserProfileV1 = UserProfileV1(UserDataV1(lang, user_id, token, username, firstname, lastname, email, password, passwordSalt), Vector.empty[IRI], Vector.empty[IRI])

    val actorUnderTest = TestActorRef[UsersResponderV1]
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val defaultUser = UserProfileV1(UserDataV1("en"))
    val newNonUniqueUser = NewUserDataV1("root", "", "", "", "", "")

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/images-demo-onto.ttl", name = "http://www.knora.org/ontology/images"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images"),
        RdfDataObject(path = "_test_data/all_data/admin-data.ttl", name = "http://www.knora.org/data/admin")
    )

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())
    }

    "The UsersResponder " when {
        "asked about an user identified by 'iri' " should {
            "return a profile if the user is known " in {
                actorUnderTest ! UserProfileByIRIGetRequestV1(requested_user_id_existing, true)
                expectMsg(Some(rootUserProfileV1))
            }
            "return 'None' when the user is unknown " in {
                actorUnderTest ! UserProfileByIRIGetRequestV1(requested_user_id_not_existing, true)
                expectMsg(Failure(NotFoundException(s"User '$requested_user_id_not_existing' not found")))
            }
        }
        "asked about an user identified by 'username' " should {
            "return a profile if the user is known " in {
                actorUnderTest ! UserProfileByUsernameGetRequestV1(requested_username_existing, true)
                expectMsg(Some(rootUserProfileV1))
            }

            "return 'None' when the user is unknown " in {
                actorUnderTest ! UserProfileByUsernameGetRequestV1(requested_username_not_existing, true)
                expectMsg(Failure(NotFoundException(s"User '$requested_username_not_existing' not found")))
            }
        }
        "asked to create a new user " should {
            "create the user and return it's profile if the supplied username is unique " in {

            }
            "return a 'DuplicateValueException' if the supplied username is not unique " in {
                actorUnderTest ! UserCreateRequestV1(newNonUniqueUser, defaultUser)
                expectMsg(Failure(DuplicateValueException(s"User with the username: '${newNonUniqueUser.username}' already exists")))
            }
        }
    }

}
