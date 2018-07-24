/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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
  * extend ResponderADM which messes up testing, as we cannot inject the TestActor system.
  */
package org.knora.webapi.responders.admin

import java.util.UUID

import akka.actor.Status.Failure
import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.groupsmessages.{GroupMembersGetRequestADM, GroupMembersGetResponseADM}
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectAdminMembersGetRequestADM, ProjectAdminMembersGetResponseADM, ProjectMembersGetRequestADM, ProjectMembersGetResponseADM}
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraPasswordCredentialsV2
import org.knora.webapi.routing.Authenticator

import scala.concurrent.duration._


object UsersResponderADMSpec {

    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test the messages received by the [[UsersResponderADM]] actor.
  */
class UsersResponderADMSpec extends CoreSpec(UsersResponderADMSpec.config) with ImplicitSender with Authenticator {

    private implicit val executionContext = system.dispatcher

    private val timeout = 5.seconds

    private val rootUser = SharedTestDataADM.rootUser

    private val normalUser = SharedTestDataADM.normalUser

    private val incunabulaUser = SharedTestDataADM.incunabulaProjectAdminUser

    private val imagesProject = SharedTestDataADM.imagesProject
    private val imagesReviewerGroup = SharedTestDataADM.imagesReviewerGroup

    private val actorUnderTest = TestActorRef[UsersResponderADM]

