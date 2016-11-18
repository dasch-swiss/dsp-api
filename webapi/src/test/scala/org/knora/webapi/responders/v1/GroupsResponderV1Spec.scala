/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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
import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.groupmessages._
import org.knora.webapi.messages.v1.store.triplestoremessages._
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_NAME
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}

import scala.concurrent.duration._


object GroupsResponderV1Spec {

    val config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test the messages received by the [[UsersResponderV1]] actor.
  */
class GroupsResponderV1Spec extends CoreSpec(GroupsResponderV1Spec.config) with ImplicitSender {

    implicit val executionContext = system.dispatcher
    private val timeout = 5.seconds

    val imageReviewerFullGroupInfo = SharedAdminTestData.imageReviewerGroupInfoV1
    val imageReviewerSafeGroupInfo = SharedAdminTestData.imageReviewerGroupInfoV1.ofType(GroupInfoType.SAFE)

    val imagesProjectAdminFullGroupInfo = SharedAdminTestData.imagesProjectAdminGroupInfoV1
    val imagesProjectAdminSafeGroupInfo = SharedAdminTestData.imagesProjectAdminGroupInfoV1.ofType(GroupInfoType.SAFE)

    val imagesProjectMemberFullGroupInfo = SharedAdminTestData.imagesProjectMemberGroupInfoV1
    val imagesProjectMemberSafeGroupInfo = SharedAdminTestData.imagesProjectMemberGroupInfoV1.ofType(GroupInfoType.SAFE)

    val rootUserProfileV1 = SharedAdminTestData.rootUserProfileV1

