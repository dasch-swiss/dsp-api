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

import java.util.UUID

import akka.actor.Props
import akka.actor.Status.Failure
import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.groupmessages.{GroupInfoByIRIGetRequest, GroupInfoByNameGetRequest, GroupInfoResponseV1, GroupInfoType}
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v1.store.triplestoremessages._
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

    val imgcontriFullGroupInfo = SharedTestData.imgcontriFullGroupInfoV1
    val imgcontriShortGroupInfo = SharedTestData.imgcontriShortGroupInfoV1

    val rootUserProfileV1 = SharedTestData.rootUserProfileV1

    val actorUnderTest = TestActorRef[GroupsResponderV1]
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/all_data/admin-data.ttl", name = "http://www.knora.org/data/admin")
    )

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())
    }

    "The GroupsResponder " when {
        "asked about a group identified by 'iri' " should {
            "return full group info if the group is known " in {
                actorUnderTest ! GroupInfoByIRIGetRequest(imgcontriFullGroupInfo.id, GroupInfoType.FULL, Some(rootUserProfileV1))
                expectMsg(GroupInfoResponseV1(imgcontriFullGroupInfo, Some(rootUserProfileV1.userData)))
            }
            "return short group info if the group is known " in {
                actorUnderTest ! GroupInfoByIRIGetRequest(imgcontriShortGroupInfo.id, GroupInfoType.SHORT, Some(rootUserProfileV1))
                expectMsg(GroupInfoResponseV1(imgcontriShortGroupInfo, Some(rootUserProfileV1.userData)))
            }
            "return 'NotFoundException' when the group is unknown " in {
                actorUnderTest ! GroupInfoByIRIGetRequest("http://data.knora.org/groups/notexisting", GroupInfoType.FULL, Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"For the given group iri 'http://data.knora.org/groups/notexisting' no information was found")))
            }
        }
        "asked about a group identified by 'name' " should {
            "return full group info if the group is known " in {
                actorUnderTest ! GroupInfoByNameGetRequest(imgcontriFullGroupInfo.name, GroupInfoType.FULL, Some(rootUserProfileV1))
                expectMsg(GroupInfoResponseV1(imgcontriFullGroupInfo, Some(rootUserProfileV1.getCleanUserProfileV1.userData)))
            }
            "return short group info if the group is known " in {
                actorUnderTest ! GroupInfoByNameGetRequest(imgcontriFullGroupInfo.name, GroupInfoType.SHORT, Some(rootUserProfileV1))
                expectMsg(GroupInfoResponseV1(imgcontriShortGroupInfo, Some(rootUserProfileV1.getCleanUserProfileV1.userData)))
            }
            "return 'NotFoundException' when the group is unknown " in {
                actorUnderTest ! GroupInfoByNameGetRequest("groupwrong", GroupInfoType.FULL, Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"Group 'groupwrong' not found")))
            }
        }
        /*
        "asked to create a new user " should {
            "create the user and return it's profile if the supplied username is unique " in {
                actorUnderTest ! UserCreateRequestV1(
                    NewUserDataV1("dduck", "Donald", "Duck", "donald.duck@example.com", "test", false, "en"),
                    SharedTestData.anonymousUserProfileV1,
                    UUID.randomUUID
                )
                expectMsgPF(timeout) {
                    case UserOperationResponseV1(newUserProfile, requestingUserData) => {
                        assert(newUserProfile.userData.username.get.equals("dduck"))
                        assert(newUserProfile.userData.firstname.get.equals("Donald"))
                        assert(newUserProfile.userData.lastname.get.equals("Duck"))
                        assert(newUserProfile.userData.email.get.equals("donald.duck@example.com"))
                        assert(newUserProfile.userData.lang.equals("en"))
                    }
                }
            }
            "return a 'DuplicateValueException' if the supplied username is not unique " in {
                actorUnderTest ! UserCreateRequestV1(
                    NewUserDataV1("root", "", "", "", "test", false, ""),
                    SharedTestData.anonymousUserProfileV1,
                    UUID.randomUUID
                )
                expectMsg(Failure(DuplicateValueException(s"User with the username: 'root' already exists")))
            }
            "return 'BadRequestException' if username or password are missing" in {

                /* missing username */
                actorUnderTest ! UserCreateRequestV1(
                    NewUserDataV1("", "", "", "", "test", false, ""),
                    SharedTestData.anonymousUserProfileV1,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Username cannot be empty")))

                /* missing password */
                actorUnderTest ! UserCreateRequestV1(
                    NewUserDataV1("dduck", "", "", "", "", false, ""),
                    SharedTestData.anonymousUserProfileV1,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Password cannot be empty")))
            }
        }
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
