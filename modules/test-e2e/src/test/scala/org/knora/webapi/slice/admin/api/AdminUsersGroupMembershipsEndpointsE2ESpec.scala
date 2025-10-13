/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.client4.UriContext
import zio.*
import zio.test.*

import org.knora.webapi.*
import org.knora.webapi.messages.admin.responder.usersmessages.UserGroupMembershipsGetResponseADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM2
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.UserCreateRequest
import org.knora.webapi.slice.admin.api.model.UserDto
import org.knora.webapi.slice.admin.api.service.UserRestService.UserResponse
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.common.domain.LanguageCode.DE
import org.knora.webapi.testservices.ResponseOps
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

object AdminUsersGroupMembershipsEndpointsE2ESpec extends E2EZSpec {

  private val multiUserIri = UserIri.unsafeFrom(SharedTestDataADM2.multiuserUser.userData.user_id.get)

  private def createNewUser =
    val firstName = faker.name().firstName()
    val lastName  = faker.name().lastName()
    val req = UserCreateRequest(
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
          newUser           <- createNewUser
          _                 <- addUserToGroup(newUser, imagesReviewerGroupExternal)
          actualMemberships <- getGroupMemberships(newUser)
        } yield assertTrue(actualMemberships.groups == Seq(imagesReviewerGroupExternal))
      } @@ TestAspect.timeout(5.seconds) @@ TestAspect.flaky,
      test("remove user from group") {
        for {
          newUser           <- createNewUser
          _                 <- addUserToGroup(newUser, imagesReviewerGroupExternal)
          _                 <- removeUserFromGroup(newUser, imagesReviewerGroupExternal)
          actualMemberships <- getGroupMemberships(newUser)
        } yield assertTrue(actualMemberships.groups == Seq.empty)
      } @@ TestAspect.timeout(5.seconds) @@ TestAspect.flaky,
    ),
  )

  private def removeUserFromGroup(user: UserDto, group: Group) =
    TestApiClient
      .deleteJson[UserResponse](
        uri"/admin/users/iri/${user.id}/group-memberships/${group.groupIri}",
        rootUser,
      )
      .flatMap(_.assert200)

  private def getGroupMemberships(user: UserDto) =
    TestApiClient
      .getJson[UserGroupMembershipsGetResponseADM](
        uri"/admin/users/iri/${user.id}/group-memberships",
        rootUser,
      )
      .flatMap(_.assert200)

  private def addUserToGroup(user: UserDto, group: Group) =
    TestApiClient
      .postJson[UserResponse, IRI](
        uri"/admin/users/iri/${user.id}/group-memberships/${group.groupIri}",
        "",
        rootUser,
      )
      .flatMap(_.assert200)
}
