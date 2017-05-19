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
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v1.store.triplestoremessages._
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_NAME
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

    private val actorUnderTest = TestActorRef[UsersResponderV1]
    private val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
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
                response.users.size should be (16)
            }
        }
        "asked about an user identified by 'iri' " should {

            "return a profile if the user (root user) is known" in {
                actorUnderTest ! UserProfileByIRIGetV1(rootUserIri, UserProfileType.FULL)
                expectMsg(Some(rootUser.ofType(UserProfileType.FULL)))
            }

            "return a profile if the user (incunabula user) is known" in {
                actorUnderTest ! UserProfileByIRIGetV1(incunabulaUserIri, UserProfileType.FULL)
                expectMsg(Some(incunabulaUser.ofType(UserProfileType.FULL)))
            }

            "return 'NotFoundException' when the user is unknown " in {
                actorUnderTest ! UserProfileByIRIGetRequestV1("http://data.knora.org/users/notexisting", UserProfileType.RESTRICTED, rootUser)
                expectMsg(Failure(NotFoundException(s"User 'http://data.knora.org/users/notexisting' not found")))
            }

            "return 'None' when the user is unknown " in {
                actorUnderTest ! UserProfileByIRIGetV1("http://data.knora.org/users/notexisting", UserProfileType.RESTRICTED)
                expectMsg(None)
            }
        }
        "asked about an user identified by 'email'" should {

            "return a profile if the user (root user) is known" in {
                actorUnderTest ! UserProfileByEmailGetV1(rootUserEmail, UserProfileType.RESTRICTED)
                expectMsg(Some(rootUser.ofType(UserProfileType.RESTRICTED)))
            }

            "return a profile if the user (incunabula user) is known" in {
                actorUnderTest ! UserProfileByEmailGetV1(incunabulaUserEmail, UserProfileType.RESTRICTED)
                expectMsg(Some(incunabulaUser.ofType(UserProfileType.RESTRICTED)))
            }

            "return 'NotFoundException' when the user is unknown" in {
                actorUnderTest ! UserProfileByEmailGetRequestV1("userwrong@example.com", UserProfileType.RESTRICTED, rootUser)
                expectMsg(Failure(NotFoundException(s"User 'userwrong@example.com' not found")))
            }

            "return 'None' when the user is unknown" in {
                actorUnderTest ! UserProfileByEmailGetV1("userwrong@example.com", UserProfileType.RESTRICTED)
                expectMsg(None)
            }
        }
        "asked to create a new user" should {

            "create the user and return it's profile if the supplied email is unique " in {
                actorUnderTest ! UserCreateRequestV1(
                    createRequest = CreateUserApiRequestV1(
                        email = "donald.duck@example.com",
                        givenName = "Donald",
                        familyName = "Duck",
                        password = "test",
                        status = true,
                        lang = "en"
                    ),
                    userProfile = SharedAdminTestData.anonymousUser,
                    apiRequestID = UUID.randomUUID
                )
                expectMsgPF(timeout) {
                    case UserOperationResponseV1(newUserProfile) => {
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
                        password = "test",
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
                actorUnderTest ! UserChangeBasicUserDataRequestV1(
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    changeUserRequest = ChangeUserApiRequestV1(
                        email = None,
                        givenName = Some("Donald"),
                        familyName = None,
                        lang = None
                    ),
                    userProfile = SharedAdminTestData.normalUser,
                    UUID.randomUUID
                )

                val response1 = expectMsgType[UserOperationResponseV1](timeout)
                response1.userProfile.userData.firstname.get should equal ("Donald")

                /* User information is updated by a system admin */
                actorUnderTest ! UserChangeBasicUserDataRequestV1(
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    changeUserRequest = ChangeUserApiRequestV1(
                        email = None,
                        givenName = None,
                        familyName = Some("Duck"),
                        lang = None
                    ),
                    userProfile = SharedAdminTestData.superUser,
                    UUID.randomUUID
                )

                val response2 = expectMsgType[UserOperationResponseV1](timeout)
                response2.userProfile.userData.lastname.get should equal ("Duck")

                /* User information is updated by a system admin */
                actorUnderTest ! UserChangeBasicUserDataRequestV1(
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    changeUserRequest = ChangeUserApiRequestV1(
                        email = None,
                        givenName = Some(SharedAdminTestData.normalUser.userData.firstname.get),
                        familyName = Some(SharedAdminTestData.normalUser.userData.lastname.get),
                        lang = None
                    ),
                    userProfile = SharedAdminTestData.superUser,
                    UUID.randomUUID
                )

                val response3 = expectMsgType[UserOperationResponseV1](timeout)
                response3.userProfile.userData.firstname.get should equal (SharedAdminTestData.normalUser.userData.firstname.get)
                response3.userProfile.userData.lastname.get should equal (SharedAdminTestData.normalUser.userData.lastname.get)

            }

            "update the user's password" in {
                actorUnderTest ! UserChangePasswordRequestV1(
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    changeUserRequest = ChangeUserApiRequestV1(
                        oldPassword = Some("test"),
                        newPassword = Some("test123456")
                    ),
                    userProfile = SharedAdminTestData.normalUser,
                    apiRequestID = UUID.randomUUID()
                )

                // Can't check if password was changed directly, since it will not be returned. If no error message
                // is returned, then I can assume that the password was successfully changed, as this check is
                // performed in the responder itself.
                expectMsgType[UserOperationResponseV1](timeout)


                // By doing the change again, I can check if the first change was indeed successful.
                actorUnderTest ! UserChangePasswordRequestV1(
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    changeUserRequest = ChangeUserApiRequestV1(
                        oldPassword = Some("test123456"),
                        newPassword = Some("test")
                    ),
                    userProfile = SharedAdminTestData.normalUser,
                    apiRequestID = UUID.randomUUID()
                )

                expectMsgType[UserOperationResponseV1](timeout)
            }

            "update the user's status, (deleting) making him inactive " in {
                actorUnderTest ! UserChangeStatusRequestV1(
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    changeUserRequest = ChangeUserApiRequestV1(newUserStatus = Some(false)),
                    userProfile = SharedAdminTestData.superUser,
                    UUID.randomUUID()
                )

                val response1 = expectMsgType[UserOperationResponseV1](timeout)
                response1.userProfile.userData.isActiveUser.get should equal (false)

                actorUnderTest ! UserChangeStatusRequestV1(
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    changeUserRequest = ChangeUserApiRequestV1(newUserStatus = Some(true)),
                    userProfile = SharedAdminTestData.superUser,
                    UUID.randomUUID()
                )

                val response2 = expectMsgType[UserOperationResponseV1](timeout)
                response2.userProfile.userData.isActiveUser.get should equal (true)
            }

            "update the user's system admin membership" in {
                actorUnderTest ! UserChangeSystemAdminMembershipStatusRequestV1(
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    changeUserRequest = ChangeUserApiRequestV1(newSystemAdminMembershipStatus = Some(true)),
                    userProfile = SharedAdminTestData.superUser,
                    UUID.randomUUID()
                )

                val response1 = expectMsgType[UserOperationResponseV1](timeout)
                response1.userProfile.permissionData.isSystemAdmin should equal (true)

                actorUnderTest ! UserChangeSystemAdminMembershipStatusRequestV1(
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    changeUserRequest = ChangeUserApiRequestV1(newSystemAdminMembershipStatus = Some(false)),
                    userProfile = SharedAdminTestData.superUser,
                    UUID.randomUUID()
                )

                val response2 = expectMsgType[UserOperationResponseV1](timeout)
                response2.userProfile.permissionData.isSystemAdmin should equal (false)
            }


            "return a 'ForbiddenException' if the user requesting update is not the user itself or system admin" in {

                /* User information is updated by other normal user */
                actorUnderTest ! UserChangeBasicUserDataRequestV1(
                    userIri = SharedAdminTestData.superUser.userData.user_id.get,
                    changeUserRequest = ChangeUserApiRequestV1(
                        email = None,
                        givenName = Some("Donald"),
                        familyName = None,
                        lang = None
                    ),
                    userProfile = SharedAdminTestData.normalUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("User information can only be changed by the user itself or a system administrator")))

                /* Password is updated by other normal user */
                actorUnderTest ! UserChangePasswordRequestV1(
                    userIri = SharedAdminTestData.superUser.userData.user_id.get,
                    changeUserRequest = ChangeUserApiRequestV1(
                        oldPassword = Some("test"),
                        newPassword = Some("test123456")
                    ),
                    userProfile = SharedAdminTestData.normalUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("User's password can only be changed by the user itself")))

                /* Status is updated by other normal user */
                actorUnderTest ! UserChangeStatusRequestV1(
                    userIri = SharedAdminTestData.superUser.userData.user_id.get,
                    changeUserRequest = ChangeUserApiRequestV1(newUserStatus = Some(false)),
                    userProfile = SharedAdminTestData.normalUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("User's status can only be changed by the user itself or a system administrator")))

                /* System admin group membership */
                actorUnderTest ! UserChangeSystemAdminMembershipStatusRequestV1(
                    userIri = SharedAdminTestData.normalUser.userData.user_id.get,
                    changeUserRequest = ChangeUserApiRequestV1(newSystemAdminMembershipStatus = Some(true)),
                    userProfile = SharedAdminTestData.normalUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(ForbiddenException("User's system admin membership can only be changed by a system administrator")))
            }
        }

        "asked to update the user's project membership" should {

            "add user to project" in {

                actorUnderTest ! UserProjectMembershipsGetRequestV1(normalUserIri, rootUser, UUID.randomUUID())
                val membershipsBeforeUpdate = expectMsgType[UserProjectMembershipsGetResponseV1](timeout)
                membershipsBeforeUpdate.projects should equal (Seq())

                actorUnderTest ! UserProjectMembershipAddRequestV1(normalUserIri, imagesProjectIri, rootUser, UUID.randomUUID())
                expectMsgType[UserOperationResponseV1](timeout)

                actorUnderTest ! UserProjectMembershipsGetRequestV1(normalUserIri, rootUser, UUID.randomUUID())
                val membershipsAfterUpdate = expectMsgType[UserProjectMembershipsGetResponseV1](timeout)
                membershipsAfterUpdate.projects should equal (Seq("http://data.knora.org/projects/images"))

                responderManager ! ProjectMembersByIRIGetRequestV1(imagesProjectIri, rootUser)
                val received: ProjectMembersGetResponseV1 = expectMsgType[ProjectMembersGetResponseV1](timeout)

                received.members should contain (normalUser.ofType(UserProfileType.SHORT).userData)
            }

            "remove user from project" in {

                actorUnderTest ! UserProjectMembershipsGetRequestV1(normalUserIri, rootUser, UUID.randomUUID())
                val membershipsBeforeUpdate = expectMsgType[UserProjectMembershipsGetResponseV1](timeout)
                membershipsBeforeUpdate.projects should equal (Seq("http://data.knora.org/projects/images"))

                actorUnderTest ! UserProjectMembershipRemoveRequestV1(normalUserIri, imagesProjectIri, rootUser, UUID.randomUUID())
                expectMsgType[UserOperationResponseV1](timeout)

                actorUnderTest ! UserProjectMembershipsGetRequestV1(normalUserIri, rootUser, UUID.randomUUID())
                val membershipsAfterUpdate = expectMsgType[UserProjectMembershipsGetResponseV1](timeout)
                membershipsAfterUpdate.projects should equal (Seq())

                responderManager ! ProjectMembersByIRIGetRequestV1(imagesProjectIri, rootUser)
                val received: ProjectMembersGetResponseV1 = expectMsgType[ProjectMembersGetResponseV1](timeout)

                received.members should not contain normalUser.ofType(UserProfileType.SHORT).userData
            }

        }

        "asked to update the user's project admin group membership" should {

            "add user to project admin group" in {

                actorUnderTest ! UserProjectAdminMembershipsGetRequestV1(normalUserIri, rootUser, UUID.randomUUID())
                val membershipsBeforeUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseV1](timeout)
                membershipsBeforeUpdate.projects should equal (Seq())

                actorUnderTest ! UserProjectAdminMembershipAddRequestV1(normalUserIri, imagesProjectIri, rootUser, UUID.randomUUID())
                expectMsgType[UserOperationResponseV1](timeout)

                actorUnderTest ! UserProjectAdminMembershipsGetRequestV1(normalUserIri, rootUser, UUID.randomUUID())
                val membershipsAfterUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseV1](timeout)
                membershipsAfterUpdate.projects should equal (Seq("http://data.knora.org/projects/images"))

                responderManager ! ProjectAdminMembersByIRIGetRequestV1(imagesProjectIri, rootUser)
                val received: ProjectAdminMembersGetResponseV1 = expectMsgType[ProjectAdminMembersGetResponseV1](timeout)

                received.members should contain (normalUser.ofType(UserProfileType.SHORT).userData)
            }

            "remove user from project admin group" in {
                actorUnderTest ! UserProjectAdminMembershipsGetRequestV1(normalUserIri, rootUser, UUID.randomUUID())
                val membershipsBeforeUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseV1](timeout)
                membershipsBeforeUpdate.projects should equal (Seq("http://data.knora.org/projects/images"))

                actorUnderTest ! UserProjectAdminMembershipRemoveRequestV1(normalUserIri, imagesProjectIri, rootUser, UUID.randomUUID())
                expectMsgType[UserOperationResponseV1](timeout)

                actorUnderTest ! UserProjectAdminMembershipsGetRequestV1(normalUserIri, rootUser, UUID.randomUUID())
                val membershipsAfterUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseV1](timeout)
                membershipsAfterUpdate.projects should equal (Seq())

                responderManager ! ProjectAdminMembersByIRIGetRequestV1(imagesProjectIri, rootUser)
                val received: ProjectAdminMembersGetResponseV1 = expectMsgType[ProjectAdminMembersGetResponseV1](timeout)

                received.members should not contain normalUser.ofType(UserProfileType.SHORT).userData
            }

        }

        "asked to update the user's group membership" should {

            "add user to group" in {
                fail("test not implemented")
            }

            "remove user from group" in {
                fail("test not implemented")
            }

        }
    }
}
