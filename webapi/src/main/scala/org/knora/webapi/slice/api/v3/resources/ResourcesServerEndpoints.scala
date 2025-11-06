package org.knora.webapi.slice.api.v3.resources
import sttp.tapir.ztapir.ZServerEndpoint
import zio.*

final case class ResourcesServerEndpoints(
  private val resourcesEndpoints: ResourcesEndpoints,
  private val resourcesRestService: ResourcesRestServiceV3,
) {

  val endpoints: List[ZServerEndpoint[Any, Any]] = List(
    resourcesEndpoints.getResourcesResourcesPerOntology.serverLogic(resourcesRestService.resourcesPerOntology),
  )
}

object ResourcesServerEndpoints {
  val layer = ZLayer.derive[ResourcesServerEndpoints]
}
