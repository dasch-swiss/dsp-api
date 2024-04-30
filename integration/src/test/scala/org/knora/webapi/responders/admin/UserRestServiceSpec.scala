/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import org.apache.pekko.testkit.ImplicitSender
import zio.Chunk
import zio.ZIO

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import dsp.valueobjects.LanguageCode
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.routing.authenticationmessages.CredentialsIdentifier
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.BasicUserInformationChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.PasswordChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.StatusChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.SystemAdminChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.UserCreateRequest
import org.knora.webapi.slice.admin.api.service.GroupRestService
import org.knora.webapi.slice.admin.api.service.ProjectRestService
import org.knora.webapi.slice.admin.api.service.UserRestService
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.model._
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

class UserRestServiceSpec extends CoreSpec with ImplicitSender {

  private val rootUser   = SharedTestDataADM.rootUser
  private val normalUser = SharedTestDataADM.normalUser

  private val imagesProject       = SharedTestDataADM.imagesProject
  private val incunabulaProject   = SharedTestDataADM.incunabulaProject
  private val imagesReviewerGroup = SharedTestDataADM.imagesReviewerGroup

  private val groupRestService   = ZIO.serviceWithZIO[GroupRestService]
  private val projectRestService = ZIO.serviceWithZIO[ProjectRestService]
  private val userRestService    = ZIO.serviceWithZIO[UserRestService]

  "The UserRestService" when {
    "calling getAllUsers" should {
      "with a SystemAdmin should return all real users" in {
        val response = UnsafeZioRun.runOrThrow(userRestService(_.getAllUsers(rootUser)))
        response.users.nonEmpty should be(true)
        response.users.size should be(18)
        response.users.count(_.id == KnoraUserRepo.builtIn.AnonymousUser.id.value) should be(0)
        response.users.count(_.id == KnoraUserRepo.builtIn.SystemUser.id.value) should be(0)
      }

      "fail with unauthorized when asked by an anonymous user" in {
        val exit = UnsafeZioRun.run(userRestService(_.getAllUsers(SharedTestDataADM.anonymousUser)))
        assertFailsWithA[ForbiddenException](exit)
      }
    }

    "calling getUserByEmail" should {

      "return a profile if the user (root user) is known" in {
        val actual = UnsafeZioRun.runOrThrow(
          userRestService(_.getUserByEmail(KnoraSystemInstances.Users.SystemUser, Email.unsafeFrom(rootUser.email))),
        )
        actual.user shouldBe rootUser.ofType(UserInformationType.Restricted)
      }

      "return 'None' when the user is unknown" in {
        val exit = UnsafeZioRun.run(
          userRestService(
            _.getUserByEmail(KnoraSystemInstances.Users.SystemUser, Email.unsafeFrom("userwrong@example.com")),
          ),
        )
        assertFailsWithA[NotFoundException](exit)
      }
    }

    "calling getUserByUsername" should {

      def getUserByUsername(requestingUser: User, username: Username) =
        userRestService(_.getUserByUsername(requestingUser, username))

      "return a profile if the user (root user) is known" in {
        val actual = UnsafeZioRun.runOrThrow(
          getUserByUsername(KnoraSystemInstances.Users.SystemUser, rootUser.getUsername),
        )

        actual.user shouldBe rootUser.ofType(UserInformationType.Restricted)
      }

      "return 'None' when the user is unknown" in {
        val exit = UnsafeZioRun.run(
          getUserByUsername(
            KnoraSystemInstances.Users.SystemUser,
            Username.unsafeFrom("userwrong"),
          ),
        )
        assertFailsWithA[NotFoundException](exit)
      }
    }

    "asked about an user identified by 'iri' " should {
      "return a profile if the user (root user) is known" in {
        val actual =
          UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[UserService](_.findUserByIri(UserIri.unsafeFrom(rootUser.id))))
        actual shouldBe Some(rootUser.ofType(UserInformationType.Full))
      }

      "return 'None' when the user is unknown" in {
        val actual = UnsafeZioRun.runOrThrow(
          ZIO.serviceWithZIO[UserService](_.findUserByIri(UserIri.unsafeFrom("http://rdfh.ch/users/notexisting"))),
        )
        actual shouldBe None
      }
    }

