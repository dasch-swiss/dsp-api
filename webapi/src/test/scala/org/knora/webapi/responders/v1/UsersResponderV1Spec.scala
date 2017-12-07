/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
  * To be able to test UsersResponder, we need to be able to start UsersResponder isolated. Now the UsersResponder
  * extend ResponderV1 which messes up testing, as we cannot inject the TestActor system.
  */
package org.knora.webapi.responders.v1


import java.util.UUID

import akka.actor.Props
import akka.actor.Status.Failure
import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.groupmessages.{ChangeGroupApiRequestV1, GroupMembersByIRIGetRequestV1, GroupMembersResponseV1}
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}

import scala.concurrent.duration._


object UsersResponderV1Spec {

    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test the messages received by the [[UsersResponderV1]] actor.
  */
class UsersResponderV1Spec extends CoreSpec(UsersResponderV1Spec.config) with ImplicitSender {

    private implicit val executionContext = system.dispatcher
    private val timeout = 5.seconds

    private val rootUser = SharedAdminTestData.rootUser
    private val rootUserIri = rootUser.userData.user_id.get
    private val rootUserEmail = rootUser.userData.email.get

    private val normalUser = SharedAdminTestData.normalUser
    private val normalUserIri = normalUser.userData.user_id.get

    private val incunabulaUser = SharedAdminTestData.incunabulaProjectAdminUser
    private val incunabulaUserIri = incunabulaUser.userData.user_id.get
    private val incunabulaUserEmail = incunabulaUser.userData.email.get

    private val imagesProjectIri = SharedAdminTestData.imagesProjectInfo.id
    private val imagesReviewerGroupIri = SharedAdminTestData.imagesReviewerGroupInfo.id

    private val actorUnderTest = TestActorRef[UsersResponderV1]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val rdfDataObjects = List() /* sending an empty list, will only load the default ontologies and data */

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(SharedAdminTestData.rootUser)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The UsersResponder " when {

        "asked about all users" should {
            "return a list" in {
                actorUnderTest ! UsersGetRequestV1(rootUser)
                val response = expectMsgType[UsersGetResponseV1](timeout)
                response.users.nonEmpty should be (true)
                response.users.size should be (17)
            }
        }

        "asked about an user identified by 'iri' " should {

            "return a profile if the user (root user) is known" in {
                actorUnderTest ! UserProfileByIRIGetV1(rootUserIri, UserProfileTypeV1.FULL)
                expectMsg(Some(rootUser.ofType(UserProfileTypeV1.FULL)))
            }

            "return a profile if the user (incunabula user) is known" in {
                actorUnderTest ! UserProfileByIRIGetV1(incunabulaUserIri, UserProfileTypeV1.FULL)
                expectMsg(Some(incunabulaUser.ofType(UserProfileTypeV1.FULL)))
            }

            "return 'NotFoundException' when the user is unknown " in {
                actorUnderTest ! UserProfileByIRIGetRequestV1("http://data.knora.org/users/notexisting", UserProfileTypeV1.RESTRICTED, rootUser)
                expectMsg(Failure(NotFoundException(s"User 'http://data.knora.org/users/notexisting' not found")))
            }

            "return 'None' when the user is unknown " in {
                actorUnderTest ! UserProfileByIRIGetV1("http://data.knora.org/users/notexisting", UserProfileTypeV1.RESTRICTED)
                expectMsg(None)
            }
        }

        "asked about an user identified by 'email'" should {

            "return a profile if the user (root user) is known" in {
                actorUnderTest ! UserProfileByEmailGetV1(rootUserEmail, UserProfileTypeV1.RESTRICTED)
                expectMsg(Some(rootUser.ofType(UserProfileTypeV1.RESTRICTED)))
            }

            "return a profile if the user (incunabula user) is known" in {
                actorUnderTest ! UserProfileByEmailGetV1(incunabulaUserEmail, UserProfileTypeV1.RESTRICTED)
                expectMsg(Some(incunabulaUser.ofType(UserProfileTypeV1.RESTRICTED)))
            }

            "return 'NotFoundException' when the user is unknown" in {
                actorUnderTest ! UserProfileByEmailGetRequestV1("userwrong@example.com", UserProfileTypeV1.RESTRICTED, rootUser)
                expectMsg(Failure(NotFoundException(s"User 'userwrong@example.com' not found")))
            }

            "return 'None' when the user is unknown" in {
                actorUnderTest ! UserProfileByEmailGetV1("userwrong@example.com", UserProfileTypeV1.RESTRICTED)
                expectMsg(None)
            }
        }
    }
}
