/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

/**
 * This object represents built-in User and Project instances.
 */
object KnoraSystemInstances {

  object Users {

    /**
     * Represents the anonymous user.
     */
    val AnonymousUser = UserADM(
      id = OntologyConstants.KnoraAdmin.AnonymousUser,
      username = "anonymous",
      email = "anonymous@localhost",
      password = None,
      token = None,
      givenName = "Knora",
      familyName = "Anonymous",
      status = true,
      lang = "en",
      groups = Seq.empty[GroupADM],
      projects = Seq.empty[ProjectADM],
      sessionId = None,
      permissions = PermissionsDataADM()
    )

    /**
     * Represents the system user used internally.
     */
    val SystemUser = UserADM(
      id = OntologyConstants.KnoraAdmin.SystemUser,
      username = "system",
      email = "system@localhost",
      password = None,
      token = None,
      givenName = "Knora",
      familyName = "System",
      status = true,
      lang = "en",
      groups = Seq.empty[GroupADM],
      projects = Seq.empty[ProjectADM],
      sessionId = None,
      permissions = PermissionsDataADM()
    )
  }

}
