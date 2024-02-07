/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import org.apache.pekko.actor.Status.Failure
import org.apache.pekko.testkit.ImplicitSender

import java.util.UUID

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import dsp.errors.ForbiddenException
import dsp.valueobjects.LanguageCode
import org.knora.webapi.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupMembersGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.*
import org.knora.webapi.messages.admin.responder.usersmessages.*
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.routing.authenticationmessages.CredentialsIdentifier
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.UserCreateRequest
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

/**
 * This spec is used to test the messages received by the [[UsersResponderADM]] actor.
 */
class UsersResponderADMSpec extends CoreSpec with ImplicitSender {

  private val rootUser          = SharedTestDataADM.rootUser
  private val anythingAdminUser = SharedTestDataADM.anythingAdminUser
  private val normalUser        = SharedTestDataADM.normalUser

  private val incunabulaProjectAdminUser = SharedTestDataADM.incunabulaProjectAdminUser

  private val imagesProject       = SharedTestDataADM.imagesProject
  private val incunabulaProject   = SharedTestDataADM.incunabulaProject
  private val imagesReviewerGroup = SharedTestDataADM.imagesReviewerGroup

  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  "The UsersResponder " when {
    "asked about all users" should {
      "return a list if asked by SystemAdmin" in {
        appActor ! UsersGetRequestADM(
          requestingUser = rootUser
        )
        val response = expectMsgType[UsersGetResponseADM](timeout)
        response.users.nonEmpty should be(true)
        response.users.size should be(18)
      }

      "return a list if asked by ProjectAdmin" in {
        appActor ! UsersGetRequestADM(
          requestingUser = anythingAdminUser
        )
        val response = expectMsgType[UsersGetResponseADM](timeout)
        response.users.nonEmpty should be(true)
        response.users.size should be(18)
      }

      "return 'ForbiddenException' if asked by normal user'" in {
        appActor ! UsersGetRequestADM(
          requestingUser = normalUser
        )
        expectMsg(timeout, Failure(ForbiddenException("ProjectAdmin or SystemAdmin permissions are required.")))
      }

      "not return the system and anonymous users" in {
        appActor ! UsersGetRequestADM(
          requestingUser = rootUser
        )
        val response = expectMsgType[UsersGetResponseADM](timeout)
        response.users.nonEmpty should be(true)
        response.users.size should be(18)
        response.users.count(_.id == KnoraSystemInstances.Users.AnonymousUser.id) should be(0)
        response.users.count(_.id == KnoraSystemInstances.Users.SystemUser.id) should be(0)
      }
    }

    "asked about an user identified by 'iri' " should {
      "return a profile if the user (root user) is known" in {
        val actual = UnsafeZioRun.runOrThrow(
          UsersResponderADM.findUserByIri(
            UserIri.unsafeFrom(rootUser.id),
            UserInformationTypeADM.Full,
            KnoraSystemInstances.Users.SystemUser
          )
        )
        actual shouldBe Some(rootUser.ofType(UserInformationTypeADM.Full))
      }

      "return 'None' when the user is unknown" in {
        val actual = UnsafeZioRun.runOrThrow(
          UsersResponderADM.findUserByIri(
            UserIri.unsafeFrom("http://rdfh.ch/users/notexisting"),
            UserInformationTypeADM.Full,
            KnoraSystemInstances.Users.SystemUser
          )
        )
        actual shouldBe None
      }
    }

    "asked about an user identified by 'email'" should {
      "return a profile if the user (root user) is known" in {
        val actual = UnsafeZioRun.runOrThrow(
          UsersResponderADM.findUserByEmail(
            Email.unsafeFrom(rootUser.email),
            UserInformationTypeADM.Full,
            KnoraSystemInstances.Users.SystemUser
          )
        )
        actual shouldBe Some(rootUser.ofType(UserInformationTypeADM.Full))
      }

      "return 'None' when the user is unknown" in {
        val actual = UnsafeZioRun.runOrThrow(
          UsersResponderADM.findUserByEmail(
            Email.unsafeFrom("userwrong@example.com"),
            UserInformationTypeADM.Full,
            KnoraSystemInstances.Users.SystemUser
          )
        )
        actual shouldBe None
      }
    }

    "asked about an user identified by 'username'" should {
      "return a profile if the user (root user) is known" in {
        val actual = UnsafeZioRun.runOrThrow(
          UsersResponderADM.findUserByUsername(
            Username.unsafeFrom(rootUser.username),
            UserInformationTypeADM.Full,
            KnoraSystemInstances.Users.SystemUser
          )
        )
        actual shouldBe Some(rootUser.ofType(UserInformationTypeADM.Full))
      }

      "return 'None' when the user is unknown" in {
        val actual = UnsafeZioRun.runOrThrow(
          UsersResponderADM.findUserByUsername(
            Username.unsafeFrom("userwrong"),
            UserInformationTypeADM.Full,
            KnoraSystemInstances.Users.SystemUser
          )
        )
        actual shouldBe None
      }
    }

    "asked to create a new user" should {
      "CREATE the user and return it's profile if the supplied email is unique " in {
        val response = UnsafeZioRun.runOrThrow(
          UsersResponderADM.createNewUserADM(
            UserCreateRequest(
              username = Username.unsafeFrom("donald.duck"),
              email = Email.unsafeFrom("donald.duck@example.com"),
              givenName = GivenName.unsafeFrom("Donald"),
              familyName = FamilyName.unsafeFrom("Duck"),
              password = Password.unsafeFrom("test"),
              status = UserStatus.from(true),
              lang = LanguageCode.en,
              systemAdmin = SystemAdmin.from(false)
            ),
            apiRequestID = UUID.randomUUID
          )
        )
        val u = response.user
        u.username shouldBe "donald.duck"
        u.givenName shouldBe "Donald"
        u.familyName shouldBe "Duck"
        u.email shouldBe "donald.duck@example.com"
        u.lang shouldBe "en"

      }

      "return a 'DuplicateValueException' if the supplied 'username' is not unique" in {
        val exit = UnsafeZioRun.run(
          UsersResponderADM.createNewUserADM(
            UserCreateRequest(
              username = Username.unsafeFrom("root"),
              email = Email.unsafeFrom("root2@example.com"),
              givenName = GivenName.unsafeFrom("Donald"),
              familyName = FamilyName.unsafeFrom("Duck"),
              password = Password.unsafeFrom("test"),
              status = UserStatus.from(true),
              lang = LanguageCode.en,
              systemAdmin = SystemAdmin.from(false)
            ),
            UUID.randomUUID
          )
        )
        assertFailsWithA[DuplicateValueException](exit, s"User with the username 'root' already exists")
      }

      "return a 'DuplicateValueException' if the supplied 'email' is not unique" in {
        val exit = UnsafeZioRun.run(
          UsersResponderADM.createNewUserADM(
            UserCreateRequest(
              username = Username.unsafeFrom("root2"),
              email = Email.unsafeFrom("root@example.com"),
              givenName = GivenName.unsafeFrom("Donald"),
              familyName = FamilyName.unsafeFrom("Duck"),
              password = Password.unsafeFrom("test"),
              status = UserStatus.from(true),
              lang = LanguageCode.en,
              systemAdmin = SystemAdmin.from(false)
            ),
            UUID.randomUUID
          )
        )
        assertFailsWithA[DuplicateValueException](exit, s"User with the email 'root@example.com' already exists")
      }
    }

    "asked to update a user" should {
      "UPDATE the user's basic information" in {
        /* User information is updated by the user */
        appActor ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            givenName = Some(GivenName.unsafeFrom("Donald"))
          ),
          requestingUser = SharedTestDataADM.normalUser,
          apiRequestID = UUID.randomUUID
        )

        val response1 = expectMsgType[UserOperationResponseADM](timeout)
        response1.user.givenName should equal("Donald")

        /* User information is updated by a system admin */
        appActor ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            familyName = Some(FamilyName.unsafeFrom("Duck"))
          ),
          requestingUser = SharedTestDataADM.superUser,
          apiRequestID = UUID.randomUUID
        )

        val response2 = expectMsgType[UserOperationResponseADM](timeout)
        response2.user.familyName should equal("Duck")

        /* User information is updated by a system admin */
        appActor ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            givenName = Some(GivenName.unsafeFrom(SharedTestDataADM.normalUser.givenName)),
            familyName = Some(FamilyName.unsafeFrom(SharedTestDataADM.normalUser.familyName))
          ),
          requestingUser = SharedTestDataADM.superUser,
          apiRequestID = UUID.randomUUID
        )

        val response3 = expectMsgType[UserOperationResponseADM](timeout)
        response3.user.givenName should equal(SharedTestDataADM.normalUser.givenName)
        response3.user.familyName should equal(SharedTestDataADM.normalUser.familyName)
      }

      "return a 'DuplicateValueException' if the supplied 'username' is not unique" in {
        val duplicateUsername =
          Some(Username.unsafeFrom(SharedTestDataADM.anythingUser1.username))
        appActor ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            username = duplicateUsername
          ),
          SharedTestDataADM.superUser,
          UUID.randomUUID
        )
        expectMsg(
          Failure(
            DuplicateValueException(
              s"User with the username '${SharedTestDataADM.anythingUser1.username}' already exists"
            )
          )
        )
      }

      "return a 'DuplicateValueException' if the supplied 'email' is not unique" in {
        val duplicateEmail = Some(Email.unsafeFrom(SharedTestDataADM.anythingUser1.email))
        appActor ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            email = duplicateEmail
          ),
          SharedTestDataADM.superUser,
          UUID.randomUUID
        )
        expectMsg(
          Failure(
            DuplicateValueException(s"User with the email '${SharedTestDataADM.anythingUser1.email}' already exists")
          )
        )
      }

      "UPDATE the user's password (by himself)" in {
        val requesterPassword = Password.unsafeFrom("test")
        val newPassword       = Password.unsafeFrom("test123456")
        appActor ! UserChangePasswordRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdatePasswordPayload = UserUpdatePasswordPayloadADM(
            requesterPassword = requesterPassword,
            newPassword = newPassword
          ),
          requestingUser = SharedTestDataADM.normalUser,
          apiRequestID = UUID.randomUUID()
        )

        expectMsgType[UserOperationResponseADM](timeout)

        // need to be able to authenticate credentials with new password
        val cedId       = CredentialsIdentifier.UsernameIdentifier(Username.unsafeFrom(normalUser.username))
        val credentials = KnoraCredentialsV2.KnoraPasswordCredentialsV2(cedId, "test123456")
        val resF        = UnsafeZioRun.runToFuture(Authenticator.authenticateCredentialsV2(Some(credentials)))

        resF map { res => assert(res) }
      }

      "UPDATE the user's password (by a system admin)" in {
        val requesterPassword = Password.unsafeFrom("test")
        val newPassword       = Password.unsafeFrom("test654321")

        appActor ! UserChangePasswordRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdatePasswordPayload = UserUpdatePasswordPayloadADM(
            requesterPassword = requesterPassword,
            newPassword = newPassword
          ),
          requestingUser = SharedTestDataADM.rootUser,
          apiRequestID = UUID.randomUUID()
        )

        expectMsgType[UserOperationResponseADM](timeout)

        // need to be able to authenticate credentials with new password
        val cedId       = CredentialsIdentifier.UsernameIdentifier(Username.unsafeFrom(normalUser.username))
        val credentials = KnoraCredentialsV2.KnoraPasswordCredentialsV2(cedId, "test654321")
        val resF        = UnsafeZioRun.runToFuture(Authenticator.authenticateCredentialsV2(Some(credentials)))

        resF map { res => assert(res) }
      }

      "UPDATE the user's status, (deleting) making him inactive " in {
        appActor ! UserChangeStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          status = UserStatus.from(false),
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response1 = expectMsgType[UserOperationResponseADM](timeout)
        response1.user.status should equal(false)

        appActor ! UserChangeStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          status = UserStatus.from(true),
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response2 = expectMsgType[UserOperationResponseADM](timeout)
        response2.user.status should equal(true)
      }

      "UPDATE the user's system admin membership" in {
        appActor ! UserChangeSystemAdminMembershipStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          systemAdmin = SystemAdmin.from(true),
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response1 = expectMsgType[UserOperationResponseADM](timeout)
        response1.user.isSystemAdmin should equal(true)

        appActor ! UserChangeSystemAdminMembershipStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          systemAdmin = SystemAdmin.from(false),
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response2 = expectMsgType[UserOperationResponseADM](timeout)
        response2.user.permissions.isSystemAdmin should equal(false)
      }

      "return a 'ForbiddenException' if the user requesting update is not the user itself or system admin" in {
        /* User information is updated by other normal user */
        appActor ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.superUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            email = None,
            givenName = Some(GivenName.unsafeFrom("Donald")),
            familyName = None,
            lang = None
          ),
          requestingUser = SharedTestDataADM.normalUser,
          UUID.randomUUID
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User information can only be changed by the user itself or a system administrator")
          )
        )

        /* Password is updated by other normal user */
        appActor ! UserChangePasswordRequestADM(
          userIri = SharedTestDataADM.superUser.id,
          userUpdatePasswordPayload = UserUpdatePasswordPayloadADM(
            requesterPassword = Password.unsafeFrom("test"),
            newPassword = Password.unsafeFrom("test123456")
          ),
          requestingUser = SharedTestDataADM.normalUser,
          UUID.randomUUID
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's password can only be changed by the user itself or a system administrator")
          )
        )

        /* Status is updated by other normal user */
        appActor ! UserChangeStatusRequestADM(
          userIri = SharedTestDataADM.superUser.id,
          status = UserStatus.from(false),
          requestingUser = SharedTestDataADM.normalUser,
          UUID.randomUUID
        )
        expectMsg(
          timeout,
          Failure(ForbiddenException("User's status can only be changed by the user itself or a system administrator"))
        )

        /* System admin group membership */
        appActor ! UserChangeSystemAdminMembershipStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          systemAdmin = SystemAdmin.from(true),
          requestingUser = SharedTestDataADM.normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(ForbiddenException("User's system admin membership can only be changed by a system administrator"))
        )
      }

      "return 'BadRequest' if system user is requested to change" in {
        appActor ! UserChangeStatusRequestADM(
          userIri = KnoraSystemInstances.Users.SystemUser.id,
          status = UserStatus.from(false),
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        expectMsg(timeout, Failure(BadRequestException("Changes to built-in users are not allowed.")))
      }

      "return 'BadRequest' if anonymous user is requested to change" in {
        appActor ! UserChangeStatusRequestADM(
          userIri = KnoraSystemInstances.Users.AnonymousUser.id,
          status = UserStatus.from(false),
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        expectMsg(timeout, Failure(BadRequestException("Changes to built-in users are not allowed.")))
      }
    }

    "asked to update the user's project membership" should {
      "ADD user to project" in {

        // get current project memberships
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findProjectMemberShipsByIri(normalUser.userIri))
        membershipsBeforeUpdate.projects should equal(Seq())

        // add user to images project (00FF)
        appActor ! UserProjectMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          rootUser,
          UUID.randomUUID()
        )

        // wait for the response before checking the project membership
        expectMsgType[UserOperationResponseADM](timeout)

        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findProjectMemberShipsByIri(normalUser.userIri))
        membershipsAfterUpdate.projects should equal(Seq(imagesProject))

        val received = UnsafeZioRun.runOrThrow(
          ProjectsResponderADM.projectMembersGetRequestADM(
            IriIdentifier.unsafeFrom(imagesProject.id),
            KnoraSystemInstances.Users.SystemUser
          )
        )
        received.members.map(_.id) should contain(normalUser.id)
      }

      "not ADD user to project as project admin of another project" in {
        // get current project memberships
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findProjectMemberShipsByIri(normalUser.userIri))
        membershipsBeforeUpdate.projects.map(_.id).sorted should equal(Seq(imagesProject.id).sorted)

        // try to add user to incunabula project but as project admin of another project
        appActor ! UserProjectMembershipAddRequestADM(
          normalUser.id,
          incunabulaProject.id,
          anythingAdminUser,
          UUID.randomUUID()
        )

        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's project membership can only be changed by a project or system administrator")
          )
        )

        // check that the user is still only member of one project
        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findProjectMemberShipsByIri(normalUser.userIri))
        membershipsAfterUpdate.projects.map(_.id).sorted should equal(Seq(imagesProject.id).sorted)

        // check that the user was not added to the project
        val received = UnsafeZioRun.runOrThrow(
          ProjectsResponderADM.projectMembersGetRequestADM(
            IriIdentifier.unsafeFrom(incunabulaProject.id),
            KnoraSystemInstances.Users.SystemUser
          )
        )
        received.members.map(_.id) should not contain normalUser.id
      }

      "ADD user to project as project admin" in {
        // get current project memberships
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findProjectMemberShipsByIri(normalUser.userIri))
        membershipsBeforeUpdate.projects.map(_.id).sorted should equal(Seq(imagesProject.id).sorted)

        // add user to images project (00FF)
        appActor ! UserProjectMembershipAddRequestADM(
          normalUser.id,
          incunabulaProject.id,
          incunabulaProjectAdminUser,
          UUID.randomUUID()
        )

        // wait for the response before checking the project membership
        expectMsgType[UserOperationResponseADM](timeout)

        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findProjectMemberShipsByIri(normalUser.userIri))
        membershipsAfterUpdate.projects.map(_.id).sorted should equal(
          Seq(imagesProject.id, incunabulaProject.id).sorted
        )

        val received = UnsafeZioRun.runOrThrow(
          ProjectsResponderADM.projectMembersGetRequestADM(
            IriIdentifier.unsafeFrom(incunabulaProject.id),
            KnoraSystemInstances.Users.SystemUser
          )
        )
        received.members.map(_.id) should contain(normalUser.id)
      }

      "DELETE user from project and also as project admin" in {
        // check project memberships (user should be member of images and incunabula projects)
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findProjectMemberShipsByIri(normalUser.userIri))
        membershipsBeforeUpdate.projects.map(_.id).sorted should equal(
          Seq(imagesProject.id, incunabulaProject.id).sorted
        )

        // add user as project admin to images project
        appActor ! UserProjectAdminMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          rootUser,
          UUID.randomUUID()
        )

        expectMsgType[UserOperationResponseADM](timeout)

        // verify that the user has been added as project admin to the images project
        val projectAdminMembershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findUserProjectAdminMemberships(normalUser.userIri))
        projectAdminMembershipsBeforeUpdate.projects.map(_.id).sorted should equal(Seq(imagesProject.id).sorted)

        // remove the user as member of the images project
        appActor ! UserProjectMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        // verify that the user has been removed as project member of the images project
        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findProjectMemberShipsByIri(normalUser.userIri))
        membershipsAfterUpdate.projects should equal(Seq(incunabulaProject))

        // this should also have removed him as project admin from images project
        val projectAdminMembershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findUserProjectAdminMemberships(normalUser.userIri))
        projectAdminMembershipsAfterUpdate.projects should equal(Seq())

        // also check that the user has been removed from the project's list of users
        val received = UnsafeZioRun.runOrThrow(
          ProjectsResponderADM.projectMembersGetRequestADM(IriIdentifier.unsafeFrom(imagesProject.id), rootUser)
        )
        received.members should not contain normalUser.ofType(UserInformationTypeADM.Restricted)
      }

      "return a 'ForbiddenException' if the user requesting update is not the project or system admin" in {
        /* User is added to a project by a normal user */
        appActor ! UserProjectMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's project membership can only be changed by a project or system administrator")
          )
        )

        /* User is removed from a project by a normal user */
        appActor ! UserProjectMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's project membership can only be changed by a project or system administrator")
          )
        )
      }

    }

    "asked to update the user's project admin group membership" should {
      "Not ADD user to project admin group if he is not a member of that project" in {
        // get the current project admin memberships (should be empty)
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findUserProjectAdminMemberships(normalUser.userIri))
        membershipsBeforeUpdate.projects should equal(Seq())

        // try to add user as project admin to images project (expected to fail because he is not a member of the project)
        appActor ! UserProjectAdminMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          rootUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            BadRequestException(
              "User http://rdfh.ch/users/normaluser is not a member of project http://rdfh.ch/projects/00FF. A user needs to be a member of the project to be added as project admin."
            )
          )
        )
      }

      "ADD user to project admin group" in {
        // get the current project admin memberships (should be empty)
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findUserProjectAdminMemberships(normalUser.userIri))
        membershipsBeforeUpdate.projects should equal(Seq())

        // add user as project member to images project
        appActor ! UserProjectMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        // add user as project admin to images project
        appActor ! UserProjectAdminMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          rootUser,
          UUID.randomUUID()
        )

        expectMsgType[UserOperationResponseADM](timeout)

        // get the updated project admin memberships (should contain images project)
        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findUserProjectAdminMemberships(normalUser.userIri))
        membershipsAfterUpdate.projects should equal(Seq(imagesProject))

        // get project admins for images project (should contain normal user)
        val received = UnsafeZioRun.runOrThrow(
          ProjectsResponderADM.projectAdminMembersGetRequestADM(IriIdentifier.unsafeFrom(imagesProject.id), rootUser)
        )
        received.members.map(_.id) should contain(normalUser.id)
      }

      "DELETE user from project admin group" in {
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findUserProjectAdminMemberships(normalUser.userIri))
        membershipsBeforeUpdate.projects should equal(Seq(imagesProject))

        appActor ! UserProjectAdminMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findUserProjectAdminMemberships(normalUser.userIri))
        membershipsAfterUpdate.projects should equal(Seq())

        val received = UnsafeZioRun.runOrThrow(
          ProjectsResponderADM.projectAdminMembersGetRequestADM(IriIdentifier.unsafeFrom(imagesProject.id), rootUser)
        )
        received.members should not contain normalUser.ofType(UserInformationTypeADM.Restricted)
      }

      "return a 'ForbiddenException' if the user requesting update is not the project or system admin" in {
        /* User is added to a project by a normal user */
        appActor ! UserProjectAdminMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException(
              "User's project admin membership can only be changed by a project or system administrator"
            )
          )
        )

        /* User is removed from a project by a normal user */
        appActor ! UserProjectAdminMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException(
              "User's project admin membership can only be changed by a project or system administrator"
            )
          )
        )
      }

    }

    "asked to update the user's group membership" should {
      "ADD user to group" in {
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findGroupMembershipsByIri(normalUser.userIri))
        membershipsBeforeUpdate should equal(Seq())

        appActor ! UserGroupMembershipAddRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findGroupMembershipsByIri(normalUser.userIri))
        membershipsAfterUpdate.map(_.id) should equal(Seq(imagesReviewerGroup.id))

        appActor ! GroupMembersGetRequestADM(
          groupIri = imagesReviewerGroup.id,
          requestingUser = rootUser
        )
        val received: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)

        received.members.map(_.id) should contain(normalUser.id)
      }

      "DELETE user from group" in {
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findGroupMembershipsByIri(normalUser.userIri))
        membershipsBeforeUpdate.map(_.id) should equal(Seq(imagesReviewerGroup.id))

        appActor ! UserGroupMembershipRemoveRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponderADM.findGroupMembershipsByIri(normalUser.userIri))
        membershipsAfterUpdate should equal(Seq())

        appActor ! GroupMembersGetRequestADM(
          groupIri = imagesReviewerGroup.id,
          requestingUser = rootUser
        )
        val received: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)

        received.members.map(_.id) should not contain normalUser.id
      }

      "return a 'ForbiddenException' if the user requesting update is not the project or system admin" in {
        /* User is added to a project by a normal user */
        appActor ! UserGroupMembershipAddRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's group membership can only be changed by a project or system administrator")
          )
        )

        /* User is removed from a project by a normal user */
        appActor ! UserGroupMembershipRemoveRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's group membership can only be changed by a project or system administrator")
          )
        )
      }
    }
  }
}
