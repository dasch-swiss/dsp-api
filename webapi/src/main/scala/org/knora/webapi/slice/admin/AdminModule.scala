/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin

import zio.ZLayer

import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.admin.domain.AdminDomainModule
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.repo.AdminRepoModule
import org.knora.webapi.store.cache.CacheService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper

object AdminModule {

  type Dependencies =
    TriplestoreService & CacheService & IriService & PasswordService & CacheService & PredicateObjectMapper

  type Provided =
    KnoraGroupRepo & KnoraUserRepo & KnoraProjectRepo & KnoraUserService & MaintenanceService & KnoraProjectService

  val layer: ZLayer[Dependencies, Nothing, Provided] = AdminRepoModule.layer >+> AdminDomainModule.layer
}
