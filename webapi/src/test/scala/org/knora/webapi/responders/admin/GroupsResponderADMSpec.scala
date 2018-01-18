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
  * extend ResponderADM which messes up testing, as we cannot inject the TestActor system.
  */
package org.knora.webapi.responders.admin

import java.util.UUID

import akka.actor.Props
import akka.actor.Status.Failure
import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.MutableTestIri

import scala.concurrent.duration._


object GroupsResponderADMSpec {

    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test the messages received by the [[org.knora.webapi.responders.admin.UsersResponderADM]] actor.
  */
class GroupsResponderADMSpec extends CoreSpec(GroupsResponderADMSpec.config) with ImplicitSender {

    implicit private val executionContext = system.dispatcher
    private val timeout = 5.seconds

    private val imagesProject = SharedTestDataADM.imagesProject
    private val imagesReviewerGroup = SharedTestDataADM.imagesReviewerGroup
    private val imagesProjectAdminGroup = SharedTestDataADM.imagesProjectAdminGroup
    private val imagesProjectMemberGroup = SharedTestDataADM.imagesProjectMemberGroup

    private val rootUser = SharedTestDataADM.rootUser

    private val actorUnderTest = TestActorRef[GroupsResponderADM]
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

        "asked about all groups" should {
            "return a list" in {
                actorUnderTest ! GroupsGetRequestADM(SharedTestDataADM.rootUser)
                val response = expectMsgType[GroupsGetResponseADM](timeout)
                // println(response.users)
                response.groups.nonEmpty should be (true)
                response.groups.size should be (1)
            }
        }

        "asked about a group identified by 'iri' " should {
            "return group info if the group is known " in {
                actorUnderTest ! GroupGetRequestADM(imagesReviewerGroup.id, rootUser)
                expectMsg(GroupGetResponseADM(imagesReviewerGroup))
            }
            "return 'NotFoundException' when the group is unknown " in {
                actorUnderTest ! GroupGetRequestADM("http://data.knora.org/groups/notexisting", rootUser)
                expectMsg(Failure(NotFoundException(s"For the given group iri 'http://data.knora.org/groups/notexisting' no information was found")))
            }
        }

        "used to modify group information" should {

            val newGroupIri = new MutableTestIri

            "CREATE the group and return the group's info if the supplied group name is unique" in {
                actorUnderTest ! GroupCreateRequestADM(
                    CreateGroupApiRequestADM("NewGroup", Some("""NewGroupDescription with "quotes" and <html tag>"""), SharedTestDataADM.IMAGES_PROJECT_IRI, status = true, selfjoin = false),
                    SharedTestDataADM.imagesUser01,
                    UUID.randomUUID
                )

                val received: GroupOperationResponseADM = expectMsgType[GroupOperationResponseADM](timeout)
                val newGroupInfo = received.group

                newGroupInfo.name should equal ("NewGroup")
                newGroupInfo.description should equal ("""NewGroupDescription with "quotes" and <html tag>""")
                newGroupInfo.project should equal (imagesProject)
                newGroupInfo.status should equal (true)
                newGroupInfo.selfjoin should equal (false)

                // store for later usage
                newGroupIri.set(newGroupInfo.id)
            }

            "return a 'DuplicateValueException' if the supplied group name is not unique" in {
                actorUnderTest ! GroupCreateRequestADM(
                    CreateGroupApiRequestADM("NewGroup", Some("NewGroupDescription"), SharedTestDataADM.IMAGES_PROJECT_IRI, status = true, selfjoin = false),
                    SharedTestDataADM.imagesUser01,
                    UUID.randomUUID
                )
                expectMsg(Failure(DuplicateValueException(s"Group with the name: 'NewGroup' already exists")))
            }

            "return 'BadRequestException' if group name or project IRI are missing" in {

                /* missing group name */
                actorUnderTest ! GroupCreateRequestADM(
                    CreateGroupApiRequestADM("", Some("NoNameGroupDescription"), SharedTestDataADM.IMAGES_PROJECT_IRI, status = true, selfjoin = false),
                    SharedTestDataADM.imagesUser01,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Group name cannot be empty")))

                /* missing project */
                actorUnderTest ! GroupCreateRequestADM(
                    CreateGroupApiRequestADM("OtherNewGroup", Some("OtherNewGroupDescription"), "", status = true, selfjoin = false),
                    SharedTestDataADM.imagesUser01,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Project IRI cannot be empty")))
            }

            "UPDATE a group" in {
                actorUnderTest ! GroupChangeRequestADM(
                    newGroupIri.get,
                    ChangeGroupApiRequestADM(Some("UpdatedGroupName"), Some("""UpdatedDescription with "quotes" and <html tag>""")),
                    SharedTestDataADM.imagesUser01,
                    UUID.randomUUID
                )

                val received: GroupOperationResponseADM = expectMsgType[GroupOperationResponseADM](timeout)
                val updatedGroupInfo = received.group

                updatedGroupInfo.name should equal ("UpdatedGroupName")
                updatedGroupInfo.description should equal ("""UpdatedDescription with "quotes" and <html tag>""")
                updatedGroupInfo.project should equal (imagesProject)
                updatedGroupInfo.status should equal (true)
                updatedGroupInfo.selfjoin should equal (false)
            }

            "return 'NotFound' if a not-existing group IRI is submitted during update" in {
                actorUnderTest ! GroupChangeRequestADM(
                    groupIri = "http://data.knora.org/groups/notexisting",
                    ChangeGroupApiRequestADM(Some("UpdatedGroupName"), Some("UpdatedDescription")),
                    SharedTestDataADM.imagesUser01,
                    UUID.randomUUID
                )

                expectMsg(Failure(NotFoundException(s"Group 'http://data.knora.org/groups/notexisting' not found. Aborting update request.")))
            }

            "return 'BadRequest' if the new group name already exists inside the project" in {
                actorUnderTest ! GroupChangeRequestADM(
                    newGroupIri.get,
                    ChangeGroupApiRequestADM(Some("Image reviewer"), Some("UpdatedDescription")),
                    SharedTestDataADM.imagesUser01,
                    UUID.randomUUID
                )

                expectMsg(Failure(BadRequestException(s"Group with the name: 'Image reviewer' already exists.")))
            }

            "return 'BadRequest' if nothing would be changed during the update" in {

                an [BadRequestException] should be thrownBy ChangeGroupApiRequestADM(None, None, None, None)

                /*
                actorUnderTest ! GroupChangeRequestADM(
                    newGroupIri.get,
                    ChangeGroupApiRequestADM(None, None, None, None),
                    SharedAdminTestData.imagesUser01,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException(s"No data sent in API request.")))
                */
            }

        }

        "used to query members" should {

            "return all members of a group identified by IRI" in {
                actorUnderTest ! GroupMembersGetRequestADM(
                    groupIri = SharedTestDataADM.imagesReviewerGroup.id,
                    requestingUser = SharedTestDataADM.rootUser
                )
                val received: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)
                received.members.map(_.id) should contain allElementsOf Seq(
                    SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.RESTRICTED),
                    SharedTestDataADM.imagesReviewerUser.ofType(UserInformationTypeADM.RESTRICTED)
                ).map(_.id)
            }

            "return 'NotFound' when the group IRI is unknown" in {
                actorUnderTest ! GroupMembersGetRequestADM(
                    groupIri = "http://data.knora.org/groups/notexisting",
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Group 'http://data.knora.org/groups/notexisting' not found.")))
            }
        }
    }

}
