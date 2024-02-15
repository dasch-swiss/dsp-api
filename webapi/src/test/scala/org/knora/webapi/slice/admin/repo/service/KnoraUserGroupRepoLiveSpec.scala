/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import zio.Ref
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraUserGroup
import org.knora.webapi.slice.admin.domain.service.KnoraUserGroupRepo
import org.knora.webapi.slice.common.repo.AbstractInMemoryCrudRepository

final case class KnoraUserGroupRepoInMemory(groups: Ref[List[KnoraUserGroup]])
    extends AbstractInMemoryCrudRepository[KnoraUserGroup, GroupIri](groups, _.id)
    with KnoraUserGroupRepo {}

object KnoraUserGroupRepoInMemory {
  val layer = ZLayer.fromZIO(Ref.make(List.empty[KnoraUserGroup])) >>>
    ZLayer.derive[KnoraUserGroupRepoInMemory]
}
