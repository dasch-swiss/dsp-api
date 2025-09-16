/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.client4.*
import sttp.model.*
import zio.*
import zio.json.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.slice.common.domain.LanguageCode.EN
import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.messages.admin.responder.usersmessages.GroupMembersGetResponseADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.api.GroupsRequests.*
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

object AdminGroupsEndpointsSpec extends E2EZSpec {

  private val customGroupIri = GroupIri.makeNew(imagesProject.shortcode)

  val e2eSpec = suite("The Groups Route ('admin/groups')")(
    suite("used to query for group information")(
      test("return all groups") {
        TestApiClient
          .getJson[GroupsGetResponseADM](uri"/admin/groups", imagesUser01)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("return the group's information") {
        TestApiClient
          .getJson[GroupGetResponseADM](uri"/admin/groups/${imagesReviewerGroup.id}", imagesUser01)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
    ),
    suite("given a custom Iri")(
      test("create a group with the provided custom IRI ") {
        val createRequest = GroupCreateRequest(
          Some(customGroupIri),
          GroupName.unsafeFrom("NewGroupWithCustomIri"),
          GroupDescriptions.unsafeFrom(Seq(StringLiteralV2.from("A new group with a custom Iri", EN))),
          imagesProjectIri,
          GroupStatus.active,
          GroupSelfJoin.disabled,
        )
        TestApiClient
          .postJson[GroupGetResponseADM, GroupCreateRequest](
            uri"/admin/groups",
            createRequest,
            imagesUser01,
          )
          .flatMap(_.assert200)
          .map(response => assertTrue(response.group.groupIri == customGroupIri))
      },
      test("return 'BadRequest' if the supplied IRI for the group is not unique") {
        val createRequest = GroupCreateRequest(
          Some(customGroupIri),
          GroupName.unsafeFrom("NewGroupWithDuplicateCustomIri"),
          GroupDescriptions.unsafeFrom(Seq(StringLiteralV2.from("A new group with a duplicate custom Iri", EN))),
          imagesProjectIri,
          GroupStatus.active,
          GroupSelfJoin.disabled,
        )
        TestApiClient
          .postJson[GroupGetResponseADM, GroupCreateRequest](
            uri"/admin/groups",
            createRequest,
            imagesUser01,
          )
          .map(response =>
            assertTrue(
              response.code == StatusCode.BadRequest,
              response.body == Left(s"""{"message":"IRI: '$customGroupIri' already exists, try another one."}"""),
            ),
          )
      },
    ),
    suite("used to modify group information")(
      test("CREATE a new group") {
        val createRequest = GroupCreateRequest(
          None,
          GroupName.unsafeFrom("NewGroup"),
          GroupDescriptions.unsafeFrom(Seq(StringLiteralV2.from("NewGroupDescription", EN))),
          imagesProjectIri,
          GroupStatus.active,
          GroupSelfJoin.disabled,
        )
        TestApiClient
          .postJson[GroupGetResponseADM, GroupCreateRequest](
            uri"/admin/groups",
            createRequest,
            imagesUser01,
          )
          .flatMap(_.assert200)
          .map(response =>
            assertTrue(
              response.group.name == "NewGroup",
              response.group.descriptions == Seq(StringLiteralV2.from("NewGroupDescription", Some("en"))),
              response.group.project.contains(imagesProjectExternal),
              response.group.status,
              !response.group.selfjoin,
            ),
          )
      },
      test("UPDATE a group") {
        val updateRequest = GroupUpdateRequest(
          name = Some(GroupName.unsafeFrom("UpdatedGroupName")),
          descriptions = Some(GroupDescriptions.unsafeFrom(Seq(StringLiteralV2.from("UpdatedGroupDescription", EN)))),
        )
        TestApiClient
          .putJson[GroupGetResponseADM, GroupUpdateRequest](
            uri"/admin/groups/${imagesReviewerGroup.id}",
            updateRequest,
            imagesUser01,
          )
          .flatMap(_.assert200)
          .map(response =>
            assertTrue(
              response.group.name == "UpdatedGroupName",
              response.group.descriptions == Seq(StringLiteralV2.from("UpdatedGroupDescription", Some("en"))),
              response.group.project.contains(imagesProjectExternal),
              response.group.status,
              !response.group.selfjoin,
            ),
          )
      },
      test("DELETE a group") {
        TestApiClient
          .deleteJson[GroupGetResponseADM](uri"/admin/groups/${imagesReviewerGroup.id}", imagesUser01)
          .flatMap(_.assert200)
          .map(response =>
            assertTrue(
              response.group.name == "UpdatedGroupName",
              response.group.descriptions == Seq(StringLiteralV2.from("UpdatedGroupDescription", Some("en"))),
              response.group.project.contains(imagesProjectExternal),
              !response.group.status,
              !response.group.selfjoin,
            ),
          )
      },
      test("CHANGE status of a group") {
        val updateRequest = GroupStatusUpdateRequest(GroupStatus.active)
        TestApiClient
          .putJson[GroupGetResponseADM, GroupStatusUpdateRequest](
            uri"/admin/groups/${imagesReviewerGroup.id}/status",
            updateRequest,
            imagesUser01,
          )
          .flatMap(_.assert200)
          .map(response =>
            assertTrue(
              response.group.name == "UpdatedGroupName",
              response.group.descriptions == Seq(StringLiteralV2.from("UpdatedGroupDescription", Some("en"))),
              response.group.project.contains(imagesProjectExternal),
              response.group.status,
              !response.group.selfjoin,
            ),
          )
      },
    ),
    suite("used to query members")(
      test("return all members of a group") {
        TestApiClient
          .getJson[GroupMembersGetResponseADM](uri"/admin/groups/${imagesReviewerGroup.id}/members", imagesUser01)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
    ),
  )
}
