/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain

import zio.ZLayer

import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.repo.AdminRepoModule
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.store.cache.CacheService
import org.knora.webapi.store.triplestore.api.TriplestoreService

object AdminDomainModule {

  type Dependencies =
    AdminRepoModule.Provided & TriplestoreService & IriService & PasswordService & CacheService & PredicateObjectMapper
  type Provided = KnoraGroupService & KnoraUserService & KnoraProjectService & MaintenanceService

  val layer = ZLayer.makeSome[Dependencies, Provided](
    KnoraGroupService.layer,
    KnoraProjectService.layer,
    KnoraUserService.layer,
    MaintenanceService.layer,
  )
}
