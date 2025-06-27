/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import sttp.client4.UriContext
import sttp.model.StatusCode

import dsp.valueobjects.LanguageCode
import org.knora.webapi.*
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM2
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.*
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.api.service.UserRestService.UserResponse
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.Group
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
class AdminUsersEndpointsE2ESpec extends E2ESpec {

  private val projectAdminUser = imagesUser01
  private val multiUserIri     = SharedTestDataADM2.multiuserUser.userData.user_id.get

  private val customUserIri      = UserIri.unsafeFrom("http://rdfh.ch/users/14pxW-LAQIaGcCRiNCPJcQ")
  private val otherCustomUserIri = UserIri.unsafeFrom("http://rdfh.ch/users/v8_12VcJRlGNFCjYzqJ5cA")

  private val donaldIri = new MutableTestIri

  "The Users Route ('admin/users')" when {
    "used to query user information [FUNCTIONALITY]" should {
      "return all users" in {
        val response = UnsafeZioRun.runOrThrow(TestAdminApiClient.getAllUsers(rootUser))
        response.code should be(StatusCode.Ok)
      }

      "return a single user profile identified by iri" in {
        val response = UnsafeZioRun.runOrThrow(TestAdminApiClient.getUser(rootUser.userIri, rootUser))
        response.code should be(StatusCode.Ok)
      }

      "return a single user profile identified by email" in {
        val response = UnsafeZioRun.runOrThrow(TestAdminApiClient.getUserByEmail(rootUser.getEmail, rootUser))
        response.code should be(StatusCode.Ok)
      }

      "return a single user profile identified by username" in {
        val response = UnsafeZioRun.runOrThrow(TestAdminApiClient.getUserByUsername(rootUser.getUsername, rootUser))
        response.code should be(StatusCode.Ok)
      }
    }

    "used to query user information [PERMISSIONS]" should {
      "return single user for SystemAdmin" in {
        val response = UnsafeZioRun.runOrThrow(TestAdminApiClient.getUser(normalUser.userIri, rootUser))
        response.code should be(StatusCode.Ok)
      }

      "return single user for itself" in {
        val response = UnsafeZioRun.runOrThrow(TestAdminApiClient.getUser(normalUser.userIri, normalUser))
        response.code should be(StatusCode.Ok)
      }

      "return only public information for single user for non SystemAdmin and self" in {
        val result =
          UnsafeZioRun.runOrThrow(TestAdminApiClient.getUser(normalUser.userIri, projectAdminUser).flatMap(_.assert200))

        result.user.givenName should be(normalUser.givenName)
        result.user.familyName should be(normalUser.familyName)
        result.user.status should be(false)
        result.user.email should be("")
        result.user.username should be("")
      }

      "return only public information for single user with anonymous access" in {
        val result = UnsafeZioRun.runOrThrow(TestAdminApiClient.getUser(normalUser.userIri).flatMap(_.assert200))

        result.user.givenName should be(normalUser.givenName)
        result.user.familyName should be(normalUser.familyName)
        result.user.status should be(false)
        result.user.email should be("")
        result.user.username should be("")
      }

      "return all users for SystemAdmin" in {
        val response = UnsafeZioRun.runOrThrow(TestAdminApiClient.getAllUsers(rootUser))
        response.code should be(StatusCode.Ok)
      }

      "return all users for ProjectAdmin" in {
        val response = UnsafeZioRun.runOrThrow(TestAdminApiClient.getAllUsers(projectAdminUser))
        response.code should be(StatusCode.Ok)
      }

      "return 'Forbidden' for all users for normal user" in {
        val response = UnsafeZioRun.runOrThrow(TestAdminApiClient.getAllUsers(normalUser))
        response.code should be(StatusCode.Forbidden)
      }

    }

    "given a custom Iri" should {
      "given no credentials in the request when creating a user it must be forbidden" in {
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

        val response = UnsafeZioRun.runOrThrow(
          TestApiClient.postJson[UserResponse, UserCreateRequest](uri"/admin/users", createUserRequest),
        )
        response.code should be(StatusCode.Unauthorized)
      }

      "create a user with the provided custom IRI" in {
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

        val result =
          UnsafeZioRun.runOrThrow(TestAdminApiClient.createUser(createUserRequest, rootUser).flatMap(_.assert200))

        result.user.id should be(customUserIri.value)
      }

      "return 'BadRequest' if the supplied IRI for the user is not unique" in {
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

        val response = UnsafeZioRun.runOrThrow(TestAdminApiClient.createUser(createUserRequest, rootUser))

        response.code should be(StatusCode.BadRequest)
      }
    }

    "dealing with special characters" should {
      "escape special characters when creating the user" in {
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

        val result =
          UnsafeZioRun.runOrThrow(TestAdminApiClient.createUser(createUserRequest, rootUser).flatMap(_.assert200))

        // check that the special characters were escaped correctly
        result.user.id should equal(otherCustomUserIri.value)
        result.user.givenName should equal("M\"Given 'Name")
        result.user.familyName should equal("M\tFamily Name")
      }

      "escape special characters when updating the user" in {
        val updateUserRequest = BasicUserInformationChangeRequest(
          givenName = Some(GivenName.unsafeFrom("Updated\tGivenName")),
          familyName = Some(FamilyName.unsafeFrom("Updated\"FamilyName")),
        )

        val result = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.updateUserBasicInfo(otherCustomUserIri, updateUserRequest, rootUser).flatMap(_.assert200),
        )

        result.user.givenName should be("Updated\tGivenName")
        result.user.familyName should be("Updated\"FamilyName")
      }

      "return the special characters correctly when getting a user with special characters in givenName and familyName" in {
        val result =
          UnsafeZioRun.runOrThrow(TestAdminApiClient.getUser(otherCustomUserIri, rootUser).flatMap(_.assert200))

        result.user.givenName should be("Updated\tGivenName")
        result.user.familyName should be("Updated\"FamilyName")
      }
    }

