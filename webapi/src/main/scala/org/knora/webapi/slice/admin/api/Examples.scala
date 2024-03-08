package org.knora.webapi.slice.admin.api

import dsp.valueobjects.V2
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.admin.domain.model.GroupDescriptions
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.GroupSelfJoin
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

object Examples {

  object ProjectExample {
    val projectIri: ProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0042")
  }

  object GroupExample {
    val groupIri: GroupIri = GroupIri.unsafeFrom("http://rdfh.ch/groups/0042/a95UWs71KUklnFOe1rcw1w")

    val groupName: GroupName = GroupName.unsafeFrom("NewGroup")

    val groupDescriptions: GroupDescriptions = GroupDescriptions.unsafeFrom(
      Seq(
        V2.StringLiteralV2(s"${groupName.value} description in English", Some("en")),
        V2.StringLiteralV2(s"${groupName.value} Beschreibung auf Deutsch", Some("de")),
      ),
    )
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

    val groupUpdateRequest = GroupUpdateRequest(
      name = Option(newGroupName),
      descriptions = Option(
        GroupDescriptions.unsafeFrom(
          Seq(
            V2.StringLiteralV2(s"${newGroupName.value} description in English", Some("en")),
            V2.StringLiteralV2(s"${newGroupName.value} Beschreibung auf Deutsch", Some("de")),
          ),
        ),
      ),
      status = Option(GroupStatus.inactive),
      selfjoin = Option(GroupSelfJoin.enabled),
    )
  }
}
