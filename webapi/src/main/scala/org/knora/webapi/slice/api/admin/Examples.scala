/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import org.knora.webapi.messages.admin.responder.groupsmessages.GroupGetResponseADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.usersmessages.GroupMembersGetResponseADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.GroupDescriptions
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.GroupSelfJoin
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Longname
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.SelfJoin.CannotJoin
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Status.Active
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse as ApiPagedResponse
import org.knora.webapi.slice.api.admin.Examples.GroupExample.groupName
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.domain.LanguageCode.DE
import org.knora.webapi.slice.common.domain.LanguageCode.EN

object Examples {

  object PagedResponse {
    def fromSlice[A](data: Seq[A]): ApiPagedResponse[A] =
      ApiPagedResponse.from(data, data.size * 5, PageAndSize(1, data.size))

    def fromTotal[A](data: Seq[A]): ApiPagedResponse[A] =
      ApiPagedResponse.from(data, data.size, PageAndSize(1, data.size))
  }

  object ProjectExample {
    val projectIri: ProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0042")
  }

  object GroupExample {
    val groupIri: GroupIri = GroupIri.unsafeFrom("http://rdfh.ch/groups/0042/a95UWs71KUklnFOe1rcw1w")

    val groupName: GroupName = GroupName.unsafeFrom("NewGroup")

    val groupDescriptions: GroupDescriptions = GroupDescriptions.unsafeFrom(
      Seq(
        StringLiteralV2.from(s"${groupName.value} description in English", EN),
        StringLiteralV2.from(s"${groupName.value} Beschreibung auf Deutsch", DE),
      ),
    )
  }

  object UserExample {
    val userIri: UserIri       = UserIri.unsafeFrom("http://rdfh.ch/users/0001")
    val username: Username     = Username.unsafeFrom("username")
    val email: Email           = Email.unsafeFrom("user@exampl.com")
    val familyName: FamilyName = FamilyName.unsafeFrom("Doe")
    val givenName: GivenName   = GivenName.unsafeFrom("Jane")
    val userStatus: UserStatus = UserStatus.Active
  }

  object GroupEndpointsExample {
    val groupCreateRequest: GroupCreateRequest = GroupCreateRequest(
      name = GroupExample.groupName,
      descriptions = GroupExample.groupDescriptions,
      project = ProjectExample.projectIri,
      status = GroupStatus.active,
      selfjoin = GroupSelfJoin.disabled,
    )

    private val newGroupName: GroupName = GroupName.unsafeFrom("NewGroupNewName")

    val groupUpdateRequest: GroupUpdateRequest = GroupUpdateRequest(
      name = Option(newGroupName),
      descriptions = Option(
        GroupDescriptions.unsafeFrom(
          Seq(
            StringLiteralV2.from(s"${newGroupName.value} description in English", EN),
            StringLiteralV2.from(s"${newGroupName.value} Beschreibung auf Deutsch", DE),
          ),
        ),
      ),
      status = Option(GroupStatus.inactive),
      selfjoin = Option(GroupSelfJoin.enabled),
    )

    private val project: Project = Project(
      id = ProjectExample.projectIri,
      shortname = Shortname.unsafeFrom("example"),
      shortcode = Shortcode.unsafeFrom("0001"),
      longname = Some(Longname.unsafeFrom("Example Project")),
      description = Seq(StringLiteralV2.from("An example project", EN)),
      keywords = Seq("example", "project"),
      logo = None,
      status = Active,
      ontologies = Seq.empty,
      selfjoin = CannotJoin,
      allowedCopyrightHolders = Set.empty,
      enabledLicenses = LicenseIri.BUILT_IN,
    )

    private val group = Group(
      id = GroupExample.groupIri.value,
      name = groupName.value,
      descriptions = Seq(
        StringLiteralV2.from(s"${groupName.value} description in English", EN),
        StringLiteralV2.from(s"${groupName.value} Beschreibung auf Deutsch", DE),
      ),
      project = Some(project),
      status = GroupStatus.active.value,
      selfjoin = GroupSelfJoin.disabled.value,
    )

    val groupGetResponseADM: GroupGetResponseADM = GroupGetResponseADM(group)

    val groupsGetResponseADM: GroupsGetResponseADM = GroupsGetResponseADM(Seq(group))

    private val user = User(
      id = UserExample.userIri.value,
      username = UserExample.username.value,
      email = UserExample.email.value,
      givenName = UserExample.givenName.value,
      familyName = UserExample.familyName.value,
      status = UserExample.userStatus.value,
      lang = "rm",
      password = None,
      groups = Seq(group),
      projects = Seq(project),
      permissions = PermissionsDataADM.apply(Map.empty),
    )
    val groupGetMembersResponse: GroupMembersGetResponseADM = GroupMembersGetResponseADM.from(Seq(user))
  }
}
