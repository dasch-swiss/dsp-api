package org.knora.webapi.slice.admin.repo

import zio.ZLayer

import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.store.cache.CacheService
import org.knora.webapi.store.triplestore.api.TriplestoreService

object AdminRepoModule {

  type Dependencies = TriplestoreService & CacheService
  type Provided     = KnoraGroupRepo & KnoraProjectRepo & KnoraUserRepo

  val layer = ZLayer.makeSome[Dependencies, Provided](
    KnoraGroupRepoLive.layer,
    KnoraProjectRepoLive.layer,
    KnoraUserRepoLive.layer,
  )
}