    "The UsersResponder " when {

        "asked about all users" should {
            "return a list" in {
                actorUnderTest ! UsersGetRequestADM(requestingUser = rootUser)
                val response = expectMsgType[UsersGetResponseADM](timeout)
                response.users.nonEmpty should be (true)
                response.users.size should be (21)
            }
        }

        "asked about an user identified by 'iri' " should {

            "return a profile if the user (root user) is known" in {
                actorUnderTest ! UserGetADM(
                    maybeIri = Some(rootUser.id),
                    maybeEmail = None,
                    userInformationTypeADM = UserInformationTypeADM.FULL,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(Some(rootUser.ofType(UserInformationTypeADM.FULL)))
            }

            "return a profile if the user (incunabula user) is known" in {
                actorUnderTest ! UserGetADM(
                    maybeIri = Some(incunabulaUser.id),
                    maybeEmail = None,
                    userInformationTypeADM = UserInformationTypeADM.FULL,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(Some(incunabulaUser.ofType(UserInformationTypeADM.FULL)))
            }

            "return 'NotFoundException' when the user is unknown " in {
                actorUnderTest ! UserGetRequestADM(
                    maybeIri = Some("http://rdfh.ch/users/notexisting"),
                    maybeEmail = None,
                    userInformationTypeADM = UserInformationTypeADM.FULL,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(Failure(NotFoundException(s"User 'http://rdfh.ch/users/notexisting' not found")))
            }

            "return 'None' when the user is unknown " in {
                actorUnderTest ! UserGetADM(
                    maybeIri = Some("http://rdfh.ch/users/notexisting"),
                    maybeEmail = None,
                    userInformationTypeADM = UserInformationTypeADM.FULL,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(None)
            }
        }

        "asked about an user identified by 'email'" should {

            "return a profile if the user (root user) is known" in {
                actorUnderTest ! UserGetADM(
                    maybeIri = None,
                    maybeEmail = Some(rootUser.email),
                    userInformationTypeADM = UserInformationTypeADM.FULL,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(Some(rootUser.ofType(UserInformationTypeADM.FULL)))
            }

            "return a profile if the user (incunabula user) is known" in {
                actorUnderTest ! UserGetADM(
                    maybeIri = None,
                    maybeEmail = Some(incunabulaUser.email),
                    userInformationTypeADM = UserInformationTypeADM.FULL,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(Some(incunabulaUser.ofType(UserInformationTypeADM.FULL)))
            }

            "return 'NotFoundException' when the user is unknown" in {
                actorUnderTest ! UserGetRequestADM(
                    maybeIri = None,
                    maybeEmail = Some("userwrong@example.com"),
                    userInformationTypeADM = UserInformationTypeADM.FULL,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(Failure(NotFoundException(s"User 'userwrong@example.com' not found")))
            }

            "return 'None' when the user is unknown" in {
                actorUnderTest ! UserGetADM(
                    maybeIri = None,
                    maybeEmail = Some("userwrong@example.com"),
                    userInformationTypeADM = UserInformationTypeADM.FULL,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(None)
            }
        }

        "asked to create a new user" should {

            "CREATE the user and return it's profile if the supplied email is unique " in {
                actorUnderTest ! UserCreateRequestADM(
                    createRequest = CreateUserApiRequestADM(
                        email = "donald.duck@example.com",
                        givenName = "Donald",
                        familyName = "Duck",
                        password = "test",
                        status = true,
                        lang = "en",
                        systemAdmin = false
                    ),
                    requestingUser = SharedTestDataADM.anonymousUser,
                    apiRequestID = UUID.randomUUID
                )
                expectMsgPF(timeout) {
                    case UserOperationResponseADM(newUser) => {
                        assert(newUser.givenName.equals("Donald"))
                        assert(newUser.familyName.equals("Duck"))
                        assert(newUser.email.equals("donald.duck@example.com"))
                        assert(newUser.lang.equals("en"))
                    }
                }
            }

            "return a 'DuplicateValueException' if the supplied 'email' is not unique" in {
                actorUnderTest ! UserCreateRequestADM(
                    createRequest = CreateUserApiRequestADM(
                        email = "root@example.com",
                        givenName = "Donal",
                        familyName = "Duck",
                        password = "test",
                        status = true,
                        lang = "en",
                        systemAdmin = false
                    ),
                    SharedTestDataADM.anonymousUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(DuplicateValueException(s"User with the email: 'root@example.com' already exists")))
            }

            "return 'BadRequestException' if 'email' or 'password' or 'givenName' or 'familyName' are missing" in {

                /* missing email */
                actorUnderTest ! UserCreateRequestADM(
                    createRequest = CreateUserApiRequestADM(
                        email = "",
                        givenName = "Donald",
                        familyName = "Duck",
                        password = "test",
                        status = true,
                        lang = "en",
                        systemAdmin = false
                    ),
                    SharedTestDataADM.anonymousUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Email cannot be empty")))

                /* missing password */
                actorUnderTest ! UserCreateRequestADM(
                    createRequest = CreateUserApiRequestADM(
                        email = "donald.duck@example.com",
                        givenName = "Donald",
                        familyName = "Duck",
                        password = "",
                        status = true,
                        lang = "en",
                        systemAdmin = false
                    ),
                    SharedTestDataADM.anonymousUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Password cannot be empty")))

                /* missing givenName */
                actorUnderTest ! UserCreateRequestADM(
                    createRequest = CreateUserApiRequestADM(
                        email = "donald.duck@example.com",
                        givenName = "",
                        familyName = "Duck",
                        password = "test",
                        status = true,
                        lang = "en",
                        systemAdmin = false
                    ),
                    SharedTestDataADM.anonymousUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Given name cannot be empty")))

                /* missing familyName */
                actorUnderTest ! UserCreateRequestADM(
                    createRequest = CreateUserApiRequestADM(
                        email = "donald.duck@example.com",
                        givenName = "Donald",
                        familyName = "",
                        password = "test",
                        status = true,
                        lang = "en",
                        systemAdmin = false
                    ),
                    SharedTestDataADM.anonymousUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException("Family name cannot be empty")))
            }
        }

        "asked to update a user" should {

            "UPDATE the user's basic information" in {

                /* User information is updated by the user */
                actorUnderTest ! UserChangeBasicUserInformationRequestADM(
                    userIri = SharedTestDataADM.normalUser.id,
                    changeUserRequest = ChangeUserApiRequestADM(
                        email = None,
                        givenName = Some("Donald"),
                        familyName = None,
                        lang = None
                    ),
                    requestingUser = SharedTestDataADM.normalUser,
                    UUID.randomUUID
                )

                val response1 = expectMsgType[UserOperationResponseADM](timeout)
                response1.user.givenName should equal ("Donald")

                /* User information is updated by a system admin */
                actorUnderTest ! UserChangeBasicUserInformationRequestADM(
                    userIri = SharedTestDataADM.normalUser.id,
                    changeUserRequest = ChangeUserApiRequestADM(
                        email = None,
                        givenName = None,
                        familyName = Some("Duck"),
                        lang = None
                    ),
                    requestingUser = SharedTestDataADM.superUser,
                    UUID.randomUUID
                )

                val response2 = expectMsgType[UserOperationResponseADM](timeout)
                response2.user.familyName should equal ("Duck")

                /* User information is updated by a system admin */
                actorUnderTest ! UserChangeBasicUserInformationRequestADM(
                    userIri = SharedTestDataADM.normalUser.id,
                    changeUserRequest = ChangeUserApiRequestADM(
                        email = None,
                        givenName = Some(SharedTestDataADM.normalUser.givenName),
                        familyName = Some(SharedTestDataADM.normalUser.familyName),
                        lang = None
                    ),
                    requestingUser = SharedTestDataADM.superUser,
                    UUID.randomUUID
                )

                val response3 = expectMsgType[UserOperationResponseADM](timeout)
                response3.user.givenName should equal (SharedTestDataADM.normalUser.givenName)
                response3.user.familyName should equal (SharedTestDataADM.normalUser.familyName)

            }

            "UPDATE the user's password (by himself)" in {
                actorUnderTest ! UserChangePasswordRequestADM(
                    userIri = SharedTestDataADM.normalUser.id,
                    changeUserRequest = ChangeUserApiRequestADM(
                        requesterPassword = Some("test"), // of the requesting user
                        newPassword = Some("test123456")
                    ),
                    requestingUser = SharedTestDataADM.normalUser,
                    apiRequestID = UUID.randomUUID()
                )

                expectMsgType[UserOperationResponseADM](timeout)

                // need to be able to authenticate credentials with new password
                val resF = Authenticator.authenticateCredentialsV2(Some(KnoraPasswordCredentialsV2(normalUser.email, "test123456")))

                resF map { res => assert(res) }
            }

            "UPDATE the user's password (by a system admin)" in {
                actorUnderTest ! UserChangePasswordRequestADM(
                    userIri = SharedTestDataADM.normalUser.id,
                    changeUserRequest = ChangeUserApiRequestADM(
                        requesterPassword = Some("test"), // of the requesting user
                        newPassword = Some("test654321")
                    ),
                    requestingUser = SharedTestDataADM.rootUser,
                    apiRequestID = UUID.randomUUID()
                )

                expectMsgType[UserOperationResponseADM](timeout)

                // need to be able to authenticate credentials with new password
                val resF = Authenticator.authenticateCredentialsV2(Some(KnoraPasswordCredentialsV2(normalUser.email, "test654321")))

                resF map { res => assert(res) }
            }

            "UPDATE the user's status, (deleting) making him inactive " in {
                actorUnderTest ! UserChangeStatusRequestADM(
                    userIri = SharedTestDataADM.normalUser.id,
                    changeUserRequest = ChangeUserApiRequestADM(status = Some(false)),
                    requestingUser = SharedTestDataADM.superUser,
                    UUID.randomUUID()
                )

                val response1 = expectMsgType[UserOperationResponseADM](timeout)
                response1.user.status should equal (false)

                actorUnderTest ! UserChangeStatusRequestADM(
                    userIri = SharedTestDataADM.normalUser.id,
                    changeUserRequest = ChangeUserApiRequestADM(status = Some(true)),
                    requestingUser = SharedTestDataADM.superUser,
                    UUID.randomUUID()
                )

                val response2 = expectMsgType[UserOperationResponseADM](timeout)
                response2.user.status should equal (true)
            }

            "UPDATE the user's system admin membership" in {
                actorUnderTest ! UserChangeSystemAdminMembershipStatusRequestADM(
                    userIri = SharedTestDataADM.normalUser.id,
                    changeUserRequest = ChangeUserApiRequestADM(systemAdmin = Some(true)),
                    requestingUser = SharedTestDataADM.superUser,
                    UUID.randomUUID()
                )

                val response1 = expectMsgType[UserOperationResponseADM](timeout)
                response1.user.isSystemAdmin should equal (true)

                actorUnderTest ! UserChangeSystemAdminMembershipStatusRequestADM(
                    userIri = SharedTestDataADM.normalUser.id,
                    changeUserRequest = ChangeUserApiRequestADM(systemAdmin = Some(false)),
                    requestingUser = SharedTestDataADM.superUser,
                    UUID.randomUUID()
                )

                val response2 = expectMsgType[UserOperationResponseADM](timeout)
                response2.user.permissions.isSystemAdmin should equal (false)
            }


            "return a 'ForbiddenException' if the user requesting update is not the user itself or system admin" in {

                /* User information is updated by other normal user */
                actorUnderTest ! UserChangeBasicUserInformationRequestADM(
                    userIri = SharedTestDataADM.superUser.id,
                    changeUserRequest = ChangeUserApiRequestADM(
                        email = None,
                        givenName = Some("Donald"),
                        familyName = None,
                        lang = None
                    ),
                    requestingUser = SharedTestDataADM.normalUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("User information can only be changed by the user itself or a system administrator")))

                /* Password is updated by other normal user */
                actorUnderTest ! UserChangePasswordRequestADM(
                    userIri = SharedTestDataADM.superUser.id,
                    changeUserRequest = ChangeUserApiRequestADM(
                        requesterPassword = Some("test"),
                        newPassword = Some("test123456")
                    ),
                    requestingUser = SharedTestDataADM.normalUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("User's password can only be changed by the user itself or a system admin.")))

                /* Status is updated by other normal user */
                actorUnderTest ! UserChangeStatusRequestADM(
                    userIri = SharedTestDataADM.superUser.id,
                    changeUserRequest = ChangeUserApiRequestADM(status = Some(false)),
                    requestingUser = SharedTestDataADM.normalUser,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("User's status can only be changed by the user itself or a system administrator")))

                /* System admin group membership */
                actorUnderTest ! UserChangeSystemAdminMembershipStatusRequestADM(
                    userIri = SharedTestDataADM.normalUser.id,
                    changeUserRequest = ChangeUserApiRequestADM(systemAdmin = Some(true)),
                    requestingUser = SharedTestDataADM.normalUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(ForbiddenException("User's system admin membership can only be changed by a system administrator")))
            }

            "return 'BadRequest' if nothing would be changed during the update" in {

                an [BadRequestException] should be thrownBy ChangeUserApiRequestADM(None, None, None, None, None, None, None, None)
            }
        }

        "asked to update the user's project membership" should {

            "ADD user to project" in {

                actorUnderTest ! UserProjectMembershipsGetRequestADM(normalUser.id, rootUser, UUID.randomUUID())
                val membershipsBeforeUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
                membershipsBeforeUpdate.projects should equal (Seq())

                actorUnderTest ! UserProjectMembershipAddRequestADM(normalUser.id, imagesProject.id, rootUser, UUID.randomUUID())
                val membershipUpdateResponse = expectMsgType[UserOperationResponseADM](timeout)

                actorUnderTest ! UserProjectMembershipsGetRequestADM(normalUser.id, rootUser, UUID.randomUUID())
                val membershipsAfterUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
                membershipsAfterUpdate.projects should equal (Seq(imagesProject))

                responderManager ! ProjectMembersGetRequestADM(
                    maybeIri = Some(imagesProject.id),
                    maybeShortname = None,
                    maybeShortcode = None,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)

                received.members.map(_.id) should contain (normalUser.id)
            }

            "DELETE user from project" in {

                actorUnderTest ! UserProjectMembershipsGetRequestADM(normalUser.id, rootUser, UUID.randomUUID())
                val membershipsBeforeUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
                membershipsBeforeUpdate.projects should equal (Seq(imagesProject))

                actorUnderTest ! UserProjectMembershipRemoveRequestADM(normalUser.id, imagesProject.id, rootUser, UUID.randomUUID())
                expectMsgType[UserOperationResponseADM](timeout)

                actorUnderTest ! UserProjectMembershipsGetRequestADM(normalUser.id, rootUser, UUID.randomUUID())
                val membershipsAfterUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
                membershipsAfterUpdate.projects should equal (Seq())

                responderManager ! ProjectMembersGetRequestADM(
                    maybeIri = Some(imagesProject.id),
                    maybeShortname = None,
                    maybeShortcode = None,
                    requestingUser = rootUser
                )
                val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)

                received.members should not contain normalUser.ofType(UserInformationTypeADM.RESTRICTED)
            }

            "return a 'ForbiddenException' if the user requesting update is not the project or system admin" in {

                /* User is added to a project by a normal user */
                actorUnderTest ! UserProjectMembershipAddRequestADM(normalUser.id, imagesProject.id, normalUser, UUID.randomUUID())
                expectMsg(Failure(ForbiddenException("User's project membership can only be changed by a project or system administrator")))

                /* User is removed from a project by a normal user */
                actorUnderTest ! UserProjectMembershipRemoveRequestADM(normalUser.id, imagesProject.id, normalUser, UUID.randomUUID())
                expectMsg(Failure(ForbiddenException("User's project membership can only be changed by a project or system administrator")))
            }

        }

        "asked to update the user's project admin group membership" should {

            "ADD user to project admin group" in {

                actorUnderTest ! UserProjectAdminMembershipsGetRequestADM(normalUser.id, rootUser, UUID.randomUUID())
                val membershipsBeforeUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
                membershipsBeforeUpdate.projects should equal (Seq())

                actorUnderTest ! UserProjectAdminMembershipAddRequestADM(normalUser.id, imagesProject.id, rootUser, UUID.randomUUID())
                expectMsgType[UserOperationResponseADM](timeout)

                actorUnderTest ! UserProjectAdminMembershipsGetRequestADM(normalUser.id, rootUser, UUID.randomUUID())
                val membershipsAfterUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
                membershipsAfterUpdate.projects should equal (Seq(imagesProject))

                responderManager ! ProjectAdminMembersGetRequestADM(
                    maybeIri = Some(imagesProject.id),
                    maybeShortname = None,
                    maybeShortcode = None,
                    requestingUser = rootUser
                )
                val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)

                received.members should contain (normalUser.ofType(UserInformationTypeADM.RESTRICTED))
            }

            "DELETE user from project admin group" in {
                actorUnderTest ! UserProjectAdminMembershipsGetRequestADM(normalUser.id, rootUser, UUID.randomUUID())
                val membershipsBeforeUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
                membershipsBeforeUpdate.projects should equal (Seq(imagesProject))

                actorUnderTest ! UserProjectAdminMembershipRemoveRequestADM(normalUser.id, imagesProject.id, rootUser, UUID.randomUUID())
                expectMsgType[UserOperationResponseADM](timeout)

                actorUnderTest ! UserProjectAdminMembershipsGetRequestADM(normalUser.id, rootUser, UUID.randomUUID())
                val membershipsAfterUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
                membershipsAfterUpdate.projects should equal (Seq())

                responderManager ! ProjectAdminMembersGetRequestADM(
                    maybeIri = Some(imagesProject.id),
                    maybeShortname = None,
                    maybeShortcode = None,
                    requestingUser = rootUser
                )
                val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)

                received.members should not contain normalUser.ofType(UserInformationTypeADM.RESTRICTED)
            }

            "return a 'ForbiddenException' if the user requesting update is not the project or system admin" in {

                /* User is added to a project by a normal user */
                actorUnderTest ! UserProjectAdminMembershipAddRequestADM(normalUser.id, imagesProject.id, normalUser, UUID.randomUUID())
                expectMsg(Failure(ForbiddenException("User's project admin membership can only be changed by a project or system administrator")))

                /* User is removed from a project by a normal user */
                actorUnderTest ! UserProjectAdminMembershipRemoveRequestADM(normalUser.id, imagesProject.id, normalUser, UUID.randomUUID())
                expectMsg(Failure(ForbiddenException("User's project admin membership can only be changed by a project or system administrator")))
            }

        }

        "asked to update the user's group membership" should {

            "ADD user to group" in {
                actorUnderTest ! UserGroupMembershipsGetRequestADM(normalUser.id, rootUser, UUID.randomUUID())
                val membershipsBeforeUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
                membershipsBeforeUpdate.groups should equal (Seq())

                actorUnderTest ! UserGroupMembershipAddRequestADM(normalUser.id, imagesReviewerGroup.id, rootUser, UUID.randomUUID())
                expectMsgType[UserOperationResponseADM](timeout)

                actorUnderTest ! UserGroupMembershipsGetRequestADM(normalUser.id, rootUser, UUID.randomUUID())
                val membershipsAfterUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
                membershipsAfterUpdate.groups.map(_.id) should equal (Seq(imagesReviewerGroup.id))

                responderManager ! GroupMembersGetRequestADM(
                    groupIri = imagesReviewerGroup.id,
                    requestingUser = rootUser
                )
                val received: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)

                received.members.map(_.id) should contain (normalUser.id)
            }

            "DELETE user from group" in {
                actorUnderTest ! UserGroupMembershipsGetRequestADM(normalUser.id, rootUser, UUID.randomUUID())
                val membershipsBeforeUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
                membershipsBeforeUpdate.groups.map(_.id) should equal (Seq(imagesReviewerGroup.id))

                actorUnderTest ! UserGroupMembershipRemoveRequestADM(normalUser.id, imagesReviewerGroup.id, rootUser, UUID.randomUUID())
                expectMsgType[UserOperationResponseADM](timeout)

                actorUnderTest ! UserGroupMembershipsGetRequestADM(normalUser.id, rootUser, UUID.randomUUID())
                val membershipsAfterUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
                membershipsAfterUpdate.groups should equal (Seq())

                responderManager ! GroupMembersGetRequestADM(
                    groupIri = imagesReviewerGroup.id,
                    requestingUser = rootUser
                )
                val received: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)

                received.members.map(_.id) should not contain normalUser.id
            }

            "return a 'ForbiddenException' if the user requesting update is not the project or system admin" in {

                /* User is added to a project by a normal user */
                actorUnderTest ! UserGroupMembershipAddRequestADM(normalUser.id, imagesReviewerGroup.id, normalUser, UUID.randomUUID())
                expectMsg(Failure(ForbiddenException("User's group membership can only be changed by a project or system administrator")))

                /* User is removed from a project by a normal user */
                actorUnderTest ! UserGroupMembershipRemoveRequestADM(normalUser.id, imagesReviewerGroup.id, normalUser, UUID.randomUUID())
                expectMsg(Failure(ForbiddenException("User's group membership can only be changed by a project or system administrator")))
            }

        }
    }
}
