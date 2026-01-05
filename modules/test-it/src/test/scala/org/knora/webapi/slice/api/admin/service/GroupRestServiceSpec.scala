/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

import zio.*
import zio.test.*
import zio.test.Assertion.*

import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.messages.admin.responder.usersmessages.*
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.GroupDescriptions
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.GroupSelfJoin
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.service.GroupService
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupStatusUpdateRequest
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.common.domain.LanguageCode.EN
import org.knora.webapi.util.MutableTestIri

object GroupRestServiceSpec extends E2EZSpec {

  private val groupRestService = ZIO.serviceWithZIO[GroupRestService]
  private val groupService     = ZIO.serviceWithZIO[GroupService]
  private val newGroupIri      = new MutableTestIri

  val e2eSpec = suite("GroupRestService")(
    suite("getGroups - asked about all groups")(
      test("return a list") {
        groupRestService(_.getGroups).map { response =>
          assertTrue(
            response.groups.nonEmpty,
            response.groups.size >= 2, // at least imagesReviewerGroup and imagesProjectGroup
          )
        }
      },
    ),
    suite("findById - asked about a group identified by IRI ")(
      test("return group info if the group is known ") {
        val iri = imagesReviewerGroup.groupIri
        groupService(_.findById(iri)).map { response =>
          assertTrue(response.nonEmpty, response.map(_.id).contains(imagesReviewerGroup.id))
        }
      },
      test("return 'None' when the group is unknown ") {
        groupService(_.findById(GroupIri.unsafeFrom("http://rdfh.ch/groups/0987/notexisting"))).map(response =>
          assertTrue(response.isEmpty),
        )
      },
    ),
    suite("used to modify group information")(
      test("CREATE the group and return the group's info if the supplied group name is unique") {

        val createReq = GroupCreateRequest(
          id = None,
          name = GroupName.unsafeFrom("NewGroup"),
          descriptions = GroupDescriptions
            .unsafeFrom(
              Seq(StringLiteralV2.from("""NewGroupDescription with "quotes" and <html tag>""", EN)),
            ),
          project = imagesProjectIri,
          status = GroupStatus.active,
          selfjoin = GroupSelfJoin.disabled,
        )

        groupRestService(_.postGroup(rootUser)(createReq))
          .map(_.group)
          .tap(group => ZIO.succeed(newGroupIri.set(group.id)))
          .map { group =>
            assertTrue(
              group.id == newGroupIri.get,
              group.name == "NewGroup",
              group.descriptions == Seq(
                StringLiteralV2.from("""NewGroupDescription with "quotes" and <html tag>""", EN),
              ),
              group.project.contains(imagesProjectExternal),
              group.status,
              !group.selfjoin,
            )
          }
      },
      test("return a 'DuplicateValueException' if the supplied group name is not unique") {
        val groupName     = GroupName.unsafeFrom("NewGroup")
        val createRequest = GroupCreateRequest(
          id = Some(imagesReviewerGroup.groupIri),
          name = groupName,
          descriptions = GroupDescriptions
            .unsafeFrom(Seq(StringLiteralV2.from(value = "NewGroupDescription", EN))),
          project = imagesProjectIri,
          status = GroupStatus.active,
          selfjoin = GroupSelfJoin.disabled,
        )
        groupRestService(_.postGroup(rootUser)(createRequest)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageContaining[DuplicateValueException](
              s"Group with name: '${groupName.value}' already exists.",
            ),
          ),
        )
      },
      test("UPDATE a group") {
        val updateReq = GroupUpdateRequest(
          name = Some(GroupName.unsafeFrom("UpdatedGroupName")),
          descriptions = Some(
            GroupDescriptions.unsafeFrom(
              Seq(StringLiteralV2.from("""UpdatedDescription with "quotes" and <html tag>""", EN)),
            ),
          ),
          status = Some(GroupStatus.active),
          selfjoin = Some(GroupSelfJoin.disabled),
        )
        groupRestService(_.putGroup(rootUser)(newGroupIri.asGroupIri, updateReq))
          .map(_.group)
          .map(group =>
            assertTrue(
              group.id == newGroupIri.get,
              group.name == "UpdatedGroupName",
              group.descriptions == Seq(
                StringLiteralV2.from("""UpdatedDescription with "quotes" and <html tag>""", EN),
              ),
              group.project.contains(imagesProjectExternal),
              group.status,
              !group.selfjoin,
            ),
          )
      },
      test("return 'BadRequestException' if nothing would be changed during the update") {
        val updateReq = GroupUpdateRequest(None, None, None, None)
        groupRestService(_.putGroup(rootUser)(newGroupIri.asGroupIri, updateReq)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[BadRequestException](
              "No data would be changed. Aborting update request.",
            ),
          ),
        )
      },
      test("return 'DuplicateValueException' if the new group name already exists inside the project") {
        val groupName = GroupName.unsafeFrom("Image reviewer")
        val updateReq = GroupUpdateRequest(
          name = Some(groupName),
          descriptions = Some(
            GroupDescriptions
              .unsafeFrom(Seq(StringLiteralV2.from(value = "UpdatedDescription", EN))),
          ),
          status = Some(GroupStatus.active),
          selfjoin = Some(GroupSelfJoin.disabled),
        )
        groupRestService(_.putGroup(rootUser)(newGroupIri.asGroupIri, updateReq)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[DuplicateValueException](
              s"Group with name: '${groupName.value}' already exists.",
            ),
          ),
        )
      },
      test("return 'ForbiddenException' if a not-existing group IRI is submitted during update") {
        val groupIri  = GroupIri.unsafeFrom("http://rdfh.ch/groups/0000/notexisting")
        val updateReq = GroupUpdateRequest(
          name = Some(GroupName.unsafeFrom("UpdatedGroupName")),
          descriptions = Some(
            GroupDescriptions
              .unsafeFrom(Seq(StringLiteralV2.from(value = "UpdatedDescription", EN))),
          ),
          status = Some(GroupStatus.active),
          selfjoin = Some(GroupSelfJoin.disabled),
        )
        groupRestService(_.putGroup(rootUser)(groupIri, updateReq)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageContaining[ForbiddenException](
              s"Group with IRI '$groupIri' not found",
            ),
          ),
        )
      },
      test("return 'DuplicateValueException' if the new group name already exists inside the project") {
        val groupName = GroupName.unsafeFrom("Image reviewer")
        val updateReq = GroupUpdateRequest(
          name = Some(groupName),
          descriptions = Some(
            GroupDescriptions
              .unsafeFrom(Seq(StringLiteralV2.from(value = "UpdatedDescription", EN))),
          ),
          status = Some(GroupStatus.active),
          selfjoin = Some(GroupSelfJoin.disabled),
        )
        groupRestService(_.putGroup(rootUser)(newGroupIri.asGroupIri, updateReq)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[DuplicateValueException](
              s"Group with name: '${groupName.value}' already exists.",
            ),
          ),
        )
      },
      test("return 'BadRequestException' if nothing would be changed during the update") {
        val updateReq = GroupUpdateRequest(None, None, None, None)
        groupRestService(_.putGroup(rootUser)(GroupIri.unsafeFrom(newGroupIri.get), updateReq)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[BadRequestException](
              "No data would be changed. Aborting update request.",
            ),
          ),
        )
      },
    ),
    suite("used to query members")(
      test("return all members of a group identified by IRI") {
        groupRestService(_.getGroupMembers(rootUser)(imagesReviewerGroup.groupIri)).map { received =>
          val expectedIds = Seq(multiuserUser, imagesReviewerUser).map(_.id)
          val actualIds   = received.members.map(_.id)
          assert(actualIds)(hasSameElements(expectedIds))
        }
      },
      test("remove all members when group is deactivated") {
        val groupIri = imagesReviewerGroup.groupIri
        for {
          activeGroupBefore <- groupRestService(_.getGroupMembers(rootUser)(groupIri))
          statusChanged     <-
            groupRestService(_.putGroupStatus(rootUser)(groupIri, GroupStatusUpdateRequest(GroupStatus.inactive)))
          deactivatedGroup <- groupRestService(_.getGroupMembers(rootUser)(groupIri))
        } yield assertTrue(
          activeGroupBefore.members.size == 2,
          !statusChanged.group.status,
          deactivatedGroup.members.isEmpty,
        )
      },
      test("return 'ForbiddenException' when the group IRI is unknown") {
        val groupIri = GroupIri.unsafeFrom("http://rdfh.ch/groups/0000/notexisting")
        groupRestService(_.getGroupMembers(rootUser)(groupIri)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageContaining[ForbiddenException](
              s"Group with IRI '$groupIri' not found",
            ),
          ),
        )
      },
    ),
  )
}
