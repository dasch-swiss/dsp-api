/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.client4.UriContext
import sttp.model.StatusCode
import zio.ZIO
import zio.test.*

import dsp.valueobjects.LanguageCode
import org.knora.webapi.*
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM2
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.*
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.api.service.UserRestService.UserResponse
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.TokenResponse
import org.knora.webapi.testservices.ResponseOps
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestAdminApiClient
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.MutableTestIri

/**
 * End-to-End (E2E) test specification for testing admin users endpoints.
 */
object AdminUsersEndpointsE2ESpec extends E2EZSpec {

  private val projectAdminUser = imagesUser01
  private val multiUserIri     = UserIri.unsafeFrom(SharedTestDataADM2.multiuserUser.userData.user_id.get)

  private val customUserIri      = UserIri.unsafeFrom("http://rdfh.ch/users/14pxW-LAQIaGcCRiNCPJcQ")
  private val otherCustomUserIri = UserIri.unsafeFrom("http://rdfh.ch/users/v8_12VcJRlGNFCjYzqJ5cA")

  private val donaldIri = new MutableTestIri

  val e2eSpec = suite("The Users Route ('admin/users')")(
    suite("used to query user information [FUNCTIONALITY]")(
      test("return all users") {
        TestAdminApiClient
          .getAllUsers(rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return a single user profile identified by iri") {
        TestAdminApiClient
          .getUser(rootUser.userIri, rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return a single user profile identified by email") {
        TestAdminApiClient
          .getUserByEmail(rootUser.getEmail, rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return a single user profile identified by username") {
        TestAdminApiClient
          .getUserByUsername(rootUser.getUsername, rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
    ),
    suite("used to query user information [PERMISSIONS]")(
      test("return single user for SystemAdmin") {
        TestAdminApiClient
          .getUser(normalUser.userIri, rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return single user for itself") {
        TestAdminApiClient
          .getUser(normalUser.userIri, normalUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return only public information for single user for non SystemAdmin and self") {
        TestAdminApiClient
          .getUser(normalUser.userIri, projectAdminUser)
          .flatMap(_.assert200)
          .map(_.user)
          .flatMap(user =>
            assertTrue(
              user.givenName == normalUser.givenName,
              user.familyName == normalUser.familyName,
              !user.status,
              user.email == "",
              user.username == "",
            ),
          )
      },
      test("return only public information for single user with anonymous access") {
        TestAdminApiClient
          .getUser(normalUser.userIri)
          .flatMap(_.assert200)
          .map(_.user)
          .map(user =>
            assertTrue(
              user.givenName == normalUser.givenName,
              user.familyName == normalUser.familyName,
              !user.status,
              user.email == "",
              user.username == "",
            ),
          )
      },
      test("return all users for SystemAdmin") {
        TestAdminApiClient
          .getAllUsers(rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return all users for ProjectAdmin") {
        TestAdminApiClient
          .getAllUsers(projectAdminUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return 'Forbidden' for all users for normal user") {
        TestAdminApiClient
          .getAllUsers(normalUser)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
    ),
    suite("given a custom Iri")(
      test("given no credentials in the request when creating a user it must be forbidden") {
        val createUserRequest = UserCreateRequest(
          id = Some(customUserIri),
          username = Username.unsafeFrom("userWithCustomIri"),
          email = Email.unsafeFrom("userWithCustomIri@example.org"),
          givenName = GivenName.unsafeFrom("a user"),
          familyName = FamilyName.unsafeFrom("with a custom Iri"),
          password = Password.unsafeFrom("test"),
          status = UserStatus.Active,
          lang = LanguageCode.en,
          systemAdmin = SystemAdmin.IsNotSystemAdmin,
        )
        TestApiClient
          .postJson[UserResponse, UserCreateRequest](uri"/admin/users", createUserRequest)
          .map(response => assertTrue(response.code == StatusCode.Unauthorized))
      },
      test("create a user with the provided custom IRI") {
        val createUserRequest = UserCreateRequest(
          id = Some(customUserIri),
          username = Username.unsafeFrom("userWithCustomIri"),
          email = Email.unsafeFrom("userWithCustomIri@example.org"),
          givenName = GivenName.unsafeFrom("a user"),
          familyName = FamilyName.unsafeFrom("with a custom Iri"),
          password = Password.unsafeFrom("test"),
          status = UserStatus.Active,
          lang = LanguageCode.en,
          systemAdmin = SystemAdmin.IsNotSystemAdmin,
        )
        TestAdminApiClient
          .createUser(createUserRequest, rootUser)
          .flatMap(_.assert200)
          .map(result => assertTrue(result.user.id == customUserIri.value))
      },
      test("return 'BadRequest' if the supplied IRI for the user is not unique") {
        val createUserRequest = UserCreateRequest(
          id = Some(customUserIri),
          username = Username.unsafeFrom("userWithDuplicateCustomIri"),
          email = Email.unsafeFrom("userWithDuplicateCustomIri@example.org"),
          givenName = GivenName.unsafeFrom("a user"),
          familyName = FamilyName.unsafeFrom("with a duplicate custom Iri"),
          password = Password.unsafeFrom("test"),
          status = UserStatus.Active,
          lang = LanguageCode.en,
          systemAdmin = SystemAdmin.IsNotSystemAdmin,
        )
        TestAdminApiClient
          .createUser(createUserRequest, rootUser)
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
    ),
    suite("dealing with special characters")(
      test("escape special characters when creating the user") {
        val createUserRequest = UserCreateRequest(
          id = Some(otherCustomUserIri),
          username = Username.unsafeFrom("userWithApostrophe"),
          email = Email.unsafeFrom("userWithApostrophe@example.org"),
          givenName = GivenName.unsafeFrom("M\"Given 'Name"),
          familyName = FamilyName.unsafeFrom("M\tFamily Name"),
          password = Password.unsafeFrom("test"),
          status = UserStatus.Active,
          lang = LanguageCode.en,
          systemAdmin = SystemAdmin.IsNotSystemAdmin,
        )
        TestAdminApiClient
          .createUser(createUserRequest, rootUser)
          .flatMap(_.assert200)
          .flatMap(result =>
            assertTrue(
              result.user.id == otherCustomUserIri.value,
              result.user.givenName == "M\"Given 'Name",
              result.user.familyName == "M\tFamily Name",
            ),
          )
      },
      test("escape special characters when updating the user") {
        val updateUserRequest = BasicUserInformationChangeRequest(
          givenName = Some(GivenName.unsafeFrom("Updated\tGivenName")),
          familyName = Some(FamilyName.unsafeFrom("Updated\"FamilyName")),
        )
        TestAdminApiClient
          .updateUserBasicInfo(otherCustomUserIri, updateUserRequest, rootUser)
          .flatMap(_.assert200)
          .map(result =>
            assertTrue(
              result.user.givenName == "Updated\tGivenName",
              result.user.familyName == "Updated\"FamilyName",
            ),
          )
      },
      test(
        "return the special characters correctly when getting a user with special characters in givenName and familyName",
      ) {
        TestAdminApiClient
          .getUser(otherCustomUserIri, rootUser)
          .flatMap(_.assert200)
          .map(result =>
            assertTrue(
              result.user.givenName == "Updated\tGivenName",
              result.user.familyName == "Updated\"FamilyName",
            ),
          )
      },
    ),
    suite("used to create a user")(
      test("not allow a projectAdmin to create a System Admin") {
        val createUserRequest = UserCreateRequest(
          username = Username.unsafeFrom("daisy.duck"),
          email = Email.unsafeFrom("daisy.duck@example.org"),
          givenName = GivenName.unsafeFrom("Daisy"),
          familyName = FamilyName.unsafeFrom("Duck"),
          password = Password.unsafeFrom("test"),
          status = UserStatus.Active,
          lang = LanguageCode.en,
          systemAdmin = SystemAdmin.IsSystemAdmin,
        )
        TestAdminApiClient
          .createUser(createUserRequest, projectAdminUser)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
      test("create the user if the supplied email and username are unique") {
        val createUserRequest = UserCreateRequest(
          username = Username.unsafeFrom("donald.duck"),
          email = Email.unsafeFrom("donald.duck@example.org"),
          givenName = GivenName.unsafeFrom("Donald"),
          familyName = FamilyName.unsafeFrom("Duck"),
          password = Password.unsafeFrom("test"),
          status = UserStatus.Active,
          lang = LanguageCode.en,
          systemAdmin = SystemAdmin.IsNotSystemAdmin,
        )
        TestAdminApiClient
          .createUser(createUserRequest, rootUser)
          .flatMap(_.assert200)
          .tap(result => ZIO.succeed(donaldIri.set(result.user.id)))
          .map(result =>
            assertTrue(
              result.user.username == "donald.duck",
              result.user.email == "donald.duck@example.org",
              result.user.givenName == "Donald",
              result.user.familyName == "Duck",
              result.user.lang == "en",
              result.user.status,
            ),
          )
      },
      test("return a 'BadRequest' if the supplied username is not unique") {
        val createUserRequest = UserCreateRequest(
          username = Username.unsafeFrom("donald.duck"),
          email = Email.unsafeFrom("new.donald.duck@example.org"),
          givenName = GivenName.unsafeFrom("NewDonald"),
          familyName = FamilyName.unsafeFrom("NewDuck"),
          password = Password.unsafeFrom("test"),
          status = UserStatus.Active,
          lang = LanguageCode.en,
          systemAdmin = SystemAdmin.IsNotSystemAdmin,
        )
        TestAdminApiClient
          .createUser(createUserRequest, rootUser)
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("return a 'BadRequest' if the supplied email is not unique") {
        val createUserRequest = UserCreateRequest(
          username = Username.unsafeFrom("new.donald.duck"),
          email = Email.unsafeFrom("donald.duck@example.org"),
          givenName = GivenName.unsafeFrom("NewDonald"),
          familyName = FamilyName.unsafeFrom("NewDuck"),
          password = Password.unsafeFrom("test"),
          status = UserStatus.Active,
          lang = LanguageCode.en,
          systemAdmin = SystemAdmin.IsNotSystemAdmin,
        )
        TestAdminApiClient
          .createUser(createUserRequest, rootUser)
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("authenticate the newly created user using HttpBasicAuth") {
        TestApiClient
          .getJson[zio.json.ast.Json](
            uri"/v2/authentication",
            _.auth.basic("donald.duck@example.org", "test"),
          )
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("authenticate the newly created user during login") {
        TestApiClient
          .postJson[TokenResponse, LoginPayload](
            uri"/v2/authentication",
            LoginPayload.EmailPassword(Email.unsafeFrom("donald.duck@example.org"), "test"),
          )
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
    ),
    suite("used to modify user information")(
      test("update the user's basic information") {
        val updateUserRequest = BasicUserInformationChangeRequest(
          username = Some(Username.unsafeFrom("donald.big.duck")),
          email = Some(Email.unsafeFrom("donald.big.duck@example.org")),
          givenName = Some(GivenName.unsafeFrom("Big Donald")),
          familyName = Some(FamilyName.unsafeFrom("Duckmann")),
          lang = Some(LanguageCode.de),
        )
        TestAdminApiClient
          .updateUserBasicInfo(donaldIri.asUserIri, updateUserRequest, rootUser)
          .flatMap(_.assert200)
          .map(result =>
            assertTrue(
              result.user.username == "donald.big.duck",
              result.user.email == "donald.big.duck@example.org",
              result.user.givenName == "Big Donald",
              result.user.familyName == "Duckmann",
              result.user.lang == "de",
            ),
          )
      },
      test("return 'Forbidden' when updating another user's password if a requesting user is not a SystemAdmin") {
        val changeUserPasswordRequest = PasswordChangeRequest(
          requesterPassword = Password.unsafeFrom("test"),
          newPassword = Password.unsafeFrom("will-be-ignored"),
        )
        TestAdminApiClient
          .updateUserPassword(customUserIri, changeUserPasswordRequest, normalUser)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
      test("update the user's password (by himself)") {
        val changeUserPasswordRequest = PasswordChangeRequest(
          requesterPassword = Password.unsafeFrom("test"),
          newPassword = Password.unsafeFrom("test123456"),
        )
        for {
          _ <- TestAdminApiClient
                 .updateUserPassword(normalUser.userIri, changeUserPasswordRequest, normalUser)
                 .flatMap(_.assert200)
          // check if the password was changed, i.e. if the new one is accepted
          response <- TestApiClient.getJson[zio.json.ast.Json](
                        uri"/v2/authentication",
                        _.auth.basic(normalUser.email, "test123456"),
                      )
        } yield assertTrue(response.code == StatusCode.Ok)
      },
      test("update the user's password (by a system admin)") {
        val changeUserPasswordRequest = PasswordChangeRequest(
          requesterPassword = Password.unsafeFrom("test"),
          newPassword = Password.unsafeFrom("test654321"),
        )
        for {
          _ <- TestAdminApiClient
                 .updateUserPassword(normalUser.userIri, changeUserPasswordRequest, rootUser)
                 .flatMap(_.assert200)
          // check if the password was changed, i.e. if the new one is accepted
          response <- TestApiClient.getJson[zio.json.ast.Json](
                        uri"/v2/authentication",
                        _.auth.basic(normalUser.email, "test654321"),
                      )
        } yield assertTrue(response.code == StatusCode.Ok)
      },
      test("return 'BadRequest' if new password in change password request is missing") {
        val changeUserPasswordRequest: String =
          s"""{
             |    "requesterPassword": "test"
             |}""".stripMargin
        for {
          response1 <- TestApiClient.putJson[zio.json.ast.Json, String](
                         uri"/admin/users/iri/${normalUser.userIri}/Password",
                         changeUserPasswordRequest,
                         normalUser,
                       )
          // check that the password was not changed, i.e. the old one is still accepted
          response2 <- TestApiClient.getJson[zio.json.ast.Json](
                         uri"/v2/authentication",
                         _.auth.basic(normalUser.email, "test654321"),
                       )
        } yield assertTrue(
          response1.code == StatusCode.BadRequest,
          response2.code == StatusCode.Ok,
        )
      },
      test("change user's status") {
        TestAdminApiClient
          .updateUserStatus(donaldIri.asUserIri, StatusChangeRequest(UserStatus.Inactive), rootUser)
          .flatMap(_.assert200)
          .map(result => assertTrue(!result.user.status))
      },
      test("update the user's system admin membership status") {
        val changeReq = SystemAdminChangeRequest(SystemAdmin.IsSystemAdmin)
        for {
          response <- TestAdminApiClient.updateUserSystemAdmin(donaldIri.asUserIri, changeReq, rootUser)
          result   <- response.assert200
          // Throw BadRequest exception if user is built-in user
          badResponse <- TestAdminApiClient.updateUserSystemAdmin(
                           KnoraSystemInstances.Users.SystemUser.userIri,
                           changeReq,
                           rootUser,
                         )
        } yield assertTrue(
          result.user.permissions.groupsPerProject
            .get("http://www.knora.org/ontology/knora-admin#SystemProject")
            .head == List("http://www.knora.org/ontology/knora-admin#SystemAdmin"),
          badResponse.code == StatusCode.BadRequest,
        )
      },
      test("not allow updating the system user's system admin membership status") {
        TestAdminApiClient
          .updateUserSystemAdmin(
            KnoraSystemInstances.Users.SystemUser.userIri,
            SystemAdminChangeRequest(SystemAdmin.IsSystemAdmin),
            rootUser,
          )
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("not allow changing the system user's status") {
        TestAdminApiClient
          .updateUserStatus(
            KnoraSystemInstances.Users.SystemUser.userIri,
            StatusChangeRequest(UserStatus.Inactive),
            rootUser,
          )
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("not allow changing the anonymous user's status") {
        TestAdminApiClient
          .updateUserStatus(
            UserIri.unsafeFrom(KnoraSystemInstances.Users.AnonymousUser.id),
            StatusChangeRequest(UserStatus.Inactive),
            rootUser,
          )
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("delete a user") {
        TestAdminApiClient
          .deleteUser(customUserIri, rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("not allow deleting the system user") {
        TestAdminApiClient
          .deleteUser(KnoraSystemInstances.Users.SystemUser.userIri, rootUser)
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("not allow deleting the anonymous user") {
        TestAdminApiClient
          .deleteUser(UserIri.unsafeFrom(KnoraSystemInstances.Users.AnonymousUser.id), rootUser)
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
    ),
    suite("used to query project memberships")(
      test("return all projects the user is a member of") {
        TestAdminApiClient
          .getUserProjectMemberships(multiUserIri, rootUser)
          .flatMap(_.assert200)
          .map(result =>
            assertTrue(
              result.projects.contains(imagesProjectExternal),
              result.projects.contains(incunabulaProjectExternal),
              result.projects.contains(anythingProjectExternal),
            ),
          )
      },
    ),
    suite("used to modify project membership")(
      test("NOT add a user to project if the requesting user is not a SystemAdmin or ProjectAdmin") {
        TestAdminApiClient
          .addUserToProject(normalUser.userIri, imagesProjectIri, normalUser)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
      test("add user to project") {
        for {
          beforeResponse <- TestAdminApiClient.getUserProjectMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
          beforeResult   <- beforeResponse.assert200
          _              <- TestAdminApiClient.addUserToProject(normalUser.userIri, imagesProjectIri, rootUser).flatMap(_.assert200)
          afterResponse  <- TestAdminApiClient.getUserProjectMemberships(normalUser.userIri, rootUser)
          afterResult    <- afterResponse.assert200
        } yield assertTrue(
          beforeResult.projects == Seq.empty,
          afterResult.projects == Seq(SharedTestDataADM.imagesProjectExternal),
        )
      },
      test("don't add user to project if user is already a member") {
        for {
          beforeResponse <- TestAdminApiClient.getUserProjectMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
          beforeResult   <- beforeResponse.assert200
          response       <- TestAdminApiClient.addUserToProject(normalUser.userIri, imagesProjectIri, rootUser)
          afterResponse  <- TestAdminApiClient.getUserProjectMemberships(normalUser.userIri, rootUser)
          afterResult    <- afterResponse.assert200
        } yield assertTrue(
          response.code == StatusCode.BadRequest,
          afterResult.projects == beforeResult.projects,
        )
      },
      test("remove user from project") {
        for {
          beforeResponse <- TestAdminApiClient.getUserProjectMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
          beforeResult   <- beforeResponse.assert200
          response       <- TestAdminApiClient.removeUserFromProject(normalUser.userIri, imagesProjectIri, rootUser)
          afterResponse  <- TestAdminApiClient.getUserProjectMemberships(normalUser.userIri, rootUser)
          afterResult    <- afterResponse.assert200
        } yield assertTrue(
          beforeResult.projects == Seq(SharedTestDataADM.imagesProjectExternal),
          response.code == StatusCode.Ok,
          afterResult.projects == Seq.empty[Project],
        )
      },
    ),
    suite("used to query project admin group memberships")(
      test("return all projects the user is a member of the project admin group") {
        TestAdminApiClient
          .getUserProjectAdminMemberships(multiUserIri, rootUser)
          .flatMap(_.assert200)
          .map(result =>
            assertTrue(
              result.projects.contains(imagesProjectExternal) &&
                result.projects.contains(incunabulaProjectExternal) &&
                result.projects.contains(anythingProjectExternal),
            ),
          )
      },
    ),
    suite("used to modify project admin group membership")(
      test("add user to project admin group only if he is already member of that project") {
        for {
          // add user as project admin to images project - returns a BadRequest because user is not member of the project
          responseWithoutBeingMember <- TestAdminApiClient.addUserToProjectAdmin(
                                          normalUser.userIri,
                                          imagesProjectIri,
                                          rootUser,
                                        )
          // add user as member to images project
          responseAddUserToProject <- TestAdminApiClient.addUserToProject(
                                        normalUser.userIri,
                                        imagesProjectIri,
                                        rootUser,
                                      )
          // verify that user is not yet project admin in images project
          membershipsBeforeResponse <-
            TestAdminApiClient.getUserProjectAdminMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
          membershipsBeforeResult <- membershipsBeforeResponse.assert200
          // add user as project admin to images project
          response <- TestAdminApiClient.addUserToProjectAdmin(
                        normalUser.userIri,
                        imagesProjectIri,
                        rootUser,
                      )
          // verify that user has been added as project admin to images project
          membershipsAfterResponse <-
            TestAdminApiClient.getUserProjectAdminMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
          membershipsAfterResult <- membershipsAfterResponse.assert200
        } yield assertTrue(
          responseWithoutBeingMember.code == StatusCode.BadRequest,
          responseAddUserToProject.code == StatusCode.Ok,
          membershipsBeforeResult.projects == Seq(),
          response.code == StatusCode.Ok,
          membershipsAfterResult.projects == Seq(SharedTestDataADM.imagesProjectExternal),
        )
      },
      test("remove user from project admin group") {
        for {
          membershipsBeforeResponse <-
            TestAdminApiClient.getUserProjectAdminMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
          membershipsBeforeResult <- membershipsBeforeResponse.assert200
          response <- TestAdminApiClient.removeUserFromProjectAdmin(
                        normalUser.userIri,
                        imagesProjectIri,
                        rootUser,
                      )
          membershipsAfterResponse <-
            TestAdminApiClient.getUserProjectAdminMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
          membershipsAfterResult <- membershipsAfterResponse.assert200
        } yield assertTrue(
          membershipsBeforeResult.projects == Seq(SharedTestDataADM.imagesProjectExternal),
          response.code == StatusCode.Ok,
          membershipsAfterResult.projects == Seq.empty[Project],
        )
      },
      test("remove user from project which also removes him from project admin group") {
        for {
          // add user as project admin to images project
          responseAddUserAsProjectAdmin <- TestAdminApiClient.addUserToProjectAdmin(
                                             normalUser.userIri,
                                             imagesProjectIri,
                                             rootUser,
                                           )
          // verify that user has been added as project admin to images project
          membershipsBeforeResponse <-
            TestAdminApiClient.getUserProjectAdminMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
          membershipsBeforeResult <- membershipsBeforeResponse.assert200
          // remove user as project member from images project
          response <- TestAdminApiClient.removeUserFromProject(
                        normalUser.userIri,
                        imagesProjectIri,
                        rootUser,
                      )
          // verify that user has also been removed as project admin from images project
          projectAdminMembershipsAfterResponse <-
            TestAdminApiClient.getUserProjectAdminMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
          projectAdminMembershipsAfterResult <- projectAdminMembershipsAfterResponse.assert200
        } yield assertTrue(
          responseAddUserAsProjectAdmin.code == StatusCode.Ok,
          membershipsBeforeResult.projects == Seq(SharedTestDataADM.imagesProjectExternal),
          response.code == StatusCode.Ok,
          projectAdminMembershipsAfterResult.projects == Seq(),
        )
      },
    ),
    suite("used to query group memberships")(
      test("return all groups the user is a member of") {
        TestAdminApiClient
          .getUserGroupMemberships(multiUserIri, rootUser)
          .flatMap(_.assert200)
          .map(result =>
            assertTrue(
              result.groups.contains(SharedTestDataADM.imagesReviewerGroupExternal),
            ),
          )
      },
    ),
    suite("used to modify group membership")(
      test("add user to group") {
        for {
          membershipsBeforeResponse <-
            TestAdminApiClient.getUserGroupMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
          membershipsBeforeResult <- membershipsBeforeResponse.assert200
          response <- TestAdminApiClient.addUserToGroup(
                        normalUser.userIri,
                        imagesReviewerGroup.groupIri,
                        rootUser,
                      )
          membershipsAfterResponse <- TestAdminApiClient.getUserGroupMemberships(normalUser.userIri, rootUser)
          membershipsAfterResult   <- membershipsAfterResponse.assert200
        } yield assertTrue(
          membershipsBeforeResult.groups == Seq.empty[Group],
          response.code == StatusCode.Ok,
          membershipsAfterResult.groups == Seq(SharedTestDataADM.imagesReviewerGroupExternal),
        )
      },
      test("remove user from group") {
        for {
          membershipsBeforeResponse <-
            TestAdminApiClient.getUserGroupMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
          membershipsBeforeResult <- membershipsBeforeResponse.assert200
          response <- TestAdminApiClient.removeUserFromGroup(
                        normalUser.userIri,
                        imagesReviewerGroup.groupIri,
                        rootUser,
                      )
          membershipsAfterResponse <- TestAdminApiClient.getUserGroupMemberships(normalUser.userIri, rootUser)
          membershipsAfterResult   <- membershipsAfterResponse.assert200
        } yield assertTrue(
          membershipsBeforeResult.groups == Seq(SharedTestDataADM.imagesReviewerGroupExternal),
          response.code == StatusCode.Ok,
          membershipsAfterResult.groups == Seq.empty[Group],
        )
      },
    ),
  )
}
