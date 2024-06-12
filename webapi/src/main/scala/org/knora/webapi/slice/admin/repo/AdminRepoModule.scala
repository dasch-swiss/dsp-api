/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionRepo
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.repo.service.AdministrativePermissionRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.store.triplestore.api.TriplestoreService

object AdminRepoModule {

  type Dependencies = 
    // format: off
    TriplestoreService & 
    CacheManager
    // format: on

  type Provided =
    // format: off
    AdministrativePermissionRepo & 
    KnoraGroupRepo & 
    KnoraProjectRepo & 
    KnoraUserRepo
    // format: on

  val layer = ZLayer.makeSome[Dependencies, Provided](
    KnoraGroupRepoLive.layer,
    KnoraProjectRepoLive.layer,
    KnoraUserRepoLive.layer,
    AdministrativePermissionRepoLive.layer,
  )
}
