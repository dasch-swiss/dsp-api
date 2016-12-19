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
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v1.store.triplestoremessages._
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_NAME
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}

import scala.concurrent.duration._


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

    implicit val executionContext = system.dispatcher
    private val timeout = 5.seconds

    val imagesProjectIri = "http://data.knora.org/projects/images"
    val incunabulaProjectIri = "http://data.knora.org/projects/77275339"

    val rootUser = SharedAdminTestData.rootUser
    val rootUserIri = rootUser.userData.user_id.get
    val rootUserName = rootUser.userData.username.get

    val incunabulaUser = SharedAdminTestData.incunabulaProjectAdminUser
    val incunabulaUserIri = incunabulaUser.userData.user_id.get
    val incunabulaUserName = incunabulaUser.userData.username.get

    val actorUnderTest = TestActorRef[UsersResponderV1]
    val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List() /* sending an empty list, will only load the default ontologies and data */

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())
    }

    "The UsersResponder " when {
        "asked about an user identified by 'iri' " should {

            "return a profile if the user (root) is known" in {
                actorUnderTest ! UserProfileByIRIGetRequestV1(rootUserIri, UserProfileType.FULL)
                expectMsg(rootUser.ofType(UserProfileType.FULL))
            }

            "return a profile if the user (incunabulaUser) is known" in {
                actorUnderTest ! UserProfileByIRIGetRequestV1(incunabulaUserIri, UserProfileType.FULL)
                expectMsg(incunabulaUser.ofType(UserProfileType.FULL))
            }

            "return 'NotFoundException' when the user is unknown " in {
                actorUnderTest ! UserProfileByIRIGetRequestV1("http://data.knora.org/users/notexisting", UserProfileType.SHORT)
                expectMsg(Failure(NotFoundException(s"User 'http://data.knora.org/users/notexisting' not found")))
            }
        }
        "asked about an user identified by 'username' " should {

            "return a profile if the user (root) is known " in {
                actorUnderTest ! UserProfileByUsernameGetRequestV1(rootUserName, UserProfileType.SHORT)
                expectMsg(rootUser.ofType(UserProfileType.SHORT))
            }

            "return a profile if the user (testuser) is known " in {
                actorUnderTest ! UserProfileByUsernameGetRequestV1(incunabulaUserName, UserProfileType.SHORT)
                expectMsg(incunabulaUser.ofType(UserProfileType.SHORT))
            }

            "return 'NotFoundException' when the user is unknown " in {
                actorUnderTest ! UserProfileByUsernameGetRequestV1("userwrong", UserProfileType.SHORT)
                expectMsg(Failure(NotFoundException(s"User 'userwrong' not found")))
            }
        }
        "asked to create a new user " should {

            "create the user and return it's profile if the supplied username is unique " in {
                actorUnderTest ! UserCreateRequestV1(
                    NewUserDataV1("dduck", "Donald", "Duck", "donald.duck@example.com", "test", "en"),
                    SharedAdminTestData.anonymousUser,
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
                    NewUserDataV1("root", "", "", "", "test", ""),
                    SharedAdminTestData.anonymousUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(DuplicateValueException(s"User with the username: 'root' already exists")))
            }

            "return 'BadRequestException' if username or password are missing" in {

                /* missing username */
                actorUnderTest ! UserCreateRequestV1(
                    NewUserDataV1("", "", "", "", "test", ""),
                    SharedAdminTestData.anonymousUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Username cannot be empty")))

                /* missing password */
                actorUnderTest ! UserCreateRequestV1(
                    NewUserDataV1("dduck", "", "", "", "", ""),
                    SharedAdminTestData.anonymousUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Password cannot be empty")))
            }
        }
        "asked to update a user " should {

            "update the user " in {

                /* User information is updated by the user */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    propertyIri = OntologyConstants.Foaf.GivenName,
                    newValue = "Donald",
                    userProfile = SharedAdminTestData.normalUser,
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
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    propertyIri = OntologyConstants.Foaf.FamilyName,
                    newValue = "Duck",
                    userProfile = SharedAdminTestData.superUser,
                    UUID.randomUUID
                )
                expectMsgPF(timeout) {
                    case UserOperationResponseV1(updatedUserProfile, requestingUserData) => {
                        // check if information was changed
                        assert(updatedUserProfile.userData.lastname.contains("Duck"))

                        // check if the correct userdata is returned
                        assert(requestingUserData.user_id.contains(SharedAdminTestData.superUser.userData.user_id.get))
                    }
                }

            }

            "return a 'ForbiddenException' if the user requesting update is not the user itself or system admin " in {

                /* User information is updated by other normal user */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedAdminTestData.superUser.userData.user_id.get,
                    propertyIri = OntologyConstants.Foaf.GivenName,
                    newValue = "Donald",
                    userProfile = SharedAdminTestData.normalUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("User information can only be changed by the user itself or a system administrator")))

                /* User information is updated by anonymous */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedAdminTestData.superUser.userData.user_id.get,
                    propertyIri = OntologyConstants.Foaf.GivenName,
                    newValue = ("Donald"),
                    userProfile = SharedAdminTestData.anonymousUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("User information can only be changed by the user itself or a system administrator")))

            }

            "update the user, (deleting) making him inactive " in {
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    propertyIri = OntologyConstants.KnoraBase.IsActiveUser,
                    newValue = false,
                    userProfile = SharedAdminTestData.superUser,
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
    }

}
