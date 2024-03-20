/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.ZLayer

final case class GroupsService(
  private val groupService: KnoraGroupService,
) {}

object GroupsService {
  def layer = ZLayer.derive[GroupsService]
}