    "asked to create a new user" should {
      "CREATE the user and return it's profile if the supplied email is unique " in {
        val response = UnsafeZioRun.runOrThrow(
          userRestService(
            _.createUser(
              rootUser,
              UserCreateRequest(
                username = Username.unsafeFrom("donald.duck"),
                email = Email.unsafeFrom("donald.duck@example.com"),
                givenName = GivenName.unsafeFrom("Donald"),
                familyName = FamilyName.unsafeFrom("Duck"),
                password = Password.unsafeFrom("test"),
                status = UserStatus.from(true),
                lang = LanguageCode.en,
                systemAdmin = SystemAdmin.IsNotSystemAdmin,
              ),
            ),
          ),
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
          userRestService(
            _.createUser(
              rootUser,
              UserCreateRequest(
                username = Username.unsafeFrom("root"),
                email = Email.unsafeFrom("root2@example.com"),
                givenName = GivenName.unsafeFrom("Donald"),
                familyName = FamilyName.unsafeFrom("Duck"),
                password = Password.unsafeFrom("test"),
                status = UserStatus.from(true),
                lang = LanguageCode.en,
                systemAdmin = SystemAdmin.IsNotSystemAdmin,
              ),
            ),
          ),
        )
        assertFailsWithA[DuplicateValueException](exit, s"User with the username 'root' already exists")
      }

      "return a 'DuplicateValueException' if the supplied 'email' is not unique" in {
        val exit = UnsafeZioRun.run(
          userRestService(
            _.createUser(
              rootUser,
              UserCreateRequest(
                username = Username.unsafeFrom("root2"),
                email = Email.unsafeFrom("root@example.com"),
                givenName = GivenName.unsafeFrom("Donald"),
                familyName = FamilyName.unsafeFrom("Duck"),
                password = Password.unsafeFrom("test"),
                status = UserStatus.from(true),
                lang = LanguageCode.en,
                systemAdmin = SystemAdmin.IsNotSystemAdmin,
              ),
            ),
          ),
        )
        assertFailsWithA[DuplicateValueException](exit, s"User with the email 'root@example.com' already exists")
      }
    }

