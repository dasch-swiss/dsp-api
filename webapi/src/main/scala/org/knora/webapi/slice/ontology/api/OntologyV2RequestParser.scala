package org.knora.webapi.slice.ontology.api

import org.knora.webapi.messages.v2.responder.ontologymessages.ChangeOntologyMetadataRequestV2
import zio.IO
import zio.ZLayer

final case class OntologyV2RequestParser() {

  def changeOntologyMetadataRequestV2(string: String): IO[String, ChangeOntologyMetadataRequestV2] = ???
}

object OntologyV2RequestParser {
  val layer = ZLayer.derive[OntologyV2RequestParser]
}
