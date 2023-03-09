package org.knora.webapi.responders.v2.ontology
import scala.concurrent.Future

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.ontology.repo.model.OntologyCacheData
import org.knora.webapi.slice.ontology.repo.service.OntologyCache

object OntologyCacheService {

  def getCacheData(implicit runtime: zio.Runtime[OntologyCache]): Future[OntologyCacheData] =
    UnsafeZioRun.runToFuture(OntologyCache.getCacheData)

  def cacheUpdatedOntologyWithoutUpdatingMaps(
    updatedOntologyIri: SmartIri,
    updatedOntologyData: ReadOntologyV2
  )(implicit runtime: zio.Runtime[OntologyCache]): Future[OntologyCacheData] =
    UnsafeZioRun.runToFuture(
      OntologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(updatedOntologyIri, updatedOntologyData)
    )

  def cacheUpdatedOntologyWithClass(
    updatedOntologyIri: SmartIri,
    updatedOntologyData: ReadOntologyV2,
    updatedClassIri: SmartIri
  )(implicit runtime: zio.Runtime[OntologyCache]): Future[OntologyCacheData] =
    UnsafeZioRun.runToFuture(
      OntologyCache.cacheUpdatedOntologyWithClass(updatedOntologyIri, updatedOntologyData, updatedClassIri)
    )

  def cacheUpdatedOntology(updatedOntologyIri: SmartIri, updatedOntologyData: ReadOntologyV2)(implicit
    runtime: zio.Runtime[OntologyCache]
  ): Future[OntologyCacheData] =
    UnsafeZioRun.runToFuture(OntologyCache.cacheUpdatedOntology(updatedOntologyIri, updatedOntologyData))
}
