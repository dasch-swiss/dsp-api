/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import zio.*

import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.valuemessages.UnverifiedValueV2
import org.knora.webapi.responders.v2.resources.SparqlTemplateResourceToCreate
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.messages.twirl.queries.sparql
import dsp.constants.SalsahGui.IRI

/**
 * Represents a resource that is ready to be created and whose contents can be verified afterwards.
 *
 * @param sparqlTemplateResourceToCreate a [[SparqlTemplateResourceToCreate]] describing SPARQL for creating
 *                                       the resource.
 * @param values                         the resource's values for verification.
 * @param hasStandoffLink                `true` if the property `knora-base:hasStandoffLinkToValue` was automatically added.
 */
case class ResourceReadyToCreate(
  sparqlTemplateResourceToCreate: SparqlTemplateResourceToCreate,
  values: Map[SmartIri, Seq[UnverifiedValueV2]],
  hasStandoffLink: Boolean,
)

trait ResourcesRepo {
  def createNewResource(
    dataGraphIri: InternalIri,
    resource: ResourceReadyToCreate,
    userIri: IRI,
    projectIri: IRI,
  ): Task[Unit]
}

final case class ResourcesRepoLive(triplestore: TriplestoreService) extends ResourcesRepo {

  def createNewResource(
    dataGraphIri: InternalIri,
    resource: ResourceReadyToCreate,
    userIri: IRI,
    projectIri: IRI,
  ): Task[Unit] =
    triplestore.query(
      Update(
        sparql.v2.txt.createNewResource(
          dataNamedGraph = dataGraphIri.value,
          resourceToCreate = resource.sparqlTemplateResourceToCreate,
          projectIri = projectIri,
          creatorIri = userIri,
        ),
      ),
    )

}

object ResourcesRepoLive { val layer = ZLayer.derive[ResourcesRepoLive] }