    "used to create a user" should {
      "not allow a projectAdmin to create a System Admin" in {
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

        val response = UnsafeZioRun.runOrThrow(TestAdminApiClient.createUser(createUserRequest, projectAdminUser))

        response.code should be(StatusCode.Forbidden)
      }

      "create the user if the supplied email and username are unique" in {
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

        val result =
          UnsafeZioRun.runOrThrow(TestAdminApiClient.createUser(createUserRequest, rootUser).flatMap(_.assert200))

        result.user.username should be("donald.duck")
        result.user.email should be("donald.duck@example.org")
        result.user.givenName should be("Donald")
        result.user.familyName should be("Duck")
        result.user.status should be(true)
        result.user.lang should be("en")

        donaldIri.set(result.user.id)
      }

      "return a 'BadRequest' if the supplied username is not unique" in {
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

        val response = UnsafeZioRun.runOrThrow(TestAdminApiClient.createUser(createUserRequest, rootUser))
        response.code should be(StatusCode.BadRequest)
      }

      "return a 'BadRequest' if the supplied email is not unique" in {
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

        val response = UnsafeZioRun.runOrThrow(TestAdminApiClient.createUser(createUserRequest, rootUser))
        response.code should be(StatusCode.BadRequest)
      }

      "authenticate the newly created user using HttpBasicAuth" in {
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient.getJson[zio.json.ast.Json](
            uri"/v2/authentication",
            _.auth.basic("donald.duck@example.org", "test"),
          ),
        )
        response.code should be(StatusCode.Ok)
      }

