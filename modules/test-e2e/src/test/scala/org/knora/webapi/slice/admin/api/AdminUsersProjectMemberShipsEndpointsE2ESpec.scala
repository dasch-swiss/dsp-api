/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.client4.UriContext
import sttp.model.StatusCode
import zio.*
import zio.test.*

import org.knora.webapi.*
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectAdminMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectMembershipsGetResponseADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM2
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.api.service.UserRestService.UserResponse
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.testservices.ResponseOps
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.ZioHelper.addLogTiming

object AdminUsersProjectMemberShipsEndpointsE2ESpec extends E2EZSpec {

  private val multiUserIri = UserIri.unsafeFrom(SharedTestDataADM2.multiuserUser.userData.user_id.get)

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
        addUserToProject(normalUser.userIri, imagesProjectExternal.id, normalUser)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
      test("add user to project") {
        for {
          beforeResult <- getProjectMemberships(normalUser.userIri).flatMap(_.assert200)
          _            <- addUserToProject(normalUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)
          afterResult  <- getProjectMemberships(normalUser.userIri).flatMap(_.assert200)
        } yield assertTrue(
          beforeResult.projects == Seq.empty,
          afterResult.projects == Seq(imagesProjectExternal),
        )
      },
      test("don't add user to project if user is already a member") {
        for {
          beforeResult <- getProjectMemberships(normalUser.userIri).flatMap(_.assert200)
          _            <- addUserToProject(normalUser.userIri, imagesProjectExternal.id).flatMap(_.assert400)
          afterResult  <- getProjectMemberships(normalUser.userIri).flatMap(_.assert200)
        } yield assertTrue(afterResult.projects == beforeResult.projects)
      },
      test("remove user from project") {
        for {
          beforeResult <- getProjectMemberships(normalUser.userIri).flatMap(_.assert200)
          _            <- removeUserFromProject(normalUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)
          afterResult  <- getProjectMemberships(normalUser.userIri).flatMap(_.assert200)
        } yield assertTrue(
          beforeResult.projects == Seq(imagesProjectExternal),
          afterResult.projects == Seq.empty,
        )
      },
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
      test("add user to project admin group only if he is already member of that project") {
        for {
          // add user as project admin to images project - should return BadRequest because user is not member of the project
          _ <- addUserToProjectAsAdmin(normalUser.userIri, imagesProjectExternal.id).flatMap(_.assert400)
          // add user as member to images project, must succeed
          _ <- addUserToProject(normalUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)
          // verify that user is not yet project admin in images project
          membershipsBeforeResult <- getProjectAdminMemberships(normalUser.userIri).flatMap(_.assert200)
          // add user as project admin to images project
          _ <- addUserToProjectAsAdmin(normalUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)
          // verify that user has been added as project admin to images project
          membershipsAfterResult <- getProjectAdminMemberships(normalUser.userIri).flatMap(_.assert200)
        } yield assertTrue(
          membershipsBeforeResult.projects == Seq.empty,
          membershipsAfterResult.projects == Seq(imagesProjectExternal),
        )
      },
      test("remove user from project admin group") {
        for {
          membershipsBefore <- getProjectAdminMemberships(normalUser.userIri).flatMap(_.assert200)
          _                 <- removeUserFromProject(normalUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)
          membershipsAfter  <- getProjectAdminMemberships(normalUser.userIri).flatMap(_.assert200)
        } yield assertTrue(
          membershipsBefore.projects == Seq(imagesProjectExternal),
          membershipsAfter.projects == Seq.empty,
        )
      },
      test("remove user from project which also removes him from project admin group") {
        for {
          // add user as project admin to images project
          _ <- addUserToProject(normalUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)
          _ <- addUserToProjectAsAdmin(normalUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)
          // remove user as project member from images project
          _ <- removeUserFromProject(normalUser.userIri, imagesProjectExternal.id).flatMap(_.assert200)
          // verify that user has also been removed as project admin from images project
          projectAdminMembershipsAfterResult <- getProjectAdminMemberships(normalUser.userIri).flatMap(_.assert200)
        } yield assertTrue(projectAdminMembershipsAfterResult.projects == Seq.empty)
      },
    ),
  ) @@ TestAspect.timeout(2.seconds)
    @@ TestAspect.flaky

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
