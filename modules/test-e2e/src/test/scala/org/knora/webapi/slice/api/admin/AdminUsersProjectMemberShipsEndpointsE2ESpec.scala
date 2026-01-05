/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.client4.UriContext
import sttp.model.StatusCode
import zio.*
import zio.json.*
import zio.test.*

import org.knora.webapi.*
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectAdminMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectMembershipsGetResponseADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM2
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.admin.AdminUsersProjectMemberShipsEndpointsE2ESpec.faker
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.UserCreateRequest
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.api.admin.service.UserRestService.UserResponse
import org.knora.webapi.slice.common.domain.LanguageCode.DE
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.ZioHelper.addLogTiming

object AdminUsersProjectMemberShipsEndpointsE2ESpec extends E2EZSpec {

  private val multiUserIri = UserIri.unsafeFrom(SharedTestDataADM2.multiuserUser.userData.user_id.get)

  private def createNewUser =
    val firstName = faker.name().firstName()
    val lastName  = faker.name().lastName()
    val req       = UserCreateRequest(
      id = None,
      Username.unsafeFrom(s"$firstName.${faker.number().positive()}"),
      Email.unsafeFrom(s"$firstName.$lastName@example.org"),
      GivenName.unsafeFrom(firstName),
      FamilyName.unsafeFrom(lastName),
      Password.unsafeFrom(faker.credentials().password(8, 16)),
      UserStatus.Active,
      DE,
      SystemAdmin.IsNotSystemAdmin,
    )
    TestApiClient
      .postJson[UserResponse, UserCreateRequest](uri"/admin/users", req, rootUser)
      .flatMap(_.assert200)
      .map(_.user)

  override val e2eSpec = suite(
    "The Users Routes ('admin/users/iri/:userIri/project-memberships', 'admin/users/iri/:userIri/project-admin-memberships') ",
  )(
    suite("used to query project memberships")(
      test("return all projects the user is a member of") {
        getProjectMemberships(multiUserIri)
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
        for {
          newUser <- createNewUser

          response <- addUserToProject(newUser.userIri, imagesProjectExternal.id, normalUser)

        } yield assertTrue(response.code == StatusCode.Forbidden)
      },
      test("add user to project") {
        for {
          newUser <- createNewUser

          _ <- addUserToProject(newUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)

          projectMemberships <- getProjectMemberships(newUser.userIri).flatMap(_.assert200)
        } yield assertTrue(projectMemberships.projects == Seq(imagesProjectExternal))
      } @@ TestAspect.timeout(5.seconds) @@ TestAspect.flaky,
      test("don't add user to project if user is already a member") {
        for {
          newUser <- createNewUser
          _       <- addUserToProject(newUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)

          response <- addUserToProject(newUser.userIri, imagesProjectExternal.id)

        } yield assertTrue(response.code == StatusCode.BadRequest)
      },
      test("remove user from project") {
        for {
          newUser <- createNewUser
          _       <- addUserToProject(newUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)

          _ <- removeUserFromProject(newUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)

          projectMemberships <- getProjectMemberships(newUser.userIri).flatMap(_.assert200)
        } yield assertTrue(projectMemberships.projects == Seq.empty)
      } @@ TestAspect.timeout(5.seconds) @@ TestAspect.flaky,
    ),
    suite("used to query project admin group memberships")(
      test("return all projects the user is a member of the project admin group") {
        getProjectAdminMemberships(multiUserIri)
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
    suite("used to modify project admin group membership")(
      test("do NOT add user to project admin group if not member of that project") {
        for {
          newUser <- createNewUser

          response <- addUserToProjectAsAdmin(newUser.userIri, imagesProjectExternal.id)

        } yield assertTrue(response.code == StatusCode.BadRequest)
      },
      test("add user to project admin group if member of that project") {
        for {
          newUser <- createNewUser

          _ <- addUserToProject(newUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)
          _ <- addUserToProjectAsAdmin(newUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)

          adminMemberships <- getProjectAdminMemberships(newUser.userIri).flatMap(_.assert200)
        } yield assertTrue(adminMemberships.projects == Seq(imagesProjectExternal))
      } @@ TestAspect.timeout(5.seconds) @@ TestAspect.flaky,
      test("remove user from project admin group") {
        for {
          newUser <- createNewUser
          _       <- addUserToProject(newUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)
          _       <- addUserToProjectAsAdmin(newUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)

          _ <- removeUserFromProject(newUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)

          adminMemberships <- getProjectAdminMemberships(newUser.userIri).flatMap(_.assert200)
        } yield assertTrue(adminMemberships.projects == Seq.empty)
      } @@ TestAspect.timeout(5.seconds) @@ TestAspect.flaky,
      test("remove user from project which also removes them from project admin group") {
        for {
          newUser <- createNewUser
          _       <- addUserToProject(newUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)
          _       <- addUserToProjectAsAdmin(newUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)

          _ <- removeUserFromProject(newUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)

          adminMemberships <- getProjectAdminMemberships(normalUser.userIri).flatMap(_.assert200)
        } yield assertTrue(adminMemberships.projects == Seq.empty)
      } @@ TestAspect.timeout(5.seconds) @@ TestAspect.flaky,
    ),
  )

  private def getProjectMemberships(userIri: UserIri, requestingUser: User = rootUser) =
    addLogTiming("GET project-memberships") {
      TestApiClient
        .getJson[UserProjectMembershipsGetResponseADM](
          uri"/admin/users/iri/$userIri/project-memberships",
          requestingUser,
        )
    }

  private def getProjectAdminMemberships(userIri: UserIri, requestingUser: User = rootUser) =
    addLogTiming("GET project-admin-memberships") {
      TestApiClient
        .getJson[UserProjectAdminMembershipsGetResponseADM](
          uri"/admin/users/iri/$userIri/project-admin-memberships",
          requestingUser,
        )
    }

  private def addUserToProject(userIri: UserIri, projectIri: ProjectIri, requestingUser: User = rootUser) =
    addLogTiming("POST project-memberships") {
      TestApiClient
        .postJson[UserResponse, String](
          uri"/admin/users/iri/$userIri/project-memberships/$projectIri",
          "",
          requestingUser,
        )
    }

  private def addUserToProjectAsAdmin(userIri: UserIri, projectIri: ProjectIri, requestingUser: User = rootUser) =
    addLogTiming("POST project-admin-memberships") {
      TestApiClient
        .postJson[UserResponse, String](
          uri"/admin/users/iri/$userIri/project-admin-memberships/$projectIri",
          "",
          requestingUser,
        )
    }

  private def removeUserFromProject(userIri: UserIri, projectIri: ProjectIri, requestingUser: User = rootUser) =
    addLogTiming("DELETE project-memberships") {
      TestApiClient
        .deleteJson[UserResponse](
          uri"/admin/users/iri/$userIri/project-memberships/$projectIri",
          requestingUser,
        )
    }
}
