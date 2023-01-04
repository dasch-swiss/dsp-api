package org.knora.webapi.slice.ontology.domain.service
import zio.Task
import zio.macros.accessible

import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

@accessible
trait OntologyRepo {
  def findOntologyBy(iri: InternalIri): Task[Option[ReadOntologyV2]]
  def findClassBy(iri: InternalIri): Task[Option[ReadClassInfoV2]]
}
