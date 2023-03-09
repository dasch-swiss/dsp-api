package org.knora.webapi.responders.v2.ontology
import scala.concurrent.Future

import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.ontology.repo.model.OntologyCacheData
import org.knora.webapi.slice.ontology.repo.service.OntologyCache

object OntologyCacheService {

  def getCacheData(implicit runtime: zio.Runtime[OntologyCache]): Future[OntologyCacheData] =
    UnsafeZioRun.runToFuture(OntologyCache.getCacheData)

  private def ensureInternal(iri: SmartIri) = iri.toOntologySchema(InternalSchema)

  def findOntologyBy(
    ontologyIri: SmartIri
  )(implicit runtime: zio.Runtime[OntologyCache]): Future[Option[ReadOntologyV2]] =
    UnsafeZioRun.runToFuture(findOntologyByZ(ensureInternal(ontologyIri)))

  private def findOntologyByZ(ontologyIri: SmartIri) =
    OntologyCache.getCacheData.map(_.ontologies.get(ontologyIri))

  def findClassBy(classIri: SmartIri)(implicit runtime: zio.Runtime[OntologyCache]): Future[Option[ReadClassInfoV2]] =
    UnsafeZioRun.runToFuture(findClassByZ(ensureInternal(classIri), ensureInternal(classIri).getOntologyFromEntity))

  private def findClassByZ(classIri: SmartIri, ontologyIri: SmartIri) =
    findOntologyByZ(ontologyIri).map(_.flatMap(_.classes.get(classIri)))

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
