/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service

import org.knora.webapi.slice.ontology.repo.service.OntologyCache

import zio.*
import org.knora.webapi.messages.OntologyConstants

trait OntologyService {
  def getProjectIriForOntologyIri(ontologyIri: String): Task[Option[String]]
}

final case class OntologyServiceLive(ontologyCache: OntologyCache) extends OntologyService {
  def getProjectIriForOntologyIri(ontologyIri: String): Task[Option[String]] =
    ontologyCache.getCacheData.map { cacheData =>
      cacheData.ontologies.map { case (k, v) => k.toString() -> v }
        .get(ontologyIri)
        .flatMap(_.ontologyMetadata.projectIri.map(_.toString()))
    }
}

object OntologyServiceLive {
  def isBuiltInOntology(ontologyIri: String): Boolean =
    OntologyConstants.BuiltInOntologyLabels.contains(ontologyIri)

  def isSharedOntology(ontologyIri: String): Boolean =
    ontologyIri.split("/")(4) == "shared"

  def isBuiltInOrSharedOntology(ontologyIri: String): Boolean =
    isBuiltInOntology(ontologyIri) || isSharedOntology(ontologyIri)

  val layer = ZLayer.derive[OntologyServiceLive]
}
