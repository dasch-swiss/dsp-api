/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import java.util.UUID

import dsp.errors.*
import dsp.valueobjects.Group.*
import dsp.valueobjects.V2
import org.knora.webapi.*
import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.messages.admin.responder.usersmessages.*
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.util.MutableTestIri
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

/**
 * This spec is used to test the messages received by the [[GroupsResponderADMSpec]] actor.
 */
class GroupsResponderADMSpec extends CoreSpec {
  "The GroupsResponder " when {
    "asked about all groups" should {
      "return a list" in {
        val groups = UnsafeZioRun.runOrThrow(GroupsResponderADM.groupsGetADM)
        assert(groups.nonEmpty)
        assert(groups.size == 2)
      }
    }

    "asked about a group identified by 'iri' " should {
      "return group info if the group is known " in {
        val group = UnsafeZioRun.runOrThrow(GroupsResponderADM.groupGetADM(imagesReviewerGroup.id))
        assert(group.nonEmpty)
        assert(group.map(_.id).contains(imagesReviewerGroup.id))
      }

      "return 'None' when the group is unknown " in {
        val iri      = "http://rdfh.ch/groups/notexisting"
        val response = UnsafeZioRun.runOrThrow(GroupsResponderADM.groupGetADM(iri))
        assert(response.isEmpty)
      }
    }

    "used to modify group information" should {
      val newGroupIri = new MutableTestIri

      "CREATE the group and return the group's info if the supplied group name is unique" in {
        val response = UnsafeZioRun.runOrThrow(
          GroupsResponderADM.createGroup(
            GroupCreateRequest(
              id = None,
              name = GroupName.unsafeFrom("NewGroup"),
              descriptions = GroupDescriptions
                .unsafeFrom(
                  Seq(
                    V2.StringLiteralV2(
                      value = """NewGroupDescription with "quotes" and <html tag>""",
                      language = Some("en")
                    )
                  )
                ),
              project = ProjectIri.unsafeFrom(imagesProjectIri),
              status = GroupStatus.active,
              selfjoin = GroupSelfJoin.impossible
            ),
            UUID.randomUUID
          )
        )

        val newGroupInfo = response.group
        newGroupInfo.name should equal("NewGroup")
        newGroupInfo.descriptions should equal(
          Seq(StringLiteralV2("""NewGroupDescription with "quotes" and <html tag>""", Some("en")))
        )
        newGroupInfo.project should equal(imagesProject)
        newGroupInfo.status should equal(true)
        newGroupInfo.selfjoin should equal(false)

        // store for later usage
        newGroupIri.set(newGroupInfo.id)
      }

      "return a 'DuplicateValueException' if the supplied group name is not unique" in {
        val groupName = GroupName.unsafeFrom("NewGroup")
        val exit = UnsafeZioRun.run(
          GroupsResponderADM.createGroup(
            GroupCreateRequest(
              id = Some(GroupIri.unsafeFrom(imagesReviewerGroup.id)),
              name = groupName,
              descriptions = GroupDescriptions
                .unsafeFrom(Seq(V2.StringLiteralV2(value = "NewGroupDescription", language = Some("en")))),
              project = ProjectIri.unsafeFrom(imagesProjectIri),
              status = GroupStatus.active,
              selfjoin = GroupSelfJoin.impossible
            ),
            UUID.randomUUID
          )
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"Group with the name '${groupName.value}' already exists"
        )
      }

      "UPDATE a group" in {
        val response = UnsafeZioRun.runOrThrow(
          GroupsResponderADM.updateGroup(
            groupIri = GroupIri.unsafeFrom(newGroupIri.get),
            GroupUpdateRequest(
              name = Some(GroupName.unsafeFrom("UpdatedGroupName")),
              descriptions = Some(
                GroupDescriptions.unsafeFrom(
                  Seq(V2.StringLiteralV2(value = """UpdatedDescription with "quotes" and <html tag>""", Some("en")))
                )
              ),
              status = Some(GroupStatus.active),
              selfjoin = Some(GroupSelfJoin.impossible)
            ),
            UUID.randomUUID
          )
        )
        val updatedGroupInfo = response.group
        updatedGroupInfo.name should equal("UpdatedGroupName")
        updatedGroupInfo.descriptions should equal(
          Seq(StringLiteralV2("""UpdatedDescription with "quotes" and <html tag>""", Some("en")))
        )
        updatedGroupInfo.project should equal(imagesProject)
        updatedGroupInfo.status should equal(true)
        updatedGroupInfo.selfjoin should equal(false)
      }

      "return 'NotFound' if a not-existing group IRI is submitted during update" in {
        val groupIri = "http://rdfh.ch/groups/0000/notexisting"
        val exit = UnsafeZioRun.run(
          GroupsResponderADM.updateGroup(
            groupIri = GroupIri.unsafeFrom(groupIri),
            GroupUpdateRequest(
              name = Some(GroupName.unsafeFrom("UpdatedGroupName")),
              descriptions = Some(
                GroupDescriptions
                  .unsafeFrom(Seq(V2.StringLiteralV2(value = "UpdatedDescription", language = Some("en"))))
              ),
              status = Some(GroupStatus.active),
              selfjoin = Some(GroupSelfJoin.impossible)
            ),
            UUID.randomUUID
          )
        )
        assertFailsWithA[NotFoundException](
          exit,
          s"Group <$groupIri> not found. Aborting update request."
        )
      }

      "return 'BadRequest' if the new group name already exists inside the project" in {
        val groupName = GroupName.unsafeFrom("Image reviewer")
        val exit = UnsafeZioRun.run(
          GroupsResponderADM.updateGroup(
            GroupIri.unsafeFrom(newGroupIri.get),
            GroupUpdateRequest(
              name = Some(groupName),
              descriptions = Some(
                GroupDescriptions
                  .unsafeFrom(Seq(V2.StringLiteralV2(value = "UpdatedDescription", language = Some("en"))))
              ),
              status = Some(GroupStatus.active),
              selfjoin = Some(GroupSelfJoin.impossible)
            ),
            UUID.randomUUID
          )
        )
        assertFailsWithA[BadRequestException](
          exit,
          s"Group with the name '${groupName.value}' already exists."
        )
      }

      "return 'BadRequest' if nothing would be changed during the update" in {
        an[BadRequestException] should be thrownBy ChangeGroupApiRequestADM(None, None, None, None)
      }
    }

    "used to query members" should {
      "return all members of a group identified by IRI" in {
        val iri = GroupIri.unsafeFrom(imagesReviewerGroup.id)
        val received =
          UnsafeZioRun.runOrThrow(GroupsResponderADM.groupMembersGetRequest(iri, rootUser))

        received.members.map(_.id) should contain allElementsOf Seq(
          multiuserUser.ofType(UserInformationType.Restricted),
          imagesReviewerUser.ofType(UserInformationType.Restricted)
        ).map(_.id)
      }

      "remove all members when group is deactivated" in {
        val group = UnsafeZioRun.runOrThrow(
          GroupsResponderADM.groupMembersGetRequestADM(
            GroupIri.unsafeFrom(imagesReviewerGroup.id).value,
            rootUser
          )
        )
        group.members.size shouldBe 2

        val statusChangeResponse = UnsafeZioRun.runOrThrow(
          GroupsResponderADM.updateGroupStatus(
            GroupIri.unsafeFrom(imagesReviewerGroup.id),
            GroupUpdateRequest(status = Some(GroupStatus.inactive)),
            UUID.randomUUID()
          )
        )
        statusChangeResponse.group.status shouldBe false

        val anotherGroup = UnsafeZioRun.runOrThrow(
          GroupsResponderADM.groupMembersGetRequest(
            GroupIri.unsafeFrom(imagesReviewerGroup.id),
            rootUser
          )
        )
        anotherGroup.members.size shouldBe 0
      }

      "return 'NotFound' when the group IRI is unknown" in {
        val groupIri = "http://rdfh.ch/groups/0000/notexisting"
        val exit = UnsafeZioRun.run(
          GroupsResponderADM.groupMembersGetRequest(
            GroupIri.unsafeFrom(groupIri),
            rootUser
          )
        )
        assertFailsWithA[NotFoundException](
          exit,
          s"Group <$groupIri> not found"
        )
      }
    }
  }
}
