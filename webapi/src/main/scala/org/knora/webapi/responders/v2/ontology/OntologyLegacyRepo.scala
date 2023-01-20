package org.knora.webapi.responders.v2.ontology

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2

object OntologyLegacyRepo {

  def getCache(implicit ec: ExecutionContext): Future[Cache.OntologyCacheData] = Cache.getCacheData

  private def ensureInternal(iri: SmartIri) = iri.toOntologySchema(InternalSchema)

  def findOntologyBy(ontologyIri: SmartIri)(implicit ec: ExecutionContext): Future[Option[ReadOntologyV2]] =
    getCache.map(_.ontologies.get(ensureInternal(ontologyIri)))

  def findClassBy(classIri: SmartIri)(implicit ec: ExecutionContext): Future[Option[ReadClassInfoV2]] =
    findClassBy(ensureInternal(classIri), ensureInternal(classIri).getOntologyFromEntity)

  def findClassBy(classIri: SmartIri, ontologyIri: SmartIri)(implicit
    ec: ExecutionContext
  ): Future[Option[ReadClassInfoV2]] =
    findOntologyBy(ensureInternal(ontologyIri)).map(_.flatMap(_.classes.get(ensureInternal(classIri))))
}
