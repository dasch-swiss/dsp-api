/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import org.apache.pekko.testkit.ImplicitSender
import zio.ZIO

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
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.BasicUserInformationChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.PasswordChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.UserCreateRequest
import org.knora.webapi.slice.admin.api.service.UsersRestService
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

/**
 * This spec is used to test the messages received by the [[UsersResponder]] actor.
 */
class UsersResponderSpec extends CoreSpec with ImplicitSender {

  private val rootUser   = SharedTestDataADM.rootUser
  private val normalUser = SharedTestDataADM.normalUser

  private val imagesProject       = SharedTestDataADM.imagesProject
  private val incunabulaProject   = SharedTestDataADM.incunabulaProject
  private val imagesReviewerGroup = SharedTestDataADM.imagesReviewerGroup

  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  "The UsersRestService" when {
    "calling getAllUsers" should {

      def getAllUsers(requestingUser: User): ZIO[UsersRestService, Throwable, UsersGetResponseADM] =
        ZIO.serviceWithZIO[UsersRestService](_.getAllUsers(requestingUser))

      "with a SystemAdmin should return all real users" in {
        val response = UnsafeZioRun.runOrThrow(getAllUsers(rootUser))
        response.users.nonEmpty should be(true)
        response.users.size should be(18)
        response.users.count(_.id == KnoraSystemInstances.Users.AnonymousUser.id) should be(0)
        response.users.count(_.id == KnoraSystemInstances.Users.SystemUser.id) should be(0)
      }

      "fail with unauthorized when asked by an anonymous user" in {
        val exit = UnsafeZioRun.run(getAllUsers(SharedTestDataADM.anonymousUser))
        assertFailsWithA[ForbiddenException](exit)
      }
    }
  }