    "asked to update a user" should {
      "UPDATE the user's basic information" in {
        /* User information is updated by the user */
        val response1 = UnsafeZioRun.runOrThrow(
          userRestService(
            _.updateUser(
              rootUser,
              SharedTestDataADM.normalUser.userIri,
              BasicUserInformationChangeRequest(givenName = Some(GivenName.unsafeFrom("Donald"))),
            ),
          ),
        )

        response1.user.givenName should equal("Donald")

        /* User information is updated by a system admin */
        val response2 = UnsafeZioRun.runOrThrow(
          userRestService(
            _.updateUser(
              rootUser,
              SharedTestDataADM.normalUser.userIri,
              BasicUserInformationChangeRequest(familyName = Some(FamilyName.unsafeFrom("Duck"))),
            ),
          ),
        )

        response2.user.familyName should equal("Duck")

        /* User information is updated by a system admin */
        val response3 = UnsafeZioRun.runOrThrow(
          userRestService(
            _.updateUser(
              rootUser,
              SharedTestDataADM.normalUser.userIri,
              BasicUserInformationChangeRequest(
                givenName = Some(GivenName.unsafeFrom(SharedTestDataADM.normalUser.givenName)),
                familyName = Some(FamilyName.unsafeFrom(SharedTestDataADM.normalUser.familyName)),
              ),
            ),
          ),
        )

        response3.user.givenName should equal(SharedTestDataADM.normalUser.givenName)
        response3.user.familyName should equal(SharedTestDataADM.normalUser.familyName)
      }

      "return a 'DuplicateValueException' if the supplied 'username' is not unique" in {
        val duplicateUsername =
          Some(Username.unsafeFrom(SharedTestDataADM.anythingUser1.username))
        val exit = UnsafeZioRun.run(
          userRestService(
            _.updateUser(
              rootUser,
              SharedTestDataADM.normalUser.userIri,
              BasicUserInformationChangeRequest(username = duplicateUsername),
            ),
          ),
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"User with the username '${SharedTestDataADM.anythingUser1.username}' already exists",
        )
      }

      "return a 'DuplicateValueException' if the supplied 'email' is not unique" in {
        val duplicateEmail = Some(Email.unsafeFrom(SharedTestDataADM.anythingUser1.email))
        val exit = UnsafeZioRun.run(
          userRestService(
            _.updateUser(
              rootUser,
              SharedTestDataADM.normalUser.userIri,
              BasicUserInformationChangeRequest(email = duplicateEmail),
            ),
          ),
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"User with the email '${SharedTestDataADM.anythingUser1.email}' already exists",
        )
      }

      "UPDATE the user's password (by himself)" in {
        val requesterPassword = Password.unsafeFrom("test")
        val newPassword       = Password.unsafeFrom("test123456")
        UnsafeZioRun.runOrThrow(
          userRestService(
            _.changePassword(normalUser, normalUser.userIri, PasswordChangeRequest(requesterPassword, newPassword)),
          ),
        )

        // need to be able to authenticate credentials with new password
        val cedId       = CredentialsIdentifier.UsernameIdentifier(Username.unsafeFrom(normalUser.username))
        val credentials = KnoraCredentialsV2.KnoraPasswordCredentialsV2(cedId, newPassword.value)
        val resF =
          UnsafeZioRun.runToFuture(ZIO.serviceWithZIO[Authenticator](_.authenticateCredentialsV2(Some(credentials))))

        resF map { res => assert(res) }
      }

      "UPDATE the user's password (by a system admin)" in {
        val requesterPassword = Password.unsafeFrom("test")
        val newPassword       = Password.unsafeFrom("test654321")

        UnsafeZioRun.runOrThrow(
          userRestService(
            _.changePassword(
              SharedTestDataADM.rootUser,
              SharedTestDataADM.normalUser.userIri,
              PasswordChangeRequest(requesterPassword, newPassword),
            ),
          ),
        )

        // need to be able to authenticate credentials with new password
        val cedId       = CredentialsIdentifier.UsernameIdentifier(Username.unsafeFrom(normalUser.username))
        val credentials = KnoraCredentialsV2.KnoraPasswordCredentialsV2(cedId, "test654321")
        val resF =
          UnsafeZioRun.runToFuture(ZIO.serviceWithZIO[Authenticator](_.authenticateCredentialsV2(Some(credentials))))

        resF map { res => assert(res) }
      }

      "UPDATE the user's status, making them inactive " in {
        val response1 = UnsafeZioRun.runOrThrow(
          userRestService(
            _.changeStatus(rootUser, SharedTestDataADM.normalUser.userIri, StatusChangeRequest(UserStatus.Inactive)),
          ),
        )
        response1.user.status should equal(false)

        val response2 = UnsafeZioRun.runOrThrow(
          userRestService(
            _.changeStatus(rootUser, SharedTestDataADM.normalUser.userIri, StatusChangeRequest(UserStatus.Active)),
          ),
        )
        response2.user.status should equal(true)
      }

      "UPDATE the user's system admin membership" in {
        val response1 = UnsafeZioRun.runOrThrow(
          userRestService(
            _.changeSystemAdmin(
              rootUser,
              SharedTestDataADM.normalUser.userIri,
              SystemAdminChangeRequest(SystemAdmin.IsSystemAdmin),
            ),
          ),
        )
        response1.user.isSystemAdmin should equal(true)

        val response2 = UnsafeZioRun.runOrThrow(
          userRestService(
            _.changeSystemAdmin(
              rootUser,
              SharedTestDataADM.normalUser.userIri,
              SystemAdminChangeRequest(SystemAdmin.IsNotSystemAdmin),
            ),
          ),
        )
        response2.user.permissions.isSystemAdmin should equal(false)
      }
    }

