/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service

import zio.*

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

trait OntologyService {
  def getProjectIriForOntologyIri(ontologyIri: InternalIri): Task[Option[String]]
}

final case class OntologyServiceLive(ontologyCache: OntologyCache) extends OntologyService {
  def getProjectIriForOntologyIri(ontologyIri: InternalIri): Task[Option[String]] =
    ontologyCache.getCacheData.map { cacheData =>
      cacheData.ontologies.map { case (k, v) => k.toString() -> v }
        .get(ontologyIri.value)
        .flatMap(_.ontologyMetadata.projectIri.map(_.toString()))
    }

}

object OntologyServiceLive {
  def isBuiltInOntology(ontologyIri: InternalIri): Boolean =
    OntologyConstants.BuiltInOntologyLabels.contains(ontologyIri.value.split("/").last)

  def isSharedOntology(ontologyIri: InternalIri): Boolean =
    ontologyIri.value.split("/")(4) == "shared"

  def isBuiltInOrSharedOntology(ontologyIri: InternalIri): Boolean =
    isBuiltInOntology(ontologyIri) || isSharedOntology(ontologyIri)

  val layer = ZLayer.derive[OntologyServiceLive]
}
