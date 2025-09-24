package org.knora.webapi.util
import zio.*

import java.time.Instant

import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

object OntologyTestHelper {
  def lastModificationDate(ontologyIri: OntologyIri): RIO[OntologyRepo, Instant] = ZIO
    .serviceWithZIO[OntologyRepo](_.findById(ontologyIri).orDie)
    .map(_.flatMap(_.ontologyMetadata.lastModificationDate))
    .someOrElseZIO(
      ZIO.die(IllegalStateException(s"Could not find the last modification date of the ontology $ontologyIri.")),
    )
}
