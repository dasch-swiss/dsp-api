/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.client4.UriContext
import sttp.model.StatusCode
import zio.ZIO
import zio.json.ast.Json
import zio.test.*

import org.knora.webapi.*
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.*
import org.knora.webapi.slice.admin.api.service.UserRestService.UserResponse
import org.knora.webapi.slice.admin.api.service.UserRestService.UsersResponse
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.TokenResponse
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.MutableTestIri

/**
 * End-to-End (E2E) test specification for testing admin users endpoints.
 */
object AdminUsersEndpointsE2ESpec extends E2EZSpec {

  private val projectAdminUser = imagesUser01

  private val customUserIri      = UserIri.unsafeFrom("http://rdfh.ch/users/14pxW-LAQIaGcCRiNCPJcQ")
  private val otherCustomUserIri = UserIri.unsafeFrom("http://rdfh.ch/users/v8_12VcJRlGNFCjYzqJ5cA")

  private val donaldIri = new MutableTestIri

  val e2eSpec = suite("The Users Route ('admin/users')")(
    suite("used to query user information [FUNCTIONALITY]")(
      test("return all users") {
        TestApiClient
          .getJson[UsersResponse](uri"/admin/users", rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return a single user profile identified by iri") {
        TestApiClient
          .getJson[UserResponse](uri"/admin/users/iri/${rootUser.id}", rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return a single user profile identified by email") {
        TestApiClient
          .getJson[UserResponse](uri"/admin/users/email/${rootUser.email}", rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return a single user profile identified by username") {
        TestApiClient
          .getJson[UserResponse](uri"/admin/users/username/${rootUser.username}", rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
    ),
    suite("used to query user information [PERMISSIONS]")(
      test("return single user for SystemAdmin") {
        TestApiClient
          .getJson[UserResponse](uri"/admin/users/iri/${normalUser.userIri}", rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return single user for itself") {
        TestApiClient
          .getJson[UserResponse](uri"/admin/users/iri/${normalUser.userIri}", normalUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return only public information for single user for non SystemAdmin and self") {
        TestApiClient
          .getJson[UserResponse](uri"/admin/users/iri/${normalUser.userIri}", projectAdminUser)
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
        TestApiClient
          .getJson[UserResponse](uri"/admin/users/iri/${normalUser.userIri}")
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
        TestApiClient
          .getJson[UsersResponse](uri"/admin/users/", rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return all users for ProjectAdmin") {
        TestApiClient
          .getJson[UsersResponse](uri"/admin/users/", projectAdminUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return 'Forbidden' for all users for normal user") {
        TestApiClient
          .getJson[UsersResponse](uri"/admin/users/", normalUser)
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
          lang = LanguageCode.EN,
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
          lang = LanguageCode.EN,
          systemAdmin = SystemAdmin.IsNotSystemAdmin,
        )
        TestApiClient
          .postJson[UserResponse, UserCreateRequest](uri"/admin/users", createUserRequest, rootUser)
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
          lang = LanguageCode.EN,
          systemAdmin = SystemAdmin.IsNotSystemAdmin,
        )
        TestApiClient
          .postJson[UserResponse, UserCreateRequest](uri"/admin/users", createUserRequest, rootUser)
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
          lang = LanguageCode.EN,
          systemAdmin = SystemAdmin.IsNotSystemAdmin,
        )
        TestApiClient
          .postJson[UserResponse, UserCreateRequest](uri"/admin/users", createUserRequest, rootUser)
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
        TestApiClient
          .putJson[UserResponse, BasicUserInformationChangeRequest](
            uri"/admin/users/iri/$otherCustomUserIri/BasicUserInformation",
            updateUserRequest,
            rootUser,
          )
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
        TestApiClient
          .getJson[UserResponse](uri"/admin/users/iri/$otherCustomUserIri", rootUser)
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
          lang = LanguageCode.EN,
          systemAdmin = SystemAdmin.IsSystemAdmin,
        )
        TestApiClient
          .postJson[UserResponse, UserCreateRequest](uri"/admin/users", createUserRequest, projectAdminUser)
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
          lang = LanguageCode.EN,
          systemAdmin = SystemAdmin.IsNotSystemAdmin,
        )
        TestApiClient
          .postJson[UserResponse, UserCreateRequest](uri"/admin/users", createUserRequest, rootUser)
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
          lang = LanguageCode.EN,
          systemAdmin = SystemAdmin.IsNotSystemAdmin,
        )
        TestApiClient
          .postJson[UserResponse, UserCreateRequest](uri"/admin/users", createUserRequest, rootUser)
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
          lang = LanguageCode.EN,
          systemAdmin = SystemAdmin.IsNotSystemAdmin,
        )
        TestApiClient
          .postJson[UserResponse, UserCreateRequest](uri"/admin/users", createUserRequest, rootUser)
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("authenticate the newly created user using HttpBasicAuth") {
        TestApiClient
          .getJson[Json](uri"/v2/authentication", _.auth.basic("donald.duck@example.org", "test"))
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
          lang = Some(LanguageCode.DE),
        )
        TestApiClient
          .putJson[UserResponse, BasicUserInformationChangeRequest](
            uri"/admin/users/iri/$donaldIri/BasicUserInformation",
            updateUserRequest,
            rootUser,
          )
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
        TestApiClient
          .putJson[UserResponse, PasswordChangeRequest](
            uri"/admin/users/iri/$customUserIri/Password",
            changeUserPasswordRequest,
            normalUser,
          )
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
      test("update the user's password (by himself)") {
        val changeUserPasswordRequest = PasswordChangeRequest(
          requesterPassword = Password.unsafeFrom("test"),
          newPassword = Password.unsafeFrom("test123456"),
        )
        for {
          _ <- TestApiClient
                 .putJson[UserResponse, PasswordChangeRequest](
                   uri"/admin/users/iri/${normalUser.id}/Password",
                   changeUserPasswordRequest,
                   normalUser,
                 )
                 .flatMap(_.assert200)
          // check if the password was changed, i.e. if the new one is accepted
          response <- TestApiClient.getJson[Json](
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
          _ <- TestApiClient
                 .putJson[UserResponse, PasswordChangeRequest](
                   uri"/admin/users/iri/${normalUser.id}/Password",
                   changeUserPasswordRequest,
                   rootUser,
                 )
                 .flatMap(_.assert200)
          // check if the password was changed, i.e. if the new one is accepted
          response <- TestApiClient.getJson[Json](
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
          response1 <- TestApiClient.putJson[Json, String](
                         uri"/admin/users/iri/${normalUser.id}/Password",
                         changeUserPasswordRequest,
                         normalUser,
                       )
          // check that the password was not changed, i.e. the old one is still accepted
          response2 <- TestApiClient.getJson[Json](
                         uri"/v2/authentication",
                         _.auth.basic(normalUser.email, "test654321"),
                       )
        } yield assertTrue(
          response1.code == StatusCode.BadRequest,
          response2.code == StatusCode.Ok,
        )
      },
      test("change user's status") {
        TestApiClient
          .putJson[UserResponse, StatusChangeRequest](
            uri"/admin/users/iri/$donaldIri/Status",
            StatusChangeRequest(UserStatus.Inactive),
            rootUser,
          )
          .flatMap(_.assert200)
          .map(result => assertTrue(!result.user.status))
      },
      test("update the user's system admin membership status") {
        val changeReq = SystemAdminChangeRequest(SystemAdmin.IsSystemAdmin)
        for {
          result <- TestApiClient
                      .putJson[UserResponse, SystemAdminChangeRequest](
                        uri"/admin/users/iri/$donaldIri/SystemAdmin",
                        changeReq,
                        rootUser,
                      )
                      .flatMap(_.assert200)
          // Throw BadRequest exception if user is built-in user
          badResponse <- TestApiClient.putJson[UserResponse, SystemAdminChangeRequest](
                           uri"/admin/users/iri/${KnoraSystemInstances.Users.SystemUser.userIri}/SystemAdmin",
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
        TestApiClient
          .putJson[UserResponse, SystemAdminChangeRequest](
            uri"/admin/users/iri/${KnoraSystemInstances.Users.SystemUser.id}/SystemAdmin",
            SystemAdminChangeRequest(SystemAdmin.IsSystemAdmin),
            rootUser,
          )
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("not allow changing the system user's status") {
        TestApiClient
          .putJson[UserResponse, StatusChangeRequest](
            uri"/admin/users/iri/${KnoraSystemInstances.Users.SystemUser.id}/Status",
            StatusChangeRequest(UserStatus.Inactive),
            rootUser,
          )
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("not allow changing the anonymous user's status") {
        TestApiClient
          .putJson[UserResponse, StatusChangeRequest](
            uri"/admin/users/iri/${KnoraSystemInstances.Users.AnonymousUser.id}/Status",
            StatusChangeRequest(UserStatus.Inactive),
            rootUser,
          )
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("delete a user") {
        TestApiClient
          .deleteJson[UserResponse](uri"/admin/users/iri/$customUserIri", rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("not allow deleting the system user") {
        TestApiClient
          .deleteJson[UserResponse](uri"/admin/users/iri/${KnoraSystemInstances.Users.SystemUser.id}", rootUser)
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("not allow deleting the anonymous user") {
        TestApiClient
          .deleteJson[UserResponse](uri"/admin/users/iri/${KnoraSystemInstances.Users.AnonymousUser.id}", rootUser)
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
    ),
  )
}
