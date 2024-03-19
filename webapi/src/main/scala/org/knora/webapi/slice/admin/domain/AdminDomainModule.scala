/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain

import zio.ZLayer
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.responders.IriService
import org.knora.webapi.store.cache.CacheService
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.store.triplestore.api.TriplestoreService

object AdminDomainModule {

  type Dependencies =
    KnoraProjectRepo & TriplestoreService & KnoraUserRepo & IriService & PasswordService & CacheService & PredicateObjectMapper
  type Provided = KnoraUserService & MaintenanceService

  val layer = ZLayer.makeSome[Dependencies, Provided](
    KnoraUserService.layer,
    MaintenanceService.layer,
  )
}
