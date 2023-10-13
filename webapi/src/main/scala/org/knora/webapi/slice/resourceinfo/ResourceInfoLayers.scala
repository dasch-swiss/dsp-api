package org.knora.webapi.slice.resourceinfo

import org.knora.webapi.slice.common.api.{BaseEndpoints, HandlerMapper, TapirToPekkoInterpreter}
import org.knora.webapi.slice.resourceinfo.api.{ResourceInfoEndpoints, ResourceInfoRoutes}
import org.knora.webapi.slice.resourceinfo.api.service.{RestResourceInfoService, RestResourceInfoServiceLive}
import org.knora.webapi.slice.resourceinfo.domain.{IriConverter, ResourceInfoRepo}
import org.knora.webapi.slice.resourceinfo.repo.ResourceInfoRepoLive
import org.knora.webapi.store.triplestore.api.TriplestoreService
import zio.ZLayer

object ResourceInfoLayers {

  val live: ZLayer[
    TriplestoreService with IriConverter with BaseEndpoints with HandlerMapper with TapirToPekkoInterpreter,
    Nothing,
    RestResourceInfoService with ResourceInfoEndpoints with ResourceInfoRoutes
  ] =
    ResourceInfoRepoLive.layer >>> RestResourceInfoServiceLive.layer >+> ResourceInfoEndpoints.layer >+> ResourceInfoRoutes.layer

}
