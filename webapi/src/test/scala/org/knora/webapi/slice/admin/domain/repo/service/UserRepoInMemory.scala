/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.repo.service

import zio.Ref
import zio.ULayer
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.UserRepo
import org.knora.webapi.slice.common.repo.service.AbstractInMemoryCrudRepository
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

final case class UserRepoInMemory(users: Ref[List[User]])
    extends AbstractInMemoryCrudRepository[User, InternalIri](users, _.id)
    with UserRepo {}

object UserRepoInMemory {
  val layer: ULayer[UserRepoInMemory] =
    ZLayer.fromZIO(Ref.make(List.empty[User]).map(UserRepoInMemory(_)))
}
