package org.knora.webapi.responders.v2.ontology

import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.{ReadClassInfoV2, ReadOntologyV2}

import scala.concurrent.{ExecutionContext, Future}

object OntologyLegacyRepo {

  def getCache(implicit ec: ExecutionContext): Future[Cache.OntologyCacheData] = Cache.getCacheData

  private def ensureInternal(iri: SmartIri) = iri.toOntologySchema(InternalSchema)

  def findOntologyBy(ontologyIri: SmartIri)(implicit ec: ExecutionContext): Future[Option[ReadOntologyV2]] = {
    val internalOntologyIri = ensureInternal(ontologyIri)
    getCache.map(_.ontologies.get(internalOntologyIri))
  }

  def findClassBy(classIri: SmartIri)(implicit ec: ExecutionContext): Future[Option[(ReadOntologyV2, ReadClassInfoV2)]] = {
    findClassBy(classIri, ensureInternal(classIri).getOntologyFromEntity)
  }

  def findClassBy(classIri: SmartIri, ontologyIri: SmartIri)(implicit ec: ExecutionContext): Future[Option[(ReadOntologyV2, ReadClassInfoV2)]] = for {
    ontologyInfo <- findOntologyBy(ontologyIri.toOntologySchema(InternalSchema))
    classInfo = ontologyInfo.flatMap(_.classes.get(ensureInternal(classIri)))
  } yield ontologyInfo zip classInfo
}
