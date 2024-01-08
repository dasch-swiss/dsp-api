/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.slice.admin.domain.model
import org.knora.webapi.slice.admin.domain.model
import org.knora.webapi.slice.admin.domain.model.User

/**
 * This object represents built-in User and Project instances.
 */
object KnoraSystemInstances {

  object Users {

    /**
     * Represents the anonymous user.
     */
    val AnonymousUser = model.User(
      id = OntologyConstants.KnoraAdmin.AnonymousUser,
      username = "anonymous",
      email = "anonymous@localhost",
      givenName = "Knora",
      familyName = "Anonymous",
      status = true,
      lang = "en",
      password = None,
      token = None,
      groups = Seq.empty[GroupADM],
      projects = Seq.empty[ProjectADM],
      permissions = PermissionsDataADM()
    )

    /**
     * Represents the system user used internally.
     */
    val SystemUser = model.User(
      id = OntologyConstants.KnoraAdmin.SystemUser,
      username = "system",
      email = "system@localhost",
      givenName = "Knora",
      familyName = "System",
      status = true,
      lang = "en",
      password = None,
      token = None,
      groups = Seq.empty[GroupADM],
      projects = Seq.empty[ProjectADM],
      permissions = PermissionsDataADM()
    )
  }

}
