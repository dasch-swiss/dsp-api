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
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
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
    val rootUserEmail = rootUser.userData.email.get

    val incunabulaUser = SharedAdminTestData.incunabulaProjectAdminUser
    val incunabulaUserIri = incunabulaUser.userData.user_id.get
    val incunabulaUserEmail = incunabulaUser.userData.email.get

    val actorUnderTest = TestActorRef[UsersResponderV1]
    val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List() /* sending an empty list, will only load the default ontologies and data */

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(SharedAdminTestData.rootUser)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The UsersResponder " when {
        "asked about an user identified by 'iri' " should {

            "return a profile if the user (root user) is known" in {
                actorUnderTest ! UserProfileByIRIGetRequestV1(rootUserIri, UserProfileType.FULL)
                expectMsg(rootUser.ofType(UserProfileType.FULL))
            }

            "return a profile if the user (incunabula user) is known" in {
                actorUnderTest ! UserProfileByIRIGetRequestV1(incunabulaUserIri, UserProfileType.FULL)
                expectMsg(incunabulaUser.ofType(UserProfileType.FULL))
            }

            "return 'NotFoundException' when the user is unknown " in {
                actorUnderTest ! UserProfileByIRIGetRequestV1("http://data.knora.org/users/notexisting", UserProfileType.RESTRICTED)
                expectMsg(Failure(NotFoundException(s"User 'http://data.knora.org/users/notexisting' not found")))
            }
        }
        "asked about an user identified by 'email'" should {

            "return a profile if the user (root user) is known " in {
                actorUnderTest ! UserProfileByEmailGetRequestV1(rootUserEmail, UserProfileType.RESTRICTED)
                expectMsg(rootUser.ofType(UserProfileType.RESTRICTED))
            }

            "return a profile if the user (incunabula user) is known " in {
                actorUnderTest ! UserProfileByEmailGetRequestV1(incunabulaUserEmail, UserProfileType.RESTRICTED)
                expectMsg(incunabulaUser.ofType(UserProfileType.RESTRICTED))
            }

            "return 'NotFoundException' when the user is unknown " in {
                actorUnderTest ! UserProfileByEmailGetRequestV1("userwrong@example.com", UserProfileType.RESTRICTED)
                expectMsg(Failure(NotFoundException(s"User 'userwrong@example.com' not found")))
            }
        }
        "asked to create a new user " should {

            "create the user and return it's profile if the supplied username is unique " in {
                actorUnderTest ! UserCreateRequestV1(
                    createRequest = CreateUserApiRequestV1(
                        email = "donald.duck@example.com",
                        givenName = "Donald",
                        familyName = "Duck",
                        password ="test",
                        status = true,
                        lang = "en"
                    ),
                    userProfile = SharedAdminTestData.anonymousUser,
                    apiRequestID = UUID.randomUUID
                )
                expectMsgPF(timeout) {
                    case UserOperationResponseV1(newUserProfile, requestingUserData) => {
                        assert(newUserProfile.userData.firstname.get.equals("Donald"))
                        assert(newUserProfile.userData.lastname.get.equals("Duck"))
                        assert(newUserProfile.userData.email.get.equals("donald.duck@example.com"))
                        assert(newUserProfile.userData.lang.equals("en"))
                    }
                }
            }

            "return a 'DuplicateValueException' if the supplied 'email' is not unique" in {
                actorUnderTest ! UserCreateRequestV1(
                    createRequest = CreateUserApiRequestV1(
                        email = "root@example.com",
                        givenName = "Donal",
                        familyName = "Duck",
                        password ="test",
                        status = true,
                        lang = "en"
                    ),
                    SharedAdminTestData.anonymousUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(DuplicateValueException(s"User with the email: 'root@example.com' already exists")))
            }

            "return 'BadRequestException' if 'email' or 'password' or 'givenName' or 'familyName' are missing" in {

                /* missing email */
                actorUnderTest ! UserCreateRequestV1(
                    createRequest = CreateUserApiRequestV1(
                        email = "",
                        givenName = "Donald",
                        familyName = "Duck",
                        password = "test",
                        status = true,
                        lang = "en"
                    ),
                    SharedAdminTestData.anonymousUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Email cannot be empty")))

                /* missing password */
                actorUnderTest ! UserCreateRequestV1(
                    createRequest = CreateUserApiRequestV1(
                        email = "donald.duck@example.com",
                        givenName = "Donald",
                        familyName = "Duck",
                        password = "",
                        status = true,
                        lang = "en"
                    ),
                    SharedAdminTestData.anonymousUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Password cannot be empty")))

                /* missing givenName */
                actorUnderTest ! UserCreateRequestV1(
                    createRequest = CreateUserApiRequestV1(
                        email = "donald.duck@example.com",
                        givenName = "",
                        familyName = "Duck",
                        password = "test",
                        status = true,
                        lang = "en"
                    ),
                    SharedAdminTestData.anonymousUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Given name cannot be empty")))

                /* missing familyName */
                actorUnderTest ! UserCreateRequestV1(
                    createRequest = CreateUserApiRequestV1(
                        email = "donald.duck@example.com",
                        givenName = "Donald",
                        familyName = "",
                        password = "test",
                        status = true,
                        lang = "en"
                    ),
                    SharedAdminTestData.anonymousUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Family name cannot be empty")))
            }
        }
        "asked to update a user" should {

            "update the user" in {

                /* User information is updated by the user */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    updateRequest = UpdateUserApiRequestV1(
                        email = None,
                        givenName = Some("Donald"),
                        familyName = None,
                        lang = None
                    ),
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
                    updateRequest = UpdateUserApiRequestV1(
                        email = None,
                        givenName = None,
                        familyName = Some("Duck"),
                        lang = None
                    ),
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

            "return a 'ForbiddenException' if the user requesting update is not the user itself or system admin" in {

                /* User information is updated by other normal user */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedAdminTestData.superUser.userData.user_id.get,
                    updateRequest = UpdateUserApiRequestV1(
                        email = None,
                        givenName = Some("Donald"),
                        familyName = None,
                        lang = None
                    ),
                    userProfile = SharedAdminTestData.normalUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("User information can only be changed by the user itself or a system administrator")))

                /* User information is updated by anonymous */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedAdminTestData.superUser.userData.user_id.get,
                    updateRequest = UpdateUserApiRequestV1(
                        email = None,
                        givenName = Some("Donald"),
                        familyName = None,
                        lang = None
                    ),
                    userProfile = SharedAdminTestData.anonymousUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("User information can only be changed by the user itself or a system administrator")))

            }

            "update the user's password" in {
                actorUnderTest ! UserChangePasswordRequestV1(
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    changePasswordRequest = ChangeUserPasswordApiRequestV1(
                        oldPassword = "test",
                        newPassword = "test123456"
                    ),
                    userProfile = SharedAdminTestData.normalUser,
                    apiRequestID = UUID.randomUUID()
                )
                expectMsgPF(timeout) {
                    case UserOperationResponseV1(updatedUserProfile, requestingUserData) => {
                        // cant check if password was changed, since it will not be returned. but if no error message
                        // is returned, then I can assume that the password was successfully changed, as this check is
                        // performed in the responder itself.

                        // check if the correct userdata is returned
                        assert(requestingUserData.user_id.contains(SharedAdminTestData.normalUser.userData.user_id.get))
                    }
                }
            }

            "update the user, (deleting) making him inactive " in {
                actorUnderTest ! UserChangeStatusRequestV1(
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    changeStatusRequest = ChangeUserStatusApiRequestV1(newStatus = false),
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
