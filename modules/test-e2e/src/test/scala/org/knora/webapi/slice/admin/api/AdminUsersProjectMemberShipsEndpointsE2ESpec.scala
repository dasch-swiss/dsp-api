/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.client4.UriContext
import sttp.model.StatusCode
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
import org.knora.webapi.testservices.ResponseOps
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

object AdminUsersProjectMemberShipsEndpointsE2ESpec extends E2EZSpec {

  private val multiUserIri = UserIri.unsafeFrom(SharedTestDataADM2.multiuserUser.userData.user_id.get)

  override val e2eSpec = suite(
    "The Users Routes ('admin/users/iri/:userIri/project-memberships', 'admin/users/iri/:userIri/project-admin-memberships') ",
  )(
    suite("used to query project memberships")(
      test("return all projects the user is a member of") {
        TestApiClient
          .getJson[UserProjectMembershipsGetResponseADM](
            uri"/admin/users/iri/$multiUserIri/project-memberships",
            rootUser,
          )
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
        TestApiClient
          .postJson[UserResponse, String](
            uri"/admin/users/iri/${normalUser.id}/project-memberships/$imagesProjectIri",
            "",
            normalUser,
          )
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
      test("add user to project") {
        for {
          beforeResult <- TestApiClient
                            .getJson[UserProjectMembershipsGetResponseADM](
                              uri"/admin/users/iri/${normalUser.id}/project-memberships",
                              rootUser,
                            )
                            .flatMap(_.assert200)
          _ <- TestApiClient
                 .postJson[UserResponse, String](
                   uri"/admin/users/iri/${normalUser.id}/project-memberships/$imagesProjectIri",
                   "",
                   rootUser,
                 )
                 .flatMap(_.assert200)
          afterResult <- TestApiClient
                           .getJson[UserProjectMembershipsGetResponseADM](
                             uri"/admin/users/iri/${normalUser.id}/project-memberships",
                             rootUser,
                           )
                           .flatMap(_.assert200)
        } yield assertTrue(
          beforeResult.projects == Seq.empty,
          afterResult.projects == Seq(SharedTestDataADM.imagesProjectExternal),
        )
      },
      test("don't add user to project if user is already a member") {
        for {
          beforeResult <- TestApiClient
                            .getJson[UserProjectMembershipsGetResponseADM](
                              uri"/admin/users/iri/${normalUser.id}/project-memberships",
                              rootUser,
                            )
                            .flatMap(_.assert200)
          response <- TestApiClient.postJson[UserResponse, String](
                        uri"/admin/users/iri/${normalUser.id}/project-memberships/$imagesProjectIri",
                        "",
                        rootUser,
                      )
          afterResult <- TestApiClient
                           .getJson[UserProjectMembershipsGetResponseADM](
                             uri"/admin/users/iri/${normalUser.id}/project-memberships",
                             rootUser,
                           )
                           .flatMap(_.assert200)
        } yield assertTrue(
          response.code == StatusCode.BadRequest,
          afterResult.projects == beforeResult.projects,
        )
      },
      test("remove user from project") {
        for {
          beforeResult <- TestApiClient
                            .getJson[UserProjectMembershipsGetResponseADM](
                              uri"/admin/users/iri/${normalUser.id}/project-memberships",
                              rootUser,
                            )
                            .flatMap(_.assert200)
          response <- TestApiClient.deleteJson[UserResponse](
                        uri"/admin/users/iri/${normalUser.id}/project-memberships/$imagesProjectIri",
                        rootUser,
                      )
          afterResult <- TestApiClient
                           .getJson[UserProjectMembershipsGetResponseADM](
                             uri"/admin/users/iri/${normalUser.id}/project-memberships",
                             rootUser,
                           )
                           .flatMap(_.assert200)
        } yield assertTrue(
          beforeResult.projects == Seq(SharedTestDataADM.imagesProjectExternal),
          response.code == StatusCode.Ok,
          afterResult.projects == Seq.empty[Project],
        )
      },
    ),
    suite("used to query project admin group memberships")(
      test("return all projects the user is a member of the project admin group") {
        TestApiClient
          .getJson[UserProjectAdminMembershipsGetResponseADM](
            uri"/admin/users/iri/$multiUserIri/project-admin-memberships",
            rootUser,
          )
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
          // add user as project admin to images project - returns a BadRequest because user is not member of the project
          responseWithoutBeingMember <-
            TestApiClient.postJson[UserResponse, String](
              uri"/admin/users/iri/${normalUser.id}/project-admin-memberships/$imagesProjectIri",
              "",
              rootUser,
            )
          // add user as member to images project
          responseAddUserToProject <- TestApiClient.postJson[UserResponse, String](
                                        uri"/admin/users/iri/${normalUser.id}/project-memberships/$imagesProjectIri",
                                        "",
                                        rootUser,
                                      )
          // verify that user is not yet project admin in images project
          membershipsBeforeResponse <- TestApiClient.getJson[UserProjectAdminMembershipsGetResponseADM](
                                         uri"/admin/users/iri/${normalUser.id}/project-admin-memberships",
                                         rootUser,
                                       )
          membershipsBeforeResult <- membershipsBeforeResponse.assert200
          // add user as project admin to images project
          response <- TestApiClient.postJson[UserResponse, String](
                        uri"/admin/users/iri/${normalUser.id}/project-admin-memberships/$imagesProjectIri",
                        "",
                        rootUser,
                      )
          // verify that user has been added as project admin to images project
          membershipsAfterResponse <- TestApiClient.getJson[UserProjectAdminMembershipsGetResponseADM](
                                        uri"/admin/users/iri/${normalUser.id}/project-admin-memberships",
                                        rootUser,
                                      )
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
          membershipsBeforeResponse <- TestApiClient.getJson[UserProjectAdminMembershipsGetResponseADM](
                                         uri"/admin/users/iri/${normalUser.id}/project-admin-memberships",
                                         rootUser,
                                       )
          membershipsBeforeResult <- membershipsBeforeResponse.assert200
          response <- TestApiClient.deleteJson[UserResponse](
                        uri"/admin/users/iri/${normalUser.id}/project-memberships/$imagesProjectIri",
                        rootUser,
                      )
          membershipsAfterResponse <- TestApiClient.getJson[UserProjectAdminMembershipsGetResponseADM](
                                        uri"/admin/users/iri/${normalUser.id}/project-admin-memberships",
                                        rootUser,
                                      )
          membershipsAfterResult <- membershipsAfterResponse.assert200
        } yield assertTrue(
          membershipsBeforeResult.projects == Seq(SharedTestDataADM.imagesProjectExternal),
          response.code == StatusCode.Ok,
          membershipsAfterResult.projects == Seq.empty,
        )
      },
      test("remove user from project which also removes him from project admin group") {
        for {
          // add user as project admin to images project
          _ <- TestApiClient
                 .postJson[UserResponse, String](
                   uri"/admin/users/iri/${normalUser.id}/project-memberships/$imagesProjectIri",
                   "",
                   rootUser,
                 )
                 .flatMap(_.assert200) *>
                 TestApiClient
                   .postJson[UserResponse, String](
                     uri"/admin/users/iri/${normalUser.id}/project-admin-memberships/$imagesProjectIri",
                     "",
                     rootUser,
                   )
                   .flatMap(_.assert200)

          // remove user as project member from images project
          _ <- TestApiClient
                 .deleteJson[UserResponse](
                   uri"/admin/users/iri/${normalUser.id}/project-memberships/$imagesProjectIri",
                   rootUser,
                 )
                 .flatMap(_.assert200)
          // verify that user has also been removed as project admin from images project
          projectAdminMembershipsAfterResult <- TestApiClient
                                                  .getJson[UserProjectAdminMembershipsGetResponseADM](
                                                    uri"/admin/users/iri/${normalUser.id}/project-admin-memberships",
                                                    rootUser,
                                                  )
                                                  .flatMap(_.assert200)
        } yield assertTrue(projectAdminMembershipsAfterResult.projects == Seq.empty)
      },
    ),
  )
}
