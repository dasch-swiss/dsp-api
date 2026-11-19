/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security

import org.knora.webapi.slice.admin.domain.model.User

/** Who may call an export endpoint: a SystemAdmin user, or a trusted service, which is not a user. */
enum ExportCaller {
  case Admin(user: User)
  case Service(principal: ServicePrincipal)
}
