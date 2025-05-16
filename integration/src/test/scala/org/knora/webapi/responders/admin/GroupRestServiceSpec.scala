/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.*

import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.messages.admin.responder.usersmessages.*
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupStatusUpdateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.admin.api.service.GroupRestService
import org.knora.webapi.slice.admin.domain.model.GroupDescriptions
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.GroupSelfJoin
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.service.GroupService
import org.knora.webapi.util.MutableTestIri
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

class GroupRestServiceSpec extends CoreSpec {
  private val groupRestService = ZIO.serviceWithZIO[GroupRestService]
  private val groupService     = ZIO.serviceWithZIO[GroupService]

  "The GroupsResponder " when {
    "asked about all groups" should {
      "return a list" in {
        val response = UnsafeZioRun.runOrThrow(groupRestService(_.getGroups(())))
        assert(response.groups.nonEmpty)
        assert(response.groups.size == 2)
      }
    }

    "asked about a group identified by IRI " should {
      "return group info if the group is known " in {
        val iri      = GroupIri.unsafeFrom(imagesReviewerGroup.id)
        val response = UnsafeZioRun.runOrThrow(groupService(_.findById(iri)))
        assert(response.nonEmpty)
        assert(response.map(_.id).contains(imagesReviewerGroup.id))
      }

      "return 'None' when the group is unknown " in {
        val iri      = GroupIri.unsafeFrom("http://rdfh.ch/groups/0987/notexisting")
        val response = UnsafeZioRun.runOrThrow(groupService(_.findById(iri)))
        assert(response.isEmpty)
      }
    }

    "used to modify group information" should {
      val newGroupIri = new MutableTestIri

      "CREATE the group and return the group's info if the supplied group name is unique" in {
        val response = UnsafeZioRun.runOrThrow(
          groupRestService(
            _.postGroup(rootUser)(
              GroupCreateRequest(
                id = None,
                name = GroupName.unsafeFrom("NewGroup"),
                descriptions = GroupDescriptions
                  .unsafeFrom(
                    Seq(
                      StringLiteralV2.from(
                        value = """NewGroupDescription with "quotes" and <html tag>""",
                        language = Some("en"),
                      ),
                    ),
                  ),
                project = imagesProjectIri,
                status = GroupStatus.active,
                selfjoin = GroupSelfJoin.disabled,
              ),
            ),
          ),
        )

        val group = response.group
        group.name should equal("NewGroup")
        group.descriptions should equal(
          Seq(StringLiteralV2.from("""NewGroupDescription with "quotes" and <html tag>""", Some("en"))),
        )
        group.project should equal(Some(imagesProjectExternal))
        group.status should equal(true)
        group.selfjoin should equal(false)

        // store for later usage
        newGroupIri.set(group.id)
      }

      "return a 'DuplicateValueException' if the supplied group name is not unique" in {
        val groupName = GroupName.unsafeFrom("NewGroup")
        val exit = UnsafeZioRun.run(
          groupRestService(
            _.postGroup(rootUser)(
              GroupCreateRequest(
                id = Some(GroupIri.unsafeFrom(imagesReviewerGroup.id)),
                name = groupName,
                descriptions = GroupDescriptions
                  .unsafeFrom(Seq(StringLiteralV2.from(value = "NewGroupDescription", language = Some("en")))),
                project = imagesProjectIri,
                status = GroupStatus.active,
                selfjoin = GroupSelfJoin.disabled,
              ),
            ),
          ),
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"Group with name: '${groupName.value}' already exists.",
        )
      }

      "UPDATE a group" in {
        val response = UnsafeZioRun.runOrThrow(
          groupRestService(
            _.putGroup(rootUser)(
              GroupIri.unsafeFrom(newGroupIri.get),
              GroupUpdateRequest(
                name = Some(GroupName.unsafeFrom("UpdatedGroupName")),
                descriptions = Some(
                  GroupDescriptions.unsafeFrom(
                    Seq(StringLiteralV2.from("""UpdatedDescription with "quotes" and <html tag>""", Some("en"))),
                  ),
                ),
                status = Some(GroupStatus.active),
                selfjoin = Some(GroupSelfJoin.disabled),
              ),
            ),
          ),
        )
        val group = response.group
        group.name should equal("UpdatedGroupName")
        group.descriptions should equal(
          Seq(StringLiteralV2.from("""UpdatedDescription with "quotes" and <html tag>""", Some("en"))),
        )
        group.project should equal(Some(imagesProjectExternal))
        group.status should equal(true)
        group.selfjoin should equal(false)
      }

      "return 'ForbiddenException' if a not-existing group IRI is submitted during update" in {
        val groupIri = "http://rdfh.ch/groups/0000/notexisting"
        val exit = UnsafeZioRun.run(
          groupRestService(
            _.putGroup(rootUser)(
              GroupIri.unsafeFrom(groupIri),
              GroupUpdateRequest(
                name = Some(GroupName.unsafeFrom("UpdatedGroupName")),
                descriptions = Some(
                  GroupDescriptions
                    .unsafeFrom(Seq(StringLiteralV2.from(value = "UpdatedDescription", language = Some("en")))),
                ),
                status = Some(GroupStatus.active),
                selfjoin = Some(GroupSelfJoin.disabled),
              ),
            ),
          ),
        )
        assertFailsWithA[ForbiddenException](
          exit,
          s"Group with IRI '$groupIri' not found",
        )
      }

      "return 'DuplicateValueException' if the new group name already exists inside the project" in {
        val groupName = GroupName.unsafeFrom("Image reviewer")
        val exit = UnsafeZioRun.run(
          groupRestService(
            _.putGroup(rootUser)(
              GroupIri.unsafeFrom(newGroupIri.get),
              GroupUpdateRequest(
                name = Some(groupName),
                descriptions = Some(
                  GroupDescriptions
                    .unsafeFrom(Seq(StringLiteralV2.from(value = "UpdatedDescription", language = Some("en")))),
                ),
                status = Some(GroupStatus.active),
                selfjoin = Some(GroupSelfJoin.disabled),
              ),
            ),
          ),
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"Group with name: '${groupName.value}' already exists.",
        )
      }

      "return 'BadRequestException' if nothing would be changed during the update" in {
        val exit = UnsafeZioRun.run(
          groupRestService(
            _.putGroup(rootUser)(
              GroupIri.unsafeFrom(newGroupIri.get),
              GroupUpdateRequest(None, None, None, None),
            ),
          ),
        )
        assertFailsWithA[BadRequestException](
          exit,
          "No data would be changed. Aborting update request.",
        )
      }
    }

    "used to query members" should {
      "return all members of a group identified by IRI" in {
        val iri = GroupIri.unsafeFrom(imagesReviewerGroup.id)
        val received = UnsafeZioRun.runOrThrow(
          groupRestService(_.getGroupMembers(rootUser)(iri)),
        )

        received.members.map(_.id) should contain allElementsOf Seq(
          multiuserUser.ofType(UserInformationType.Restricted),
          imagesReviewerUser.ofType(UserInformationType.Restricted),
        ).map(_.id)
      }

      "remove all members when group is deactivated" in {
        val group = UnsafeZioRun.runOrThrow(
          groupRestService(_.getGroupMembers(rootUser)(GroupIri.unsafeFrom(imagesReviewerGroup.id))),
        )
        group.members.size shouldBe 2

        val statusChangeResponse = UnsafeZioRun.runOrThrow(
          groupRestService(
            _.putGroupStatus(rootUser)(
              GroupIri.unsafeFrom(imagesReviewerGroup.id),
              GroupStatusUpdateRequest(GroupStatus.inactive),
            ),
          ),
        )
        statusChangeResponse.group.status shouldBe false

        val deactivatedGroup = UnsafeZioRun.runOrThrow(
          groupRestService(_.getGroupMembers(rootUser)(GroupIri.unsafeFrom(imagesReviewerGroup.id))),
        )
        deactivatedGroup.members.size shouldBe 0
      }

      "return 'ForbiddenException' when the group IRI is unknown" in {
        val groupIri = "http://rdfh.ch/groups/0000/notexisting"
        val exit = UnsafeZioRun.run(
          groupRestService(_.getGroupMembers(rootUser)(GroupIri.unsafeFrom(groupIri))),
        )
        assertFailsWithA[ForbiddenException](
          exit,
          s"Group with IRI '$groupIri' not found",
        )
      }
    }
  }
}
