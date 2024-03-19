package org.knora.webapi.slice.admin

import zio.ZLayer

import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.repo.AdminRepoModule
import org.knora.webapi.store.cache.CacheService
import org.knora.webapi.store.triplestore.api.TriplestoreService

object AdminModule {

  type Dependencies = TriplestoreService & CacheService
  type Provided     = KnoraGroupRepo & KnoraUserRepo & KnoraProjectRepo

  val layer: ZLayer[Dependencies, Nothing, Provided] = AdminRepoModule.layer
}
