/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User

/**
 * The reason a supplied on-behalf-of user is rejected for a project data import. The `reason` string is a cross-repo
 * contract read by dsp-tools as `details["reason"]`; keep the values stable.
 */
enum OnBehalfOfIneligibility(val reason: String) {
  case MalformedIdentifier extends OnBehalfOfIneligibility("malformed_identifier")
  case IsSystemAdmin       extends OnBehalfOfIneligibility("is_system_admin")
  case NotProjectMember    extends OnBehalfOfIneligibility("not_project_member")
  case Inactive            extends OnBehalfOfIneligibility("inactive")
  case CannotCreate        extends OnBehalfOfIneligibility("cannot_create")
}

object OnBehalfOfUserEligibility {

  /**
   * Returns the first failing reason, or `None` if the user is eligible. The check order is part of the
   * request contract, so reordering it changes which reason a caller sees.
   */
  def check(user: User, projectIri: ProjectIri): Option[OnBehalfOfIneligibility] =
    if (user.isSystemAdmin) Some(OnBehalfOfIneligibility.IsSystemAdmin)
    else if (!(user.isProjectMember(projectIri) || user.isProjectAdmin(projectIri)))
      Some(OnBehalfOfIneligibility.NotProjectMember)
    else if (!user.isActive) Some(OnBehalfOfIneligibility.Inactive)
    else if (!canCreate(user, projectIri)) Some(OnBehalfOfIneligibility.CannotCreate)
    else None

  /**
   * Project-wide create right. A user with only class-restricted create rights is rejected: the import spans arbitrary
   * classes unknown at trigger time, so the class-restricted grant cannot be checked up front.
   */
  private def canCreate(user: User, projectIri: ProjectIri): Boolean =
    user.permissions.administrativePermissionsPerProject
      .get(projectIri.value)
      .exists(_.contains(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll)))
}