    val actorUnderTest = TestActorRef[GroupsResponderV1]
    val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List()

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())
    }

    "The GroupsResponder " when {
        "asked about a group identified by 'iri' " should {
            "return full group info if the group is known " in {
                actorUnderTest ! GroupInfoByIRIGetRequest(imageReviewerFullGroupInfo.id, GroupInfoType.FULL, Some(rootUserProfileV1))
                expectMsg(GroupInfoResponseV1(imageReviewerFullGroupInfo, Some(rootUserProfileV1.userData)))
            }
            "return short group info if the group is known " in {
                actorUnderTest ! GroupInfoByIRIGetRequest(imageReviewerSafeGroupInfo.id, GroupInfoType.SAFE, Some(rootUserProfileV1))
                expectMsg(GroupInfoResponseV1(imageReviewerSafeGroupInfo, Some(rootUserProfileV1.userData)))
            }
            "return 'NotFoundException' when the group is unknown " in {
                actorUnderTest ! GroupInfoByIRIGetRequest("http://data.knora.org/groups/notexisting", GroupInfoType.FULL, Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"For the given group iri 'http://data.knora.org/groups/notexisting' no information was found")))
            }
        }
        "asked about a group identified by 'name' " should {
            "return full group info if the group is known " in {
                actorUnderTest ! GroupInfoByNameGetRequest(imagesProjectAdminFullGroupInfo.belongsToProject, imagesProjectAdminFullGroupInfo.name, GroupInfoType.FULL, Some(rootUserProfileV1))
                expectMsg(GroupInfoResponseV1(imagesProjectAdminFullGroupInfo, Some(rootUserProfileV1.userData)))
            }
            "return short group info if the group is known " in {
                actorUnderTest ! GroupInfoByNameGetRequest(imagesProjectMemberSafeGroupInfo.belongsToProject, imagesProjectMemberSafeGroupInfo.name, GroupInfoType.SAFE, Some(rootUserProfileV1))
                expectMsg(GroupInfoResponseV1(imagesProjectMemberSafeGroupInfo, Some(rootUserProfileV1.userData)))
            }
            "return 'NotFoundException' when the group is unknown " in {
                actorUnderTest ! GroupInfoByNameGetRequest(imagesProjectMemberFullGroupInfo.belongsToProject,"groupwrong", GroupInfoType.FULL, Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"For the given group name 'groupwrong' no information was found")))
            }
        }
        "asked to create a new group " should {
            "create the group and return the group's full info if the supplied group name is unique " in {
                actorUnderTest ! GroupCreateRequestV1(
                    NewGroupInfoV1("NewGroup", Some("NewGroupDescription"), "http://data.knora.org/projects/images", true, false),
                    SharedAdminTestData.user01UserProfileV1,
                    UUID.randomUUID
                )
                expectMsgPF(timeout) {
                    case GroupOperationResponseV1(newGroupInfo, requestingUserData) => {
                        newGroupInfo.name should equal ("NewGroup")
                        newGroupInfo.description should equal (Some("NewGroupDescription"))
                        newGroupInfo.belongsToProject should equal ("http://data.knora.org/projects/images")
                        newGroupInfo.isActiveGroup should equal (true)
                        newGroupInfo.hasSelfJoinEnabled should equal (false)
                    }
                }
            }
            "return a 'DuplicateValueException' if the supplied group name is not unique " in {
                actorUnderTest ! GroupCreateRequestV1(
                    NewGroupInfoV1("NewGroup", Some("NewGroupDescription"), "http://data.knora.org/projects/images", true, false),
                    SharedAdminTestData.user01UserProfileV1,
                    UUID.randomUUID
                )
                expectMsg(Failure(DuplicateValueException(s"Group with the name: 'NewGroup' already exists")))
            }
            "return 'BadRequestException' if group name or project IRI are missing" in {

                /* missing group name */
                actorUnderTest ! GroupCreateRequestV1(
                    NewGroupInfoV1("", Some("NoNameGroupDescription"), "http://data.knora.org/projects/images", true, false),
                    SharedAdminTestData.user01UserProfileV1,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Group name cannot be empty")))

                /* missing project */
                actorUnderTest ! GroupCreateRequestV1(
                    NewGroupInfoV1("OtherNewGroup", Some("OtherNewGroupDescription"), "", true, false),
                    SharedAdminTestData.user01UserProfileV1,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Project IRI cannot be empty")))
            }
        }
        /*
        "asked to update a user " should {
            "update the user " in {

                /* User information is updated by the user */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedTestData.normaluserUserProfileV1.userData.user_id.get,
                    propertyIri = OntologyConstants.Foaf.GivenName,
                    newValue = "Donald",
                    userProfile = SharedTestData.normaluserUserProfileV1,
                    UUID.randomUUID
                )
                expectMsgPF(timeout) {
                    case UserOperationResponseV1(updatedUserProfile, requestingUserData) => {
                        // check if information was changed
                        assert(updatedUserProfile.userData.firstname.contains("Donald"))

                        // check if correct and updated userdata is returned
                        assert(requestingUserData.firstname.contains("Donald"))
                    }
                }

                /* User information is updated by a system admin */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedTestData.normaluserUserProfileV1.userData.user_id.get,
                    propertyIri = OntologyConstants.Foaf.FamilyName,
                    newValue = "Duck",
                    userProfile = SharedTestData.superuserUserProfileV1,
                    UUID.randomUUID
                )
                expectMsgPF(timeout) {
                    case UserOperationResponseV1(updatedUserProfile, requestingUserData) => {
                        // check if information was changed
                        assert(updatedUserProfile.userData.lastname.contains("Duck"))

                        // check if the correct userdata is returned
                        assert(requestingUserData.user_id.contains(SharedTestData.superuserUserProfileV1.userData.user_id.get))
                    }
                }

            }
            "return a 'ForbiddenException' if the user requesting update is not the user itself or system admin " in {

                /* User information is updated by other normal user */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedTestData.superuserUserProfileV1.userData.user_id.get,
                    propertyIri = OntologyConstants.Foaf.GivenName,
                    newValue = "Donald",
                    userProfile = SharedTestData.normaluserUserProfileV1,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("User information can only be changed by the user itself or a system administrator")))

                /* User information is updated by anonymous */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedTestData.superuserUserProfileV1.userData.user_id.get,
                    propertyIri = OntologyConstants.Foaf.GivenName,
                    newValue = ("Donald"),
                    userProfile = SharedTestData.anonymousUserProfileV1,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("User information can only be changed by the user itself or a system administrator")))

            }
            "return a 'ForbiddenException' if the update gives SA rights but the user requesting the update is not SA " in {
                /* User information is updated by the user */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedTestData.normaluserUserProfileV1.userData.user_id.get,
                    propertyIri = OntologyConstants.KnoraBase.IsSystemAdmin,
                    newValue = true,
                    userProfile = SharedTestData.normaluserUserProfileV1,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("Giving an user system admin rights can only be performed by another system admin")))
            }
            "update the user, giving him SA rights " in {
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedTestData.normaluserUserProfileV1.userData.user_id.get,
                    propertyIri = OntologyConstants.KnoraBase.IsSystemAdmin,
                    newValue = true,
                    userProfile = SharedTestData.superuserUserProfileV1,
                    UUID.randomUUID
                )
                expectMsgPF(timeout) {
                    case UserOperationResponseV1(updatedUserProfile, requestingUserData) => {
                        // check if information was changed
                        assert(updatedUserProfile.userData.isSystemAdmin.contains(true))
                    }
                }
            }
            "update the user, (deleting) making him inactive " in {
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedTestData.normaluserUserProfileV1.userData.user_id.get,
                    propertyIri = OntologyConstants.KnoraBase.IsActiveUser,
                    newValue = false,
                    userProfile = SharedTestData.superuserUserProfileV1,
                    UUID.randomUUID
                )
                expectMsgPF(timeout) {
                    case UserOperationResponseV1(updatedUserProfile, requestingUserData) => {
                        // check if information was changed
                        assert(updatedUserProfile.userData.isActiveUser.contains(false))
                    }
                }

            }
        }
        */

    }

}
