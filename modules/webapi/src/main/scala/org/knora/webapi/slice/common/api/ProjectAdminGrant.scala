/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User

/**
 * An authenticated, active caller not yet authorized for any project. Handlers cannot read the user out of it; only
 * [[AuthorizationRestService.authorizeProjectAdminById]] turns it into a [[ProjectAdminGrant]]. A plain class, because
 * a case class `unapply` would hand out the user.
 */
final class ProjectCandidate private[api] (private[api] val user: User)

/**
 * Proof that `user` passed the ProjectAdmin rule for `project`; only [[AuthorizationRestService]] issues it. The fields
 * are public: after authorization the domain acts on them.
 */
final case class ProjectAdminGrant private[api] (user: User, project: KnoraProject)