      "authenticate the newly created user during login" in {
        val response = UnsafeZioRun.runOrThrow(
          TestApiClient.postJson[TokenResponse, LoginPayload](
            uri"/v2/authentication",
            LoginPayload.EmailPassword(Email.unsafeFrom("donald.duck@example.org"), "test"),
          ),
        )
        response.code should be(StatusCode.Ok)
      }
    }

    "used to modify user information" should {
      "update the user's basic information" in {
        val updateUserRequest = BasicUserInformationChangeRequest(
          username = Some(Username.unsafeFrom("donald.big.duck")),
          email = Some(Email.unsafeFrom("donald.big.duck@example.org")),
          givenName = Some(GivenName.unsafeFrom("Big Donald")),
          familyName = Some(FamilyName.unsafeFrom("Duckmann")),
          lang = Some(LanguageCode.de),
        )

        val result = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .updateUserBasicInfo(UserIri.unsafeFrom(donaldIri.get), updateUserRequest, rootUser)
            .flatMap(_.assert200),
        )

        result.user.username should be("donald.big.duck")
        result.user.email should be("donald.big.duck@example.org")
        result.user.givenName should be("Big Donald")
        result.user.familyName should be("Duckmann")
        result.user.lang should be("de")
      }

      "return 'Forbidden' when updating another user's password if a requesting user is not a SystemAdmin" in {
        val changeUserPasswordRequest = PasswordChangeRequest(
          requesterPassword = Password.unsafeFrom("test"),
          newPassword = Password.unsafeFrom("will-be-ignored"),
        )

        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.updateUserPassword(customUserIri, changeUserPasswordRequest, normalUser),
        )
        response.code should be(StatusCode.Forbidden)
      }

      "update the user's password (by himself)" in {
        val changeUserPasswordRequest = PasswordChangeRequest(
          requesterPassword = Password.unsafeFrom("test"),
          newPassword = Password.unsafeFrom("test123456"),
        )

        val _ = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .updateUserPassword(normalUser.userIri, changeUserPasswordRequest, normalUser)
            .flatMap(_.assert200),
        )

        // check if the password was changed, i.e. if the new one is accepted
        val response2 = UnsafeZioRun.runOrThrow(
          TestApiClient.getJson[zio.json.ast.Json](
            uri"/v2/authentication",
            _.auth.basic(normalUser.email, "test123456"),
          ),
        )
        response2.code should be(StatusCode.Ok)
      }

      "update the user's password (by a system admin)" in {
        val changeUserPasswordRequest = PasswordChangeRequest(
          requesterPassword = Password.unsafeFrom("test"),
          newPassword = Password.unsafeFrom("test654321"),
        )

        val _ = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .updateUserPassword(normalUser.userIri, changeUserPasswordRequest, rootUser)
            .flatMap(_.assert200),
        )

        // check if the password was changed, i.e. if the new one is accepted
        val response2 = UnsafeZioRun.runOrThrow(
          TestApiClient.getJson[zio.json.ast.Json](
            uri"/v2/authentication",
            _.auth.basic(normalUser.email, "test654321"),
          ),
        )
        response2.code should be(StatusCode.Ok)
      }

      "return 'BadRequest' if new password in change password request is missing" in {
        val changeUserPasswordRequest: String =
          s"""{
             |    "requesterPassword": "test"
             |}""".stripMargin

        val response1 = UnsafeZioRun.runOrThrow(
          TestApiClient.putJson[zio.json.ast.Json, String](
            uri"/admin/users/iri/${normalUser.userIri}/Password",
            changeUserPasswordRequest,
            normalUser,
          ),
        )

        response1.code should be(StatusCode.BadRequest)

        // check that the password was not changed, i.e. the old one is still accepted
        val response2 = UnsafeZioRun.runOrThrow(
          TestApiClient.getJson[zio.json.ast.Json](
            uri"/v2/authentication",
            _.auth.basic(normalUser.email, "test654321"),
          ),
        )
        response2.code should be(StatusCode.Ok)
      }

      "change user's status" in {
        val changeUserStatusRequest = StatusChangeRequest(UserStatus.Inactive)

        val result = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .updateUserStatus(UserIri.unsafeFrom(donaldIri.get), changeUserStatusRequest, rootUser)
            .flatMap(_.assert200),
        )

        result.user.status should be(false)
      }

      "update the user's system admin membership status" in {
        val changeReq = SystemAdminChangeRequest(SystemAdmin.IsSystemAdmin)

        val result = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .updateUserSystemAdmin(UserIri.unsafeFrom(donaldIri.get), changeReq, rootUser)
            .flatMap(_.assert200),
        )

        result.user.permissions.groupsPerProject
          .get("http://www.knora.org/ontology/knora-admin#SystemProject")
          .head should equal(List("http://www.knora.org/ontology/knora-admin#SystemAdmin"))

        // Throw BadRequest exception if user is built-in user
        val badResponse = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.updateUserSystemAdmin(
            KnoraSystemInstances.Users.SystemUser.userIri,
            changeReq,
            rootUser,
          ),
        )
        badResponse.code should be(StatusCode.BadRequest)
      }

      "not allow updating the system user's system admin membership status" in {
        val changeReq = SystemAdminChangeRequest(SystemAdmin.IsSystemAdmin)

        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.updateUserSystemAdmin(KnoraSystemInstances.Users.SystemUser.userIri, changeReq, rootUser),
        )
        response.code should be(StatusCode.BadRequest)
      }

      "not allow changing the system user's status" in {
        val changeUserStatusRequest = StatusChangeRequest(UserStatus.Inactive)

        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.updateUserStatus(
            KnoraSystemInstances.Users.SystemUser.userIri,
            changeUserStatusRequest,
            rootUser,
          ),
        )
        response.code should be(StatusCode.BadRequest)
      }

      "not allow changing the anonymous user's status" in {
        val changeUserStatusRequest = StatusChangeRequest(UserStatus.Inactive)

        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.updateUserStatus(
            UserIri.unsafeFrom(KnoraSystemInstances.Users.AnonymousUser.id),
            changeUserStatusRequest,
            rootUser,
          ),
        )
        response.code should be(StatusCode.BadRequest)
      }

      "delete a user" in {
        val response = UnsafeZioRun.runOrThrow(TestAdminApiClient.deleteUser(customUserIri, rootUser))
        response.code should be(StatusCode.Ok)
      }

      "not allow deleting the system user" in {
        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.deleteUser(KnoraSystemInstances.Users.SystemUser.userIri, rootUser),
        )
        response.code should be(StatusCode.BadRequest)
      }

      "not allow deleting the anonymous user" in {
        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.deleteUser(UserIri.unsafeFrom(KnoraSystemInstances.Users.AnonymousUser.id), rootUser),
        )
        response.code should be(StatusCode.BadRequest)
      }
    }

    "used to query project memberships" should {
      "return all projects the user is a member of" in {
        val projects = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserProjectMemberships(UserIri.unsafeFrom(multiUserIri), rootUser)
            .flatMap(_.assert200)
            .map(_.projects),
        )
        projects should contain allElementsOf Seq(
          imagesProjectExternal,
          incunabulaProjectExternal,
          anythingProjectExternal,
        )
      }
    }

    "used to modify project membership" should {
      "NOT add a user to project if the requesting user is not a SystemAdmin or ProjectAdmin" in {
        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.addUserToProject(
            normalUser.userIri,
            imagesProjectIri,
            normalUser,
          ),
        )
        response.code should be(StatusCode.Forbidden)
      }

      "add user to project" in {
        val membershipsBeforeUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserProjectMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
            .flatMap(_.assert200)
            .map(_.projects),
        )
        membershipsBeforeUpdate should equal(Seq())

        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.addUserToProject(
            normalUser.userIri,
            imagesProjectIri,
            rootUser,
          ),
        )
        response.code should be(StatusCode.Ok)

        val membershipsAfterUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserProjectMemberships(normalUser.userIri, rootUser)
            .flatMap(_.assert200)
            .map(_.projects),
        )
        membershipsAfterUpdate should equal(Seq(SharedTestDataADM.imagesProjectExternal))
      }

      "don't add user to project if user is already a member" in {
        val membershipsBeforeTryUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserProjectMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
            .flatMap(_.assert200)
            .map(_.projects),
        )

        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.addUserToProject(
            normalUser.userIri,
            imagesProjectIri,
            rootUser,
          ),
        )
        response.code should be(StatusCode.BadRequest)

        // verify that users's project memberships weren't changed
        val membershipsAfterTryUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserProjectMemberships(normalUser.userIri, rootUser)
            .flatMap(_.assert200)
            .map(_.projects),
        )
        membershipsAfterTryUpdate should equal(membershipsBeforeTryUpdate)
      }

      "remove user from project" in {
        val membershipsBeforeUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserProjectMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
            .flatMap(_.assert200)
            .map(_.projects),
        )
        membershipsBeforeUpdate should equal(Seq(SharedTestDataADM.imagesProjectExternal))

        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.removeUserFromProject(
            normalUser.userIri,
            imagesProjectIri,
            rootUser,
          ),
        )
        response.code should be(StatusCode.Ok)

        val membershipsAfterUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserProjectMemberships(normalUser.userIri, rootUser)
            .flatMap(_.assert200)
            .map(_.projects),
        )
        membershipsAfterUpdate should equal(Seq.empty[Project])
      }
    }

    "used to query project admin group memberships" should {
      "return all projects the user is a member of the project admin group" in {
        val projects = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserProjectAdminMemberships(UserIri.unsafeFrom(multiUserIri), rootUser)
            .flatMap(_.assert200)
            .map(_.projects),
        )
        projects should contain allElementsOf Seq(
          imagesProjectExternal,
          incunabulaProjectExternal,
          anythingProjectExternal,
        )
      }
    }

    "used to modify project admin group membership" should {
      "add user to project admin group only if he is already member of that project" in {
        // add user as project admin to images project - returns a BadRequest because user is not member of the project
        val responseWithoutBeingMember = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.addUserToProjectAdmin(
            normalUser.userIri,
            imagesProjectIri,
            rootUser,
          ),
        )
        responseWithoutBeingMember.code should be(StatusCode.BadRequest)

        // add user as member to images project
        val responseAddUserToProject = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.addUserToProject(
            normalUser.userIri,
            imagesProjectIri,
            rootUser,
          ),
        )
        responseAddUserToProject.code should be(StatusCode.Ok)

        // verify that user is not yet project admin in images project
        val membershipsBeforeUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserProjectAdminMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
            .flatMap(_.assert200)
            .map(_.projects),
        )
        membershipsBeforeUpdate should equal(Seq())

        // add user as project admin to images project
        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.addUserToProjectAdmin(
            normalUser.userIri,
            imagesProjectIri,
            rootUser,
          ),
        )
        response.code should be(StatusCode.Ok)

        // verify that user has been added as project admin to images project
        val membershipsAfterUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserProjectAdminMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
            .flatMap(_.assert200)
            .map(_.projects),
        )
        membershipsAfterUpdate should equal(Seq(SharedTestDataADM.imagesProjectExternal))
      }

      "remove user from project admin group" in {
        val membershipsBeforeUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserProjectAdminMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
            .flatMap(_.assert200)
            .map(_.projects),
        )

        membershipsBeforeUpdate should equal(Seq(SharedTestDataADM.imagesProjectExternal))

        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.removeUserFromProjectAdmin(
            normalUser.userIri,
            imagesProjectIri,
            rootUser,
          ),
        )
        response.code should be(StatusCode.Ok)

        val membershipsAfterUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserProjectAdminMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
            .flatMap(_.assert200)
            .map(_.projects),
        )

        membershipsAfterUpdate should equal(Seq.empty[Project])
      }

      "remove user from project which also removes him from project admin group" in {
        // add user as project admin to images project
        val responseAddUserAsProjectAdmin = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.addUserToProjectAdmin(
            normalUser.userIri,
            imagesProjectIri,
            rootUser,
          ),
        )
        responseAddUserAsProjectAdmin.code should be(StatusCode.Ok)

        // verify that user has been added as project admin to images project
        val membershipsBeforeUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserProjectAdminMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
            .flatMap(_.assert200)
            .map(_.projects),
        )
        membershipsBeforeUpdate should equal(Seq(SharedTestDataADM.imagesProjectExternal))

        // remove user as project member from images project
        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.removeUserFromProject(
            normalUser.userIri,
            imagesProjectIri,
            rootUser,
          ),
        )
        response.code should be(StatusCode.Ok)

        // verify that user has also been removed as project admin from images project
        val projectAdminMembershipsAfterUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserProjectAdminMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
            .flatMap(_.assert200)
            .map(_.projects),
        )

        projectAdminMembershipsAfterUpdate should equal(Seq())
      }
    }

    "used to query group memberships" should {
      "return all groups the user is a member of" in {
        val groups = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserGroupMemberships(UserIri.unsafeFrom(multiUserIri), rootUser)
            .flatMap(_.assert200)
            .map(_.groups),
        )
        groups should contain allElementsOf Seq(SharedTestDataADM.imagesReviewerGroupExternal)
      }
    }

    "used to modify group membership" should {
      "add user to group" in {
        val membershipsBeforeUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserGroupMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
            .flatMap(_.assert200)
            .map(_.groups),
        )
        membershipsBeforeUpdate should equal(Seq.empty[Group])

        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.addUserToGroup(
            normalUser.userIri,
            imagesReviewerGroup.groupIri,
            rootUser,
          ),
        )
        response.code should be(StatusCode.Ok)

        val membershipsAfterUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserGroupMemberships(normalUser.userIri, rootUser)
            .flatMap(_.assert200)
            .map(_.groups),
        )
        membershipsAfterUpdate should equal(Seq(SharedTestDataADM.imagesReviewerGroupExternal))
      }

      "remove user from group" in {
        val membershipsBeforeUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserGroupMemberships(UserIri.unsafeFrom(normalUser.id), rootUser)
            .flatMap(_.assert200)
            .map(_.groups),
        )
        membershipsBeforeUpdate should equal(Seq(SharedTestDataADM.imagesReviewerGroupExternal))

        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.removeUserFromGroup(
            normalUser.userIri,
            imagesReviewerGroup.groupIri,
            rootUser,
          ),
        )
        response.code should be(StatusCode.Ok)

        val membershipsAfterUpdate = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getUserGroupMemberships(normalUser.userIri, rootUser)
            .flatMap(_.assert200)
            .map(_.groups),
        )
        membershipsAfterUpdate should equal(Seq.empty[Group])
      }
    }
  }
}
