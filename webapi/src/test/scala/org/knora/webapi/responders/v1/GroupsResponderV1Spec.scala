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
import org.knora.webapi.SharedAdminTestData._

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

    private val imagesReviewerGroupInfo = SharedAdminTestData.imagesReviewerGroupInfo
    private val imagesProjectAdminGroupInfo = SharedAdminTestData.imagesProjectAdminGroupInfo
    private val imagesProjectMemberGroupInfo = SharedAdminTestData.imagesProjectMemberGroupInfo

    private val rootUserProfileV1 = SharedAdminTestData.rootUser

    private val actorUnderTest = TestActorRef[GroupsResponderV1]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List()

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(SharedAdminTestData.rootUser)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The GroupsResponder " when {

        "asked about a group identified by 'iri' " should {
            "return group info if the group is known " in {
                actorUnderTest ! GroupInfoByIRIGetRequest(imagesReviewerGroupInfo.id, Some(rootUserProfileV1))
                expectMsg(GroupInfoResponseV1(imagesReviewerGroupInfo))
            }
            "return 'NotFoundException' when the group is unknown " in {
                actorUnderTest ! GroupInfoByIRIGetRequest("http://data.knora.org/groups/notexisting", Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"For the given group iri 'http://data.knora.org/groups/notexisting' no information was found")))
            }
        }

        "asked about a group identified by 'name' " should {
            "return group info if the group is known " in {
                actorUnderTest ! GroupInfoByNameGetRequest(imagesProjectAdminGroupInfo.project, imagesProjectAdminGroupInfo.name, Some(rootUserProfileV1))
                expectMsg(GroupInfoResponseV1(imagesProjectAdminGroupInfo))
            }
            "return 'NotFoundException' when the group is unknown " in {
                actorUnderTest ! GroupInfoByNameGetRequest(imagesProjectMemberGroupInfo.project, "groupwrong", Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"For the given group name 'groupwrong' no information was found")))
            }
        }

        "used to modify group information" should {

            val newGroupIri = new MutableTestIri

            "CREATE the group and return the group's info if the supplied group name is unique" in {
                actorUnderTest ! GroupCreateRequestV1(
                    CreateGroupApiRequestV1("NewGroup", Some("NewGroupDescription"), SharedAdminTestData.IMAGES_PROJECT_IRI, true, false),
                    SharedAdminTestData.imagesUser01,
                    UUID.randomUUID
                )

                val received: GroupOperationResponseV1 = expectMsgType[GroupOperationResponseV1](timeout)
                val newGroupInfo = received.group_info

                newGroupInfo.name should equal ("NewGroup")
                newGroupInfo.description should equal (Some("NewGroupDescription"))
                newGroupInfo.project should equal (IMAGES_PROJECT_IRI)
                newGroupInfo.status should equal (true)
                newGroupInfo.selfjoin should equal (false)

                // store for later usage
                newGroupIri.set(newGroupInfo.id)
            }

            "return a 'DuplicateValueException' if the supplied group name is not unique" in {
                actorUnderTest ! GroupCreateRequestV1(
                    CreateGroupApiRequestV1("NewGroup", Some("NewGroupDescription"), SharedAdminTestData.IMAGES_PROJECT_IRI, true, false),
                    SharedAdminTestData.imagesUser01,
                    UUID.randomUUID
                )
                expectMsg(Failure(DuplicateValueException(s"Group with the name: 'NewGroup' already exists")))
            }

            "return 'BadRequestException' if group name or project IRI are missing" in {

                /* missing group name */
                actorUnderTest ! GroupCreateRequestV1(
                    CreateGroupApiRequestV1("", Some("NoNameGroupDescription"), SharedAdminTestData.IMAGES_PROJECT_IRI, true, false),
                    SharedAdminTestData.imagesUser01,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Group name cannot be empty")))

                /* missing project */
                actorUnderTest ! GroupCreateRequestV1(
                    CreateGroupApiRequestV1("OtherNewGroup", Some("OtherNewGroupDescription"), "", true, false),
                    SharedAdminTestData.imagesUser01,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Project IRI cannot be empty")))
            }

            "UPDATE a group" in {
                actorUnderTest ! GroupChangeRequestV1(
                    newGroupIri.get,
                    ChangeGroupApiRequestV1(Some("UpdatedGroupName"), Some("UpdatedDescription")),
                    SharedAdminTestData.imagesUser01,
                    UUID.randomUUID
                )

                val received: GroupOperationResponseV1 = expectMsgType[GroupOperationResponseV1](timeout)
                val updatedGroupInfo = received.group_info

                updatedGroupInfo.name should equal ("UpdatedGroupName")
                updatedGroupInfo.description should equal (Some("UpdatedDescription"))
                updatedGroupInfo.project should equal (SharedAdminTestData.IMAGES_PROJECT_IRI)
                updatedGroupInfo.status should equal (true)
                updatedGroupInfo.selfjoin should equal (false)
            }

            "return 'NotFound' if a not existing group IRI is submitted during update" in {
                actorUnderTest ! GroupChangeRequestV1(
                    groupIri = "http://data.knora.org/groups/notexisting",
                    ChangeGroupApiRequestV1(Some("UpdatedGroupName"), Some("UpdatedDescription")),
                    SharedAdminTestData.imagesUser01,
                    UUID.randomUUID
                )

                expectMsg(Failure(NotFoundException(s"Group 'http://data.knora.org/groups/notexisting' not found. Aborting update request.")))
            }

            "return 'BadRequest' if the new group name already exists inside the project" in {
                actorUnderTest ! GroupChangeRequestV1(
                    newGroupIri.get,
                    ChangeGroupApiRequestV1(Some("Image reviewer"), Some("UpdatedDescription")),
                    SharedAdminTestData.imagesUser01,
                    UUID.randomUUID
                )

                expectMsg(Failure(BadRequestException(s"Group with the name: 'Image reviewer' already exists.")))
            }

            "return 'BadRequest' if nothing would be changed during the update" in {

                an [BadRequestException] should be thrownBy ChangeGroupApiRequestV1(None, None, None, None)

                /*
                actorUnderTest ! GroupChangeRequestV1(
                    newGroupIri.get,
                    ChangeGroupApiRequestV1(None, None, None, None),
                    SharedAdminTestData.imagesUser01,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException(s"No data sent in API request.")))
                */
            }

        }

        "used to query members" should {

            "return all members of a group identified by IRI" in {
                actorUnderTest ! GroupMembersByIRIGetRequestV1(
                    groupIri = SharedAdminTestData.imagesReviewerGroupInfo.id,
                    userProfileV1 = SharedAdminTestData.rootUser
                )
                val received: GroupMembersResponseV1 = expectMsgType[GroupMembersResponseV1](timeout)
                received.members should contain allElementsOf Seq(
                    SharedAdminTestData.multiuserUser.ofType(UserProfileTypeV1.SHORT).userData.user_id.get,
                    SharedAdminTestData.imagesReviewerUser.ofType(UserProfileTypeV1.SHORT).userData.user_id.get
                )
            }

            "return all members of a group identified by shortname / project IRI combination" in {
                actorUnderTest ! GroupMembersByNameGetRequestV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    groupName = SharedAdminTestData.imagesReviewerGroupInfo.name,
                    userProfileV1 = SharedAdminTestData.rootUser
                )
                val received: GroupMembersResponseV1 = expectMsgType[GroupMembersResponseV1](timeout)
                received.members should contain allElementsOf Seq(
                    SharedAdminTestData.multiuserUser.ofType(UserProfileTypeV1.SHORT).userData.user_id.get,
                    SharedAdminTestData.imagesReviewerUser.ofType(UserProfileTypeV1.SHORT).userData.user_id.get
                )
            }

            "return 'NotFound' when the group IRI is unknown" in {
                actorUnderTest ! GroupMembersByIRIGetRequestV1(
                    groupIri = "http://data.knora.org/groups/notexisting",
                    SharedAdminTestData.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Group 'http://data.knora.org/groups/notexisting' not found.")))
            }

            "return 'NotFound' when the group shortname / project IRI combination is unknown" in {
                actorUnderTest ! GroupMembersByNameGetRequestV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    groupName = "groupwrong",
                    SharedAdminTestData.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Group 'groupwrong' not found.")))
            }
        }
    }

}