    "asked to update the user's project membership" should {

      "ADD user to project" in {

        // get current project memberships
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(userRestService(_.getProjectMemberShipsByUserIri(normalUser.userIri)))
        membershipsBeforeUpdate.projects should equal(Chunk.empty)

        // add user to images project (00FF)
        UnsafeZioRun.runOrThrow(
          userRestService(_.addUserToProject(rootUser, normalUser.userIri, imagesProject.projectIri)),
        )

        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(userRestService(_.getProjectMemberShipsByUserIri(normalUser.userIri)))
        membershipsAfterUpdate.projects.map(_.id) should equal(Chunk(imagesProject.id))

        val received = UnsafeZioRun.runOrThrow(
          projectRestService(
            _.getProjectMembersById(KnoraSystemInstances.Users.SystemUser, imagesProject.projectIri),
          ),
        )
        received.members.map(_.id) should contain(normalUser.id)
      }

      "ADD user to project as project admin" in {
        // get current project memberships
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(userRestService(_.getProjectMemberShipsByUserIri(normalUser.userIri)))
        membershipsBeforeUpdate.projects.map(_.id).sorted should equal(Seq(imagesProject.id).sorted)

        // add user to images project (00FF)
        UnsafeZioRun.runOrThrow(
          userRestService(_.addUserToProject(rootUser, normalUser.userIri, incunabulaProject.projectIri)),
        )

        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(userRestService(_.getProjectMemberShipsByUserIri(normalUser.userIri)))
        membershipsAfterUpdate.projects.map(_.id).sorted should equal(
          Seq(imagesProject.id, incunabulaProject.id).sorted,
        )

        val received = UnsafeZioRun.runOrThrow(
          projectRestService(
            _.getProjectMembersById(KnoraSystemInstances.Users.SystemUser, incunabulaProject.projectIri),
          ),
        )
        received.members.map(_.id) should contain(normalUser.id)
      }

      "DELETE user from project and also as project admin" in {
        // check project memberships (user should be member of images and incunabula projects)
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(userRestService(_.getProjectMemberShipsByUserIri(normalUser.userIri)))
        membershipsBeforeUpdate.projects.map(_.id).sorted should equal(
          Seq(imagesProject.id, incunabulaProject.id).sorted,
        )

        // add user as project admin to images project
        UnsafeZioRun.runOrThrow(
          userRestService(_.addUserToProjectAsAdmin(rootUser, normalUser.userIri, imagesProject.projectIri)),
        )

        // verify that the user has been added as project admin to the images project
        val projectAdminMembershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(userRestService(_.getProjectAdminMemberShipsByUserIri(normalUser.userIri)))
        projectAdminMembershipsBeforeUpdate.projects.map(_.id).sorted should equal(Seq(imagesProject.id).sorted)

        // remove the user as member of the images project
        UnsafeZioRun.runOrThrow(
          userRestService(_.removeUserFromProject(rootUser, normalUser.userIri, imagesProject.projectIri)),
        )

        // verify that the user has been removed as project member of the images project
        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(userRestService(_.getProjectMemberShipsByUserIri(normalUser.userIri)))
        membershipsAfterUpdate.projects.map(_.id) should equal(Chunk(incunabulaProject.id))

        // this should also have removed him as project admin from images project
        val projectAdminMembershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(userRestService(_.getProjectAdminMemberShipsByUserIri(normalUser.userIri)))
        projectAdminMembershipsAfterUpdate.projects should equal(Seq())

        // also check that the user has been removed from the project's list of users
        val received = UnsafeZioRun.runOrThrow(
          projectRestService(_.getProjectMembersById(rootUser, imagesProject.projectIri)),
        )
        received.members should not contain normalUser.ofType(UserInformationType.Restricted)
      }
    }

