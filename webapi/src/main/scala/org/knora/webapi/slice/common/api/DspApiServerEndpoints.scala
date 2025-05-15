package org.knora.webapi.slice.common.api

import org.knora.webapi.slice.shacl.api.ShaclServerEndpoints
import org.knora.webapi.slice.search.api.SearchServerEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementServerEndpoints
import sttp.capabilities.zio.ZioStreams
import org.knora.webapi.slice.security.api.AuthenticationServerEndpointsV2
import org.knora.webapi.slice.lists.api.ListsServerEndpointsV2
import org.knora.webapi.slice.resources.api.ResourcesApiServerEndpoints
import org.knora.webapi.slice.ontology.api.OntologiesServerEndpoints
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoServerEndpoints
import org.knora.webapi.slice.admin.api.AdminApiServerEndpoints
import sttp.tapir.ztapir.*
import zio.*

final case class DspApiServerEndpoints(
  private val adminApi: AdminApiServerEndpoints,
  private val authentication: AuthenticationServerEndpointsV2,
  private val listsApiV2: ListsServerEndpointsV2,
  private val managementRoutes: ManagementServerEndpoints,
  private val ontologiesRoutes: OntologiesServerEndpoints,
  private val resourceInfo: ResourceInfoServerEndpoints,
  private val resourcesApi: ResourcesApiServerEndpoints,
  private val searchApiRoutes: SearchServerEndpoints,
  private val shaclApiRoutes: ShaclServerEndpoints,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] =
    adminApi.serverEndpoints ++
      authentication.serverEndpoints ++
      listsApiV2.serverEndpoints ++
      managementRoutes.serverEndpoints ++
      ontologiesRoutes.serverEndpoints ++
      resourceInfo.serverEndpoints ++
      resourcesApi.serverEndpoints ++
      searchApiRoutes.serverEndpoints ++
      shaclApiRoutes.serverEndpoints
}
object DspApiServerEndpoints {
  val layer = ZLayer.derive[DspApiServerEndpoints]
}
