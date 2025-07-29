/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionRepo
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermissionRepo
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.repo.service.AdministrativePermissionRepoLive
import org.knora.webapi.slice.admin.repo.service.DefaultObjectAccessPermissionRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.infrastructure.CacheManager
import org.knora.webapi.store.triplestore.api.TriplestoreService

object AdminRepoModule { self =>

  type Dependencies = CacheManager & TriplestoreService

  type Provided =
    // format: off
    AdministrativePermissionRepo &
    DefaultObjectAccessPermissionRepo &
    LicenseRepo &
    KnoraGroupRepo &
    KnoraProjectRepo &
    KnoraUserRepo
    // format: on

  val layer: URLayer[self.Dependencies, self.Provided] = ZLayer.makeSome[self.Dependencies, self.Provided](
    KnoraGroupRepoLive.layer,
    KnoraProjectRepoLive.layer,
    KnoraUserRepoLive.layer,
    AdministrativePermissionRepoLive.layer,
    DefaultObjectAccessPermissionRepoLive.layer,
    LicenseRepo.layer,
  )
}