    "asked to update the user's project admin group membership" should {
      "Not ADD user to project admin group if he is not a member of that project" in {
        // get the current project admin memberships (should be empty)
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(userRestService(_.getProjectAdminMemberShipsByUserIri(normalUser.userIri)))
        membershipsBeforeUpdate.projects should equal(Seq())

        // try to add user as project admin to images project (expected to fail because he is not a member of the project)
        val exit = UnsafeZioRun.run(
          userRestService(_.addUserToProjectAsAdmin(rootUser, normalUser.userIri, imagesProject.projectIri)),
        )
        assertFailsWithA[BadRequestException](
          exit,
          "User http://rdfh.ch/users/normaluser is not a member of project http://rdfh.ch/projects/00FF. A user needs to be a member of the project to be added as project admin.",
        )
      }

      "ADD user to project admin group" in {
        // get the current project admin memberships (should be empty)
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(userRestService(_.getProjectAdminMemberShipsByUserIri(normalUser.userIri)))
        membershipsBeforeUpdate.projects should equal(Seq())

        // add user as project member to images project
        UnsafeZioRun.runOrThrow(
          userRestService(_.addUserToProject(rootUser, normalUser.userIri, imagesProject.projectIri)),
        )

        // add user as project admin to images project
        UnsafeZioRun.runOrThrow(
          userRestService(_.addUserToProjectAsAdmin(rootUser, normalUser.userIri, imagesProject.projectIri)),
        )

        // get the updated project admin memberships (should contain images project)
        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(userRestService(_.getProjectAdminMemberShipsByUserIri(normalUser.userIri)))
        membershipsAfterUpdate.projects.map(_.id) should equal(Seq(imagesProject.id))

        // get project admins for images project (should contain normal user)
        val received = UnsafeZioRun.runOrThrow(
          projectRestService(_.getProjectAdminMembersById(rootUser, imagesProject.projectIri)),
        )
        received.members.map(_.id) should contain(normalUser.id)
      }

      "DELETE user from project admin group" in {
        val membershipsBeforeUpdate =
          UnsafeZioRun.runOrThrow(userRestService(_.getProjectAdminMemberShipsByUserIri(normalUser.userIri)))
        membershipsBeforeUpdate.projects.map(_.id) should equal(Seq(imagesProject.id))

        UnsafeZioRun.runOrThrow(
          userRestService(_.removeUserFromProjectAsAdmin(rootUser, normalUser.userIri, imagesProject.projectIri)),
        )

        val membershipsAfterUpdate =
          UnsafeZioRun.runOrThrow(userRestService(_.getProjectAdminMemberShipsByUserIri(normalUser.userIri)))
        membershipsAfterUpdate.projects should equal(Seq())

        val received = UnsafeZioRun.runOrThrow(
          projectRestService(_.getProjectAdminMembersById(rootUser, imagesProject.projectIri)),
        )
        received.members should not contain normalUser.ofType(UserInformationType.Restricted)
      }
    }

    "asked to update the user's group membership" should {
      def findGroupMembershipsByIri(userIri: UserIri): Seq[Group] =
        UnsafeZioRun.runOrThrow(
          ZIO.serviceWithZIO[UserService](_.findUserByIri(userIri).map(_.map(_.groups).getOrElse(Seq.empty))),
        )

      "ADD user to group" in {
        val membershipsBeforeUpdate = findGroupMembershipsByIri(normalUser.userIri)
        membershipsBeforeUpdate should equal(Seq())

        UnsafeZioRun.runOrThrow(
          userRestService(_.addUserToGroup(rootUser, normalUser.userIri, imagesReviewerGroup.groupIri)),
        )

        val membershipsAfterUpdate = findGroupMembershipsByIri(normalUser.userIri)
        membershipsAfterUpdate.map(_.id) should equal(Seq(imagesReviewerGroup.id))

        val received = UnsafeZioRun.runOrThrow(
          groupRestService(
            _.getGroupMembers(
              GroupIri.unsafeFrom(imagesReviewerGroup.id),
              rootUser,
            ),
          ),
        )
        received.members.map(_.id) should contain(normalUser.id)
      }

      "DELETE user from group" in {
        val membershipsBeforeUpdate = findGroupMembershipsByIri(normalUser.userIri)
        membershipsBeforeUpdate.map(_.id) should equal(Seq(imagesReviewerGroup.id))

        UnsafeZioRun.runOrThrow(
          userRestService(_.removeUserFromGroup(rootUser, normalUser.userIri, imagesReviewerGroup.groupIri)),
        )

        val membershipsAfterUpdate = findGroupMembershipsByIri(normalUser.userIri)
        membershipsAfterUpdate should equal(Seq())

        val received = UnsafeZioRun.runOrThrow(
          groupRestService(
            _.getGroupMembers(
              GroupIri.unsafeFrom(imagesReviewerGroup.id),
              rootUser,
            ),
          ),
        )
        received.members.map(_.id) should not contain normalUser
      }
    }
  }
}
