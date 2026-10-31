/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import org.knora.webapi.slice.admin.domain.model.User

/**
 * Gives tests outside this package a [[ProjectCandidate]], as the ProjectAdmin endpoint factory would. Tests obtain a
 * [[ProjectAdminGrant]] from it through [[AuthorizationRestService]], as production does; there is no grant shortcut.
 */
object TestCandidates {
  def of(user: User): ProjectCandidate = new ProjectCandidate(user)
}
