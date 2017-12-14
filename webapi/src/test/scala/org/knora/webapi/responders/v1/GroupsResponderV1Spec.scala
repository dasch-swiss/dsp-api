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
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.groupmessages._
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileTypeV1
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.MutableTestIri
import org.knora.webapi.SharedTestDataV1._
import org.knora.webapi.responders.admin.GroupsResponderADM

import scala.concurrent.duration._


object GroupsResponderV1Spec {

    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test the messages received by the [[UsersResponderV1]] actor.
  */
class GroupsResponderV1Spec extends CoreSpec(GroupsResponderV1Spec.config) with ImplicitSender {

    implicit private val executionContext = system.dispatcher
    private val timeout = 5.seconds

    private val imagesReviewerGroupInfo = SharedTestDataV1.imagesReviewerGroupInfo
    private val imagesProjectAdminGroupInfo = SharedTestDataV1.imagesProjectAdminGroupInfo
    private val imagesProjectMemberGroupInfo = SharedTestDataV1.imagesProjectMemberGroupInfo

    private val rootUserProfileV1 = SharedTestDataV1.rootUser

    private val actorUnderTest = TestActorRef[GroupsResponderV1]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List()

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(SharedTestDataV1.rootUser)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The GroupsResponder " when {

        "asked about a group identified by 'iri' " should {
            "return group info if the group is known " in {
                actorUnderTest ! GroupInfoByIRIGetRequestV1(imagesReviewerGroupInfo.id, Some(rootUserProfileV1))
                expectMsg(GroupInfoResponseV1(imagesReviewerGroupInfo))
            }
            "return 'NotFoundException' when the group is unknown " in {
                actorUnderTest ! GroupInfoByIRIGetRequestV1("http://data.knora.org/groups/notexisting", Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"For the given group iri 'http://data.knora.org/groups/notexisting' no information was found")))
            }
        }

        "asked about a group identified by 'name' " should {
            "return group info if the group is known " in {
                actorUnderTest ! GroupInfoByNameGetRequestV1(imagesProjectAdminGroupInfo.project, imagesProjectAdminGroupInfo.name, Some(rootUserProfileV1))
                expectMsg(GroupInfoResponseV1(imagesProjectAdminGroupInfo))
            }
            "return 'NotFoundException' when the group is unknown " in {
                actorUnderTest ! GroupInfoByNameGetRequestV1(imagesProjectMemberGroupInfo.project, "groupwrong", Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"For the given group name 'groupwrong' no information was found")))
            }
        }

        "used to query members" should {

            "return all members of a group identified by IRI" in {
                actorUnderTest ! GroupMembersByIRIGetRequestV1(
                    groupIri = SharedTestDataV1.imagesReviewerGroupInfo.id,
                    userProfileV1 = SharedTestDataV1.rootUser
                )
                val received: GroupMembersResponseV1 = expectMsgType[GroupMembersResponseV1](timeout)
                received.members should contain allElementsOf Seq(
                    SharedTestDataV1.multiuserUser.ofType(UserProfileTypeV1.SHORT).userData.user_id.get,
                    SharedTestDataV1.imagesReviewerUser.ofType(UserProfileTypeV1.SHORT).userData.user_id.get
                )
            }

            "return all members of a group identified by shortname / project IRI combination" in {
                actorUnderTest ! GroupMembersByNameGetRequestV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    groupName = SharedTestDataV1.imagesReviewerGroupInfo.name,
                    userProfileV1 = SharedTestDataV1.rootUser
                )
                val received: GroupMembersResponseV1 = expectMsgType[GroupMembersResponseV1](timeout)
                received.members should contain allElementsOf Seq(
                    SharedTestDataV1.multiuserUser.ofType(UserProfileTypeV1.SHORT).userData.user_id.get,
                    SharedTestDataV1.imagesReviewerUser.ofType(UserProfileTypeV1.SHORT).userData.user_id.get
                )
            }

            "return 'NotFound' when the group IRI is unknown" in {
                actorUnderTest ! GroupMembersByIRIGetRequestV1(
                    groupIri = "http://data.knora.org/groups/notexisting",
                    SharedTestDataV1.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Group 'http://data.knora.org/groups/notexisting' not found.")))
            }

            "return 'NotFound' when the group shortname / project IRI combination is unknown" in {
                actorUnderTest ! GroupMembersByNameGetRequestV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    groupName = "groupwrong",
                    SharedTestDataV1.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Group 'groupwrong' not found.")))
            }
        }
    }

}
