/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service

import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.OntologyIri

final case class OntologyService(ontologyRepo: OntologyRepo) {
  def findProjectIriForOntology(ontologyIri: OntologyIri): Task[Option[ProjectIri]] =
    ontologyRepo
      .findById(ontologyIri)
      .map(
        _.flatMap(_.ontologyMetadata.projectIri)
          .map(_.toIri)
          .map(ProjectIri.unsafeFrom),
      )
}

object OntologyService {
  val layer = ZLayer.derive[OntologyService]
}
