/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.Chunk
import zio.ZIO
import zio.test.*
import zio.test.Assertion.*

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import org.knora.webapi.*
import org.knora.webapi.messages.admin.responder.usersmessages.*
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.SystemUser
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.*
import org.knora.webapi.slice.admin.api.model.UserDto
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.security.Authenticator

object UserRestServiceSpec extends E2EZSpec {

  private val groupRestService   = ZIO.serviceWithZIO[GroupRestService]
  private val projectRestService = ZIO.serviceWithZIO[ProjectRestService]
  private val userRestService    = ZIO.serviceWithZIO[UserRestService]
  private val userService        = ZIO.serviceWithZIO[UserService]

  override val e2eSpec = suite("The UserRestService")(
    suite("calling getAllUsers")(
      test("with a SystemAdmin should return all real users") {
        userRestService(_.getAllUsers(rootUser))
          .map(_.users)
          .map(users =>
            assertTrue(
              users.size == 18,
              !users.exists(_.id == KnoraUserRepo.builtIn.AnonymousUser.id.value),
              !users.exists(_.id == KnoraUserRepo.builtIn.SystemUser.id.value),
            ),
          )
      },
      test("fail with unauthorized when asked by an anonymous user") {
        userRestService(_.getAllUsers(anonymousUser)).exit
          .map(assert(_)(failsWithA[ForbiddenException]))
      },
    ),
    suite("calling getUserByEmail")(
      test("return a profile if the user (root user) is known") {
        userRestService(_.getUserByEmail(SystemUser)(Email.unsafeFrom(rootUser.email)))
          .map(actual => assertTrue(actual.user == UserDto.from(rootUser.ofType(UserInformationType.Restricted))))
      },
      test("return 'NotFound' when the user is unknown") {
        userRestService(
          _.getUserByEmail(SystemUser)(Email.unsafeFrom("userwrong@example.com")),
        ).exit.map(exit => assert(exit)(failsWithA[NotFoundException]))
      },
    ),
    suite("calling getUserByUsername")(
      test("return a profile if the user (root user) is known") {
        userRestService(_.getUserByUsername(SystemUser)(rootUser.getUsername))
          .map(actual => assertTrue(actual.user == UserDto.from(rootUser.ofType(UserInformationType.Restricted))))
      },
      test("return 'None' when the user is unknown") {
        userRestService(_.getUserByUsername(SystemUser)(Username.unsafeFrom("userwrong"))).exit.map(exit =>
          assert(exit)(failsWithA[NotFoundException]),
        )
      },
    ),
    suite("asked about an user identified by 'iri' ")(
      test("return a profile if the user (root user) is known") {
        userService(_.findUserByIri(UserIri.unsafeFrom(rootUser.id)))
          .map(actual => assertTrue(actual.contains(rootUser.ofType(UserInformationType.Full))))
      },
      test("return 'None' when the user is unknown") {
        userService(_.findUserByIri(UserIri.unsafeFrom("http://rdfh.ch/users/notexisting")))
          .map(actual => assertTrue(actual.isEmpty))
      },
    ),
    suite("asked to create a new user")(
      test("CREATE the user and return it's profile if the supplied email is unique ") {
        val createRequest = UserCreateRequest(
          None,
          Username.unsafeFrom("donald.duck"),
          Email.unsafeFrom("donald.duck@example.com"),
          GivenName.unsafeFrom("Donald"),
          FamilyName.unsafeFrom("Duck"),
          Password.unsafeFrom("test"),
          UserStatus.from(true),
          LanguageCode.EN,
          SystemAdmin.IsNotSystemAdmin,
        )
        userRestService(_.createUser(rootUser)(createRequest))
          .map(_.user)
          .map(user =>
            assertTrue(
              user.username == "donald.duck",
              user.givenName == "Donald",
              user.familyName == "Duck",
              user.email == "donald.duck@example.com",
              user.lang == "en",
            ),
          )
      },
      test("return a 'DuplicateValueException' if the supplied 'username' is not unique") {
        val createRequest = UserCreateRequest(
          None,
          Username.unsafeFrom("root"),
          Email.unsafeFrom("root2@example.com"),
          GivenName.unsafeFrom("Donald"),
          FamilyName.unsafeFrom("Duck"),
          Password.unsafeFrom("test"),
          UserStatus.from(true),
          LanguageCode.EN,
          SystemAdmin.IsNotSystemAdmin,
        )
        userRestService(_.createUser(rootUser)(createRequest)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[DuplicateValueException](s"User with the username 'root' already exists"),
          ),
        )
      },
      test("return a 'DuplicateValueException' if the supplied 'email' is not unique") {
        val createRequest = UserCreateRequest(
          None,
          Username.unsafeFrom("root2"),
          Email.unsafeFrom("root@example.com"),
          GivenName.unsafeFrom("Donald"),
          FamilyName.unsafeFrom("Duck"),
          Password.unsafeFrom("test"),
          UserStatus.from(true),
          LanguageCode.EN,
          SystemAdmin.IsNotSystemAdmin,
        )
        userRestService(_.createUser(rootUser)(createRequest)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[DuplicateValueException](
              s"User with the email 'root@example.com' already exists",
            ),
          ),
        )
      },
    ),
    suite("asked to update a user")(
      test("UPDATE the user's basic information") {
        val changeRequest = BasicUserInformationChangeRequest(
          givenName = Some(GivenName.unsafeFrom("Donald")),
          familyName = Some(FamilyName.unsafeFrom("Duck")),
        )
        userRestService(_.updateUser(rootUser)(normalUser.userIri, changeRequest))
          .map(_.user)
          .map(user => assertTrue(user.givenName == "Donald", user.familyName == "Duck"))
      },
      test("UPDATE the user's basic information by himself") {
        val changeRequest = BasicUserInformationChangeRequest(
          givenName = Some(GivenName.unsafeFrom(normalUser.givenName)),
          familyName = Some(FamilyName.unsafeFrom(normalUser.familyName)),
        )
        userRestService(_.updateUser(normalUser)(normalUser.userIri, changeRequest))
          .map(_.user)
          .map(user =>
            assertTrue(
              user.givenName == normalUser.givenName,
              user.familyName == normalUser.familyName,
            ),
          )
      },
      test("return a 'DuplicateValueException' if the supplied 'username' is not unique") {
        val duplicateUsername = Some(Username.unsafeFrom(anythingUser1.username))
        userRestService(
          _.updateUser(rootUser)(
            normalUser.userIri,
            BasicUserInformationChangeRequest(username = duplicateUsername),
          ),
        ).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[DuplicateValueException](
              s"User with the username '${anythingUser1.username}' already exists",
            ),
          ),
        )
      },
      test("return a 'DuplicateValueException' if the supplied 'email' is not unique") {
        val duplicateEmail = Some(Email.unsafeFrom(anythingUser1.email))
        val changeRequest  = BasicUserInformationChangeRequest(email = duplicateEmail)
        userRestService(_.updateUser(rootUser)(normalUser.userIri, changeRequest)).exit
          .map(
            assert(_)(
              E2EZSpec.failsWithMessageEqualTo[DuplicateValueException](
                s"User with the email '${anythingUser1.email}' already exists",
              ),
            ),
          )
      },
      test("UPDATE the user's password (by himself)") {
        val requesterPassword = Password.unsafeFrom("test")
        val newPassword       = Password.unsafeFrom("test123456")
        userRestService(
          _.changePassword(normalUser)(normalUser.userIri, PasswordChangeRequest(requesterPassword, newPassword)),
        ) *>
          ZIO
            .serviceWithZIO[Authenticator](_.authenticate(normalUser.getUsername, newPassword.value))
            .map(actual => // check authenticate credentials with new password
              assertTrue(actual._1.userIri == normalUser.userIri),
            )
      },
      test("UPDATE the user's password (by a system admin)") {
        val requesterPassword = Password.unsafeFrom("test")
        val newPassword       = Password.unsafeFrom("test654321")
        userRestService(
          _.changePassword(rootUser)(normalUser.userIri, PasswordChangeRequest(requesterPassword, newPassword)),
        ) *>
          ZIO
            .serviceWithZIO[Authenticator](_.authenticate(normalUser.getUsername, newPassword.value))
            .map(actual => // check authenticate credentials with new password
              assertTrue(actual._1.userIri == normalUser.userIri),
            )
      },
      test("UPDATE the user's status, making them inactive ") {
        userRestService(
          _.changeStatus(rootUser)(normalUser.userIri, StatusChangeRequest(UserStatus.Inactive)),
        ).map(r => assertTrue(!r.user.status))
      },
      test("UPDATE the user's status, making them active ") {
        userRestService(
          _.changeStatus(rootUser)(normalUser.userIri, StatusChangeRequest(UserStatus.Active)),
        ).map(r => assertTrue(r.user.status))
      },
      test("UPDATE the user's system admin membership true") {
        userRestService(
          _.changeSystemAdmin(rootUser)(
            normalUser.userIri,
            SystemAdminChangeRequest(SystemAdmin.IsSystemAdmin),
          ),
        ).map(response1 => assertTrue(response1.user.permissions.isSystemAdmin))
      },
      test("UPDATE the user's system admin membership false") {
        val changeRequest = SystemAdminChangeRequest(SystemAdmin.IsNotSystemAdmin)
        userRestService(_.changeSystemAdmin(rootUser)(normalUser.userIri, changeRequest))
          .map(response2 => assertTrue(!response2.user.permissions.isSystemAdmin))
      },
    ),
    suite("asked to update the user's project membership")(
      test("ADD user to project") {
        for {
          membershipsBeforeUpdate <- userRestService(_.getProjectMemberShipsByUserIri(normalUser.userIri))
          _                       <- userRestService(_.addUserToProject(rootUser)(normalUser.userIri, imagesProject.id))
          membershipsAfterUpdate  <- userRestService(_.getProjectMemberShipsByUserIri(normalUser.userIri))
          received                <- projectRestService(_.getProjectMembersById(SystemUser)(imagesProject.id))
        } yield assertTrue(
          membershipsBeforeUpdate.projects.isEmpty,
          membershipsAfterUpdate.projects.map(_.id) == Seq(imagesProject.id),
          received.members.map(_.id).contains(normalUser.id),
        )
      },
      test("ADD user to project as project admin") {
        for {
          membershipsBeforeUpdate <- userRestService(_.getProjectMemberShipsByUserIri(normalUser.userIri))
          _                       <- userRestService(_.addUserToProject(rootUser)(normalUser.userIri, incunabulaProject.id))
          membershipsAfterUpdate  <- userRestService(_.getProjectMemberShipsByUserIri(normalUser.userIri))
          received                <- projectRestService(_.getProjectMembersById(SystemUser)(incunabulaProject.id))
        } yield assertTrue(
          membershipsBeforeUpdate.projects.map(_.id) == Seq(imagesProject.id),
          membershipsAfterUpdate.projects.map(_.id).sortBy(_.value) == Seq(imagesProject.id, incunabulaProject.id)
            .sortBy(_.value),
          received.members.map(_.id).contains(normalUser.id),
        )
      },
      test("DELETE user from project and also as project admin") {
        for {
          membershipsBeforeUpdate <- userRestService(_.getProjectMemberShipsByUserIri(normalUser.userIri))
          _                       <- userRestService(_.addUserToProjectAsAdmin(rootUser)(normalUser.userIri, imagesProject.id))
          projectAdminMembershipsBeforeUpdate <-
            userRestService(_.getProjectAdminMemberShipsByUserIri(normalUser.userIri))
          _                      <- userRestService(_.removeUserFromProject(rootUser)(normalUser.userIri, imagesProject.id))
          membershipsAfterUpdate <- userRestService(_.getProjectMemberShipsByUserIri(normalUser.userIri))
          projectAdminMembershipsAfterUpdate <-
            userRestService(_.getProjectAdminMemberShipsByUserIri(normalUser.userIri))
          received <- projectRestService(_.getProjectMembersById(rootUser)(imagesProject.id))
        } yield assertTrue(
          membershipsBeforeUpdate.projects.map(_.id).sortBy(_.value) == Seq(imagesProject.id, incunabulaProject.id)
            .sortBy(_.value),
          projectAdminMembershipsBeforeUpdate.projects.map(_.id) == Seq(imagesProject.id),
          membershipsAfterUpdate.projects.map(_.id) == Chunk(incunabulaProject.id),
          projectAdminMembershipsAfterUpdate.projects == Seq(),
          !received.members.contains(normalUser.ofType(UserInformationType.Restricted)),
        )
      },
    ),
    suite("asked to update the user's project admin group membership")(
      test("Not ADD user to project admin group if he is not a member of that project") {
        for {
          membershipsBeforeUpdate <- userRestService(_.getProjectAdminMemberShipsByUserIri(normalUser.userIri))
          _ <- ZIO
                 .fail(IllegalStateException("This test assumes the user is not a member of the project"))
                 .unless(membershipsBeforeUpdate.projects == Seq())
          exit <- userRestService(_.addUserToProjectAsAdmin(rootUser)(normalUser.userIri, imagesProject.id)).exit
        } yield assert(exit)(
          E2EZSpec.failsWithMessageEqualTo[BadRequestException](
            s"User ${normalUser.userIri} is not member of project ${imagesProject.id}.",
          ),
        )
      },
      test("ADD user to project admin group") {
        for {
          membershipsBeforeUpdate <- userRestService(_.getProjectAdminMemberShipsByUserIri(normalUser.userIri))
          _                       <- userRestService(_.addUserToProject(rootUser)(normalUser.userIri, imagesProject.id))
          _                       <- userRestService(_.addUserToProjectAsAdmin(rootUser)(normalUser.userIri, imagesProject.id))
          membershipsAfterUpdate <-
            userRestService(_.getProjectAdminMemberShipsByUserIri(normalUser.userIri))
          received <- projectRestService(_.getProjectAdminMembersById(rootUser)(imagesProject.id))
        } yield assertTrue(
          membershipsBeforeUpdate.projects == Seq(),
          membershipsAfterUpdate.projects.map(_.id) == Seq(imagesProject.id),
          received.members.map(_.id).contains(normalUser.id),
        )
      },
      test("DELETE user from project admin group") {
        for {
          membershipsBeforeUpdate <- userRestService(_.getProjectAdminMemberShipsByUserIri(normalUser.userIri))
          _                       <- userRestService(_.removeUserFromProjectAsAdmin(rootUser)(normalUser.userIri, imagesProject.id))
          membershipsAfterUpdate  <- userRestService(_.getProjectAdminMemberShipsByUserIri(normalUser.userIri))
          received                <- projectRestService(_.getProjectAdminMembersById(rootUser)(imagesProject.id))
        } yield assertTrue(
          membershipsBeforeUpdate.projects.map(_.id) == Seq(imagesProject.id),
          membershipsAfterUpdate.projects == Seq(),
          !received.members.contains(normalUser.ofType(UserInformationType.Restricted)),
        )
      },
    ),
    suite("asked to update the user's group membership")(
      test("ADD user to group") {
        for {
          membershipsBeforeUpdate <-
            userService(_.findUserByIri(normalUser.userIri)).map(_.map(_.groups).getOrElse(Seq.empty))
          _ <- userRestService(_.addUserToGroup(rootUser)(normalUser.userIri, imagesReviewerGroup.groupIri))
          membershipsAfterUpdate <-
            userService(_.findUserByIri(normalUser.userIri)).map(_.map(_.groups).getOrElse(Seq.empty))
          received <- groupRestService(_.getGroupMembers(rootUser)(imagesReviewerGroup.groupIri))
        } yield assertTrue(
          membershipsBeforeUpdate == Seq(),
          membershipsAfterUpdate.map(_.id) == Seq(imagesReviewerGroup.id),
          received.members.map(_.id).contains(normalUser.id),
        )
      },
      test("DELETE user from group") {
        for {
          membershipsBeforeUpdate <-
            userService(_.findUserByIri(normalUser.userIri).map(_.map(_.groups).getOrElse(Seq.empty)))
          _ <- userRestService(_.removeUserFromGroup(rootUser)(normalUser.userIri, imagesReviewerGroup.groupIri))
          membershipsAfterUpdate <-
            userService(_.findUserByIri(normalUser.userIri)).map(_.map(_.groups).getOrElse(Seq.empty))
          received <- groupRestService(_.getGroupMembers(rootUser)(imagesReviewerGroup.groupIri))
        } yield assertTrue(
          membershipsBeforeUpdate.map(_.id) == Seq(imagesReviewerGroup.id),
          membershipsAfterUpdate == Seq(),
          !received.members.map(_.id).contains(normalUser.id),
        )
      },
    ),
  )
}
