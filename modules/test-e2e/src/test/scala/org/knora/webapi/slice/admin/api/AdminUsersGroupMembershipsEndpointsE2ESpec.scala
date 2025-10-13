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
import org.knora.webapi.messages.admin.responder.usersmessages.UserGroupMembershipsGetResponseADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM2
import org.knora.webapi.slice.admin.api.service.UserRestService.UserResponse
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.testservices.ResponseOps
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

object AdminUsersGroupMembershipsEndpointsE2ESpec extends E2EZSpec {

  private val multiUserIri = UserIri.unsafeFrom(SharedTestDataADM2.multiuserUser.userData.user_id.get)

  override val e2eSpec = suite(
    "The Users Route ('admin/users/iri/:userIri/group-member-ships') ",
  )(
    suite("used to query group memberships")(
      test("return all groups the user is a member of") {
        TestApiClient
          .getJson[UserGroupMembershipsGetResponseADM](uri"/admin/users/iri/$multiUserIri/group-memberships", rootUser)
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
          _ <- TestApiClient
                 .getJson[UserGroupMembershipsGetResponseADM](
                   uri"/admin/users/iri/${normalUser.id}/group-memberships",
                   rootUser,
                 )
                 .flatMap(_.assert200)
                 .filterOrFail(_.groups.isEmpty)(IllegalStateException("User is already member of a group"))
          response <- TestApiClient.postJson[UserResponse, String](
                        uri"/admin/users/iri/${normalUser.id}/group-memberships/${imagesReviewerGroup.groupIri}",
                        "",
                        rootUser,
                      )
          membershipsAfterResult <- TestApiClient
                                      .getJson[UserGroupMembershipsGetResponseADM](
                                        uri"/admin/users/iri/${normalUser.id}/group-memberships",
                                        rootUser,
                                      )
                                      .flatMap(_.assert200)
        } yield assertTrue(membershipsAfterResult.groups == Seq(SharedTestDataADM.imagesReviewerGroupExternal))
      } @@ TestAspect.timeout(5.seconds) @@ TestAspect.flaky,
      test("remove user from group") {
        for {
          membershipsBeforeResponse <-
            TestApiClient.getJson[UserGroupMembershipsGetResponseADM](
              uri"/admin/users/iri/${normalUser.id}/group-memberships",
              rootUser,
            )
          membershipsBeforeResult <- membershipsBeforeResponse.assert200
          response <- TestApiClient.deleteJson[UserResponse](
                        uri"/admin/users/iri/${normalUser.id}/group-memberships/${imagesReviewerGroup.groupIri}",
                        rootUser,
                      )
          membershipsAfterResponse <- TestApiClient.getJson[UserGroupMembershipsGetResponseADM](
                                        uri"/admin/users/iri/${normalUser.id}/group-memberships",
                                        rootUser,
                                      )
          membershipsAfterResult <- membershipsAfterResponse.assert200
        } yield assertTrue(
          membershipsBeforeResult.groups == Seq(SharedTestDataADM.imagesReviewerGroupExternal),
          response.code == StatusCode.Ok,
          membershipsAfterResult.groups == Seq.empty[Group],
        )
      },
    ),
  )
}
