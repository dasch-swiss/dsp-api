/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import zio.*

import java.time.Instant

import dsp.constants.SalsahGui.IRI
import org.knora.webapi.messages.twirl.NewLinkValueInfo
import org.knora.webapi.messages.twirl.NewValueInfo
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

case class ResourceReadyToCreate(
  resourceIri: IRI,
  resourceClassIri: IRI,
  resourceLabel: String,
  creationDate: Instant,
  permissions: String,
  newValueInfos: Seq[NewValueInfo],
  linkUpdates: Seq[NewLinkValueInfo],
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
      ResourcesRepoLive.createNewResourceQuery(
        dataGraphIri,
        resource,
        projectIri,
        userIri,
      ),
    )

}

object ResourcesRepoLive {
  val layer = ZLayer.derive[ResourcesRepoLive]

  private[service] def createNewResourceQuery(
    dataGraphIri: InternalIri,
    resourceToCreate: ResourceReadyToCreate,
    projectIri: IRI,
    creatorIri: IRI,
  ): Update =
    Update(
      sparql.v2.txt.createNewResource(
        dataNamedGraph = dataGraphIri.value,
        projectIri = projectIri,
        creatorIri = creatorIri,
        creationDate = resourceToCreate.creationDate,
        resourceIri = resourceToCreate.resourceIri,
        resourceClassIri = resourceToCreate.resourceClassIri,
        resourceLabel = resourceToCreate.resourceLabel,
        permissions = resourceToCreate.permissions,
        linkUpdates = resourceToCreate.linkUpdates,
        newValueInfos = resourceToCreate.newValueInfos,
      ),
    )
}