  "The UsersResponder " when {
    "asked about an user identified by 'iri' " should {
      "return a profile if the user (root user) is known" in {
        val actual = UnsafeZioRun.runOrThrow(
          UsersResponder.findUserByIri(
            UserIri.unsafeFrom(rootUser.id),
            UserInformationTypeADM.Full,
            KnoraSystemInstances.Users.SystemUser
          )
        )
        actual shouldBe Some(rootUser.ofType(UserInformationTypeADM.Full))
      }

      "return 'None' when the user is unknown" in {
        val actual = UnsafeZioRun.runOrThrow(
          UsersResponder.findUserByIri(
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
          UsersResponder.findUserByEmail(
            Email.unsafeFrom(rootUser.email),
            UserInformationTypeADM.Full,
            KnoraSystemInstances.Users.SystemUser
          )
        )
        actual shouldBe Some(rootUser.ofType(UserInformationTypeADM.Full))
      }

      "return 'None' when the user is unknown" in {
        val actual = UnsafeZioRun.runOrThrow(
          UsersResponder.findUserByEmail(
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
          UsersResponder.findUserByUsername(
            Username.unsafeFrom(rootUser.username),
            UserInformationTypeADM.Full,
            KnoraSystemInstances.Users.SystemUser
          )
        )
        actual shouldBe Some(rootUser.ofType(UserInformationTypeADM.Full))
      }

      "return 'None' when the user is unknown" in {
        val actual = UnsafeZioRun.runOrThrow(
          UsersResponder.findUserByUsername(
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
          UsersResponder.createNewUserADM(
            UserCreateRequest(
              username = Username.unsafeFrom("donald.duck"),
              email = Email.unsafeFrom("donald.duck@example.com"),
              givenName = GivenName.unsafeFrom("Donald"),
              familyName = FamilyName.unsafeFrom("Duck"),
              password = Password.unsafeFrom("test"),
              status = UserStatus.from(true),
              lang = LanguageCode.en,
              systemAdmin = SystemAdmin.IsNotSystemAdmin
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
          UsersResponder.createNewUserADM(
            UserCreateRequest(
              username = Username.unsafeFrom("root"),
              email = Email.unsafeFrom("root2@example.com"),
              givenName = GivenName.unsafeFrom("Donald"),
              familyName = FamilyName.unsafeFrom("Duck"),
              password = Password.unsafeFrom("test"),
              status = UserStatus.from(true),
              lang = LanguageCode.en,
              systemAdmin = SystemAdmin.IsNotSystemAdmin
            ),
            UUID.randomUUID
          )
        )
        assertFailsWithA[DuplicateValueException](exit, s"User with the username 'root' already exists")
      }

      "return a 'DuplicateValueException' if the supplied 'email' is not unique" in {
        val exit = UnsafeZioRun.run(
          UsersResponder.createNewUserADM(
            UserCreateRequest(
              username = Username.unsafeFrom("root2"),
              email = Email.unsafeFrom("root@example.com"),
              givenName = GivenName.unsafeFrom("Donald"),
              familyName = FamilyName.unsafeFrom("Duck"),
              password = Password.unsafeFrom("test"),
              status = UserStatus.from(true),
              lang = LanguageCode.en,
              systemAdmin = SystemAdmin.IsNotSystemAdmin
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
        val response1: UserOperationResponseADM = UnsafeZioRun.runOrThrow(
          UsersResponder.changeBasicUserInformationADM(
            SharedTestDataADM.normalUser.userIri,
            BasicUserInformationChangeRequest(givenName = Some(GivenName.unsafeFrom("Donald"))),
            UUID.randomUUID
          )
        )

        response1.user.givenName should equal("Donald")

        /* User information is updated by a system admin */
        val response2: UserOperationResponseADM = UnsafeZioRun.runOrThrow(
          UsersResponder.changeBasicUserInformationADM(
            SharedTestDataADM.normalUser.userIri,
            BasicUserInformationChangeRequest(familyName = Some(FamilyName.unsafeFrom("Duck"))),
            UUID.randomUUID
          )
        )

        response2.user.familyName should equal("Duck")

        /* User information is updated by a system admin */
        val response3: UserOperationResponseADM = UnsafeZioRun.runOrThrow(
          UsersResponder.changeBasicUserInformationADM(
            SharedTestDataADM.normalUser.userIri,
            BasicUserInformationChangeRequest(
              givenName = Some(GivenName.unsafeFrom(SharedTestDataADM.normalUser.givenName)),
              familyName = Some(FamilyName.unsafeFrom(SharedTestDataADM.normalUser.familyName))
            ),
            apiRequestID = UUID.randomUUID
          )
        )

        response3.user.givenName should equal(SharedTestDataADM.normalUser.givenName)
        response3.user.familyName should equal(SharedTestDataADM.normalUser.familyName)
      }

      "return a 'DuplicateValueException' if the supplied 'username' is not unique" in {
        val duplicateUsername =
          Some(Username.unsafeFrom(SharedTestDataADM.anythingUser1.username))
        val exit = UnsafeZioRun.run(
          UsersResponder.changeBasicUserInformationADM(
            SharedTestDataADM.normalUser.userIri,
            BasicUserInformationChangeRequest(username = duplicateUsername),
            UUID.randomUUID
          )
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"User with the username '${SharedTestDataADM.anythingUser1.username}' already exists"
        )
      }

      "return a 'DuplicateValueException' if the supplied 'email' is not unique" in {
        val duplicateEmail = Some(Email.unsafeFrom(SharedTestDataADM.anythingUser1.email))
        val exit = UnsafeZioRun.run(
          UsersResponder.changeBasicUserInformationADM(
            SharedTestDataADM.normalUser.userIri,
            BasicUserInformationChangeRequest(email = duplicateEmail),
            UUID.randomUUID
          )
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"User with the email '${SharedTestDataADM.anythingUser1.email}' already exists"
        )
      }

      "UPDATE the user's password (by himself)" in {
        val requesterPassword = Password.unsafeFrom("test")
        val newPassword       = Password.unsafeFrom("test123456")
        UnsafeZioRun.runOrThrow(
          UsersResponder.changePassword(
            SharedTestDataADM.normalUser.userIri,
            PasswordChangeRequest(requesterPassword, newPassword),
            SharedTestDataADM.normalUser,
            UUID.randomUUID
          )
        )

        // need to be able to authenticate credentials with new password
        val cedId       = CredentialsIdentifier.UsernameIdentifier(Username.unsafeFrom(normalUser.username))
        val credentials = KnoraCredentialsV2.KnoraPasswordCredentialsV2(cedId, newPassword.value)
        val resF        = UnsafeZioRun.runToFuture(Authenticator.authenticateCredentialsV2(Some(credentials)))

        resF map { res => assert(res) }
      }

      "UPDATE the user's password (by a system admin)" in {
        val requesterPassword = Password.unsafeFrom("test")
        val newPassword       = Password.unsafeFrom("test654321")

        UnsafeZioRun.runOrThrow(
          UsersResponder.changePassword(
            SharedTestDataADM.normalUser.userIri,
            PasswordChangeRequest(requesterPassword, newPassword),
            SharedTestDataADM.rootUser,
            UUID.randomUUID()
          )
        )

        // need to be able to authenticate credentials with new password
        val cedId       = CredentialsIdentifier.UsernameIdentifier(Username.unsafeFrom(normalUser.username))
        val credentials = KnoraCredentialsV2.KnoraPasswordCredentialsV2(cedId, "test654321")
        val resF        = UnsafeZioRun.runToFuture(Authenticator.authenticateCredentialsV2(Some(credentials)))

        resF map { res => assert(res) }
      }

      "UPDATE the user's status, making them inactive " in {
        val response1 = UnsafeZioRun.runOrThrow(
          UsersResponder.changeUserStatus(
            SharedTestDataADM.normalUser.userIri,
            UserStatus.from(false),
            UUID.randomUUID
          )
        )
        response1.user.status should equal(false)

        val response2 = UnsafeZioRun.runOrThrow(
          UsersResponder.changeUserStatus(
            SharedTestDataADM.normalUser.userIri,
            UserStatus.from(true),
            UUID.randomUUID()
          )
        )
        response2.user.status should equal(true)
      }

      "UPDATE the user's system admin membership" in {
        val response1 = UnsafeZioRun.runOrThrow(
          UsersResponder.changeSystemAdmin(
            SharedTestDataADM.normalUser.userIri,
            SystemAdmin.IsSystemAdmin,
            UUID.randomUUID()
          )
        )
        response1.user.isSystemAdmin should equal(true)

        val response2 = UnsafeZioRun.runOrThrow(
          UsersResponder.changeSystemAdmin(
            SharedTestDataADM.normalUser.userIri,
            SystemAdmin.IsNotSystemAdmin,
            UUID.randomUUID()
          )
        )
        response2.user.permissions.isSystemAdmin should equal(false)
      }
    }

    "asked to update the user's project membership" should {
      "ADD user to project" in {

        // get current project memberships
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponder.findProjectMemberShipsByIri(normalUser.userIri))
        membershipsBeforeUpdate.projects should equal(Seq())

        // add user to images project (00FF)
        UnsafeZioRun.runOrThrow(
          UsersResponder.addProjectToUserIsInProject(normalUser.userIri, imagesProject.projectIri, UUID.randomUUID())
        )

        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponder.findProjectMemberShipsByIri(normalUser.userIri))
        membershipsAfterUpdate.projects should equal(Seq(imagesProject))

        val received = UnsafeZioRun.runOrThrow(
          ProjectsResponderADM.projectMembersGetRequestADM(
            IriIdentifier.unsafeFrom(imagesProject.id),
            KnoraSystemInstances.Users.SystemUser
          )
        )
        received.members.map(_.id) should contain(normalUser.id)
      }

      "ADD user to project as project admin" in {
        // get current project memberships
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponder.findProjectMemberShipsByIri(normalUser.userIri))
        membershipsBeforeUpdate.projects.map(_.id).sorted should equal(Seq(imagesProject.id).sorted)

        // add user to images project (00FF)
        UnsafeZioRun.runOrThrow(
          UsersResponder.addProjectToUserIsInProject(
            normalUser.userIri,
            incunabulaProject.projectIri,
            UUID.randomUUID()
          )
        )

        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponder.findProjectMemberShipsByIri(normalUser.userIri))
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
          UnsafeZioRun.runOrThrow(UsersResponder.findProjectMemberShipsByIri(normalUser.userIri))
        membershipsBeforeUpdate.projects.map(_.id).sorted should equal(
          Seq(imagesProject.id, incunabulaProject.id).sorted
        )

        // add user as project admin to images project
        UnsafeZioRun.runOrThrow(
          UsersResponder.addProjectToUserIsInProjectAdminGroup(
            normalUser.userIri,
            imagesProject.projectIri,
            UUID.randomUUID()
          )
        )

        // verify that the user has been added as project admin to the images project
        val projectAdminMembershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponder.findUserProjectAdminMemberships(normalUser.userIri))
        projectAdminMembershipsBeforeUpdate.projects.map(_.id).sorted should equal(Seq(imagesProject.id).sorted)

        // remove the user as member of the images project
        UnsafeZioRun.runOrThrow(
          UsersResponder.removeProjectFromUserIsInProjectAndIsInProjectAdminGroup(
            normalUser.userIri,
            imagesProject.projectIri,
            UUID.randomUUID()
          )
        )

        // verify that the user has been removed as project member of the images project
        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponder.findProjectMemberShipsByIri(normalUser.userIri))
        membershipsAfterUpdate.projects should equal(Seq(incunabulaProject))

        // this should also have removed him as project admin from images project
        val projectAdminMembershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponder.findUserProjectAdminMemberships(normalUser.userIri))
        projectAdminMembershipsAfterUpdate.projects should equal(Seq())

        // also check that the user has been removed from the project's list of users
        val received = UnsafeZioRun.runOrThrow(
          ProjectsResponderADM.projectMembersGetRequestADM(IriIdentifier.unsafeFrom(imagesProject.id), rootUser)
        )
        received.members should not contain normalUser.ofType(UserInformationTypeADM.Restricted)
      }
    }

    "asked to update the user's project admin group membership" should {
      "Not ADD user to project admin group if he is not a member of that project" in {
        // get the current project admin memberships (should be empty)
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponder.findUserProjectAdminMemberships(normalUser.userIri))
        membershipsBeforeUpdate.projects should equal(Seq())

        // try to add user as project admin to images project (expected to fail because he is not a member of the project)
        val exit = UnsafeZioRun.run(
          UsersResponder.addProjectToUserIsInProjectAdminGroup(
            normalUser.userIri,
            imagesProject.projectIri,
            UUID.randomUUID()
          )
        )
        assertFailsWithA[BadRequestException](
          exit,
          "User http://rdfh.ch/users/normaluser is not a member of project http://rdfh.ch/projects/00FF. A user needs to be a member of the project to be added as project admin."
        )
      }

      "ADD user to project admin group" in {
        // get the current project admin memberships (should be empty)
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponder.findUserProjectAdminMemberships(normalUser.userIri))
        membershipsBeforeUpdate.projects should equal(Seq())

        // add user as project member to images project
        UnsafeZioRun.runOrThrow(
          UsersResponder.addProjectToUserIsInProject(normalUser.userIri, imagesProject.projectIri, UUID.randomUUID())
        )

        // add user as project admin to images project
        UnsafeZioRun.runOrThrow(
          UsersResponder.addProjectToUserIsInProjectAdminGroup(
            normalUser.userIri,
            imagesProject.projectIri,
            UUID.randomUUID()
          )
        )

        // get the updated project admin memberships (should contain images project)
        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponder.findUserProjectAdminMemberships(normalUser.userIri))
        membershipsAfterUpdate.projects should equal(Seq(imagesProject))

        // get project admins for images project (should contain normal user)
        val received = UnsafeZioRun.runOrThrow(
          ProjectsResponderADM.projectAdminMembersGetRequestADM(IriIdentifier.unsafeFrom(imagesProject.id), rootUser)
        )
        received.members.map(_.id) should contain(normalUser.id)
      }

