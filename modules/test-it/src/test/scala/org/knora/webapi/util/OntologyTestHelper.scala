/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util
import zio.*

import java.time.Instant

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyMetadataV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

object OntologyTestHelper {
  private val ontologyRepo                                                       = ZIO.serviceWithZIO[OntologyRepo]
  def lastModificationDate(ontologyIri: OntologyIri): RIO[OntologyRepo, Instant] =
    ontologyRepo(
      _.findById(ontologyIri)
        .someOrFail(IllegalStateException(s"Ontology $ontologyIri not found"))
        .map(lastModificationDate),
    ).orDie

  @throws[IllegalStateException]
  def lastModificationDate(r: ReadOntologyV2): Instant =
    r.ontologyMetadata.lastModificationDate.getOrElse(
      throw IllegalStateException(s"${r.ontologyIri} has no last modification date"),
    )

  @throws[IllegalStateException]
  def lastModificationDate(r: ReadOntologyMetadataV2, ontologyIri: OntologyIri): Instant =
    r.toOntologySchema(ApiV2Complex)
      .ontologies
      .find(_.ontologyIri == ontologyIri.toComplexSchema)
      .flatMap(_.lastModificationDate)
      .getOrElse(throw IllegalStateException(s"$ontologyIri has no last modification date"))
}
