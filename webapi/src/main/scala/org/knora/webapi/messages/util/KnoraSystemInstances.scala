/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages.Project
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo

/**
 * This object represents built-in User and Project instances.
 */
object KnoraSystemInstances {

  object Users {

    private def toUser(kUser: KnoraUser): User =
      User(
        id = kUser.id.value,
        username = kUser.username.value,
        email = kUser.email.value,
        givenName = kUser.givenName.value,
        familyName = kUser.familyName.value,
        status = true,
        lang = kUser.preferredLanguage.value,
        password = None,
        groups = Seq.empty[Group],
        projects = Seq.empty[Project],
        permissions = PermissionsDataADM(),
      )

    /**
     * Represents the anonymous user.
     */
    val AnonymousUser: User = toUser(KnoraUserRepo.builtIn.AnonymousUser)

    /**
     * Represents the system user used internally.
     */
    val SystemUser: User = toUser(KnoraUserRepo.builtIn.SystemUser)
  }
}