      "DELETE user from project admin group" in {
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponder.findUserProjectAdminMemberships(normalUser.userIri))
        membershipsBeforeUpdate.projects should equal(Seq(imagesProject))

        UnsafeZioRun.runOrThrow(
          UsersResponder.removeProjectFromUserIsInProjectAdminGroup(
            normalUser.userIri,
            imagesProject.projectIri,
            UUID.randomUUID()
          )
        )

        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponder.findUserProjectAdminMemberships(normalUser.userIri))
        membershipsAfterUpdate.projects should equal(Seq())

        val received = UnsafeZioRun.runOrThrow(
          ProjectsResponderADM.projectAdminMembersGetRequestADM(IriIdentifier.unsafeFrom(imagesProject.id), rootUser)
        )
        received.members should not contain normalUser.ofType(UserInformationTypeADM.Restricted)
      }
    }

    "asked to update the user's group membership" should {
      "ADD user to group" in {
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(UsersResponder.findGroupMembershipsByIri(normalUser.userIri))
        membershipsBeforeUpdate should equal(Seq())

        UnsafeZioRun.runOrThrow(
          UsersResponder.addGroupToUserIsInGroup(normalUser.userIri, imagesReviewerGroup.groupIri, UUID.randomUUID())
        )

        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponder.findGroupMembershipsByIri(normalUser.userIri))
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
          UnsafeZioRun.runOrThrow(UsersResponder.findGroupMembershipsByIri(normalUser.userIri))
        membershipsBeforeUpdate.map(_.id) should equal(Seq(imagesReviewerGroup.id))

        UnsafeZioRun.runOrThrow(
          UsersResponder.removeGroupFromUserIsInGroup(
            normalUser.userIri,
            imagesReviewerGroup.groupIri,
            UUID.randomUUID()
          )
        )

        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(UsersResponder.findGroupMembershipsByIri(normalUser.userIri))
        membershipsAfterUpdate should equal(Seq())

        appActor ! GroupMembersGetRequestADM(
          groupIri = imagesReviewerGroup.id,
          requestingUser = rootUser
        )
        val received: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)

        received.members.map(_.id) should not contain normalUser.id
      }
    }
  }
}
