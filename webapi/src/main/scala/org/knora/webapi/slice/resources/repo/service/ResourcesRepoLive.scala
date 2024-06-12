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
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.query.InsertDataQuery
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphName
import org.eclipse.rdf4j.sparqlbuilder.core.Variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.iri
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.Namespace
import org.eclipse.rdf4j.model.impl.SimpleNamespace
import org.eclipse.rdf4j.model.vocabulary.XSD

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

  private[service] def createNewResourceQueryWithBuilder(
    dataGraphIri: InternalIri,
    resourceToCreate: ResourceReadyToCreate,
    projectIri: IRI,
    creatorIri: IRI,
  ) = {
    val resourcePattern =
      Rdf
        .iri(resourceToCreate.resourceIri)
        .isA(iri(resourceToCreate.resourceClassIri))
        .andHas(RDFS.LABEL, Rdf.literalOf(resourceToCreate.resourceLabel))
        .andHas(KnoraBaseVocab.isDeleted, Rdf.literalOf(false))
        .andHas(KnoraBaseVocab.attachedToUser, iri(creatorIri))
        .andHas(KnoraBaseVocab.attachedToProject, iri(projectIri))
        .andHas(KnoraBaseVocab.hasPermissions, Rdf.literalOf(resourceToCreate.permissions))
        .andHas(KnoraBaseVocab.creationDate, Rdf.literalOfType(resourceToCreate.creationDate.toString(), XSD.DATETIME))

    for (newValueInfo <- resourceToCreate.newValueInfos) {
      val valuePattern =
        Rdf
          .iri(newValueInfo.newValueIri)
          .isA(iri(newValueInfo.value.valueType.toString()))

      resourcePattern.and(valuePattern)
    }

    val graph = iri(dataGraphIri.value)

    val queryTotal: InsertDataQuery =
      Queries
        .INSERT_DATA(resourcePattern)
        .into(graph)
        .prefix(KnoraBaseVocab.NS, RDF.NS, RDFS.NS, XSD.NS)

    Update(queryTotal.getQueryString())
  }
}

object KnoraBaseVocab {
  private val kb = "http://www.knora.org/ontology/knora-base#"

  val NS: Namespace = new SimpleNamespace("knora-base", kb)

  val isDeleted         = iri(kb + "isDeleted")
  val attachedToUser    = iri(kb + "attachedToUser")
  val attachedToProject = iri(kb + "attachedToProject")
  val hasPermissions    = iri(kb + "hasPermissions")
  val creationDate      = iri(kb + "creationDate")
}

object Run extends ZIOAppDefault {

  override def run = Console.printLine(prettyRes)

  val graphIri         = InternalIri("fooGraph")
  val projectIri       = "fooProject"
  val userIri          = "fooUser"
  val resourceIri      = "fooResource"
  val resourceClassIri = "fooClass"
  val label            = "fooLabel"
  val creationDate     = Instant.parse("2024-01-01T10:00:00.673298Z")
  val permissions      = "fooPermissions"

  val resourceDefinition = ResourceReadyToCreate(
    resourceIri = resourceIri,
    resourceClassIri = resourceClassIri,
    resourceLabel = label,
    creationDate = creationDate,
    permissions = permissions,
    newValueInfos = Seq.empty,
    linkUpdates = Seq.empty,
  )

  val res = ResourcesRepoLive
    .createNewResourceQueryWithBuilder(
      dataGraphIri = graphIri,
      resourceToCreate = resourceDefinition,
      projectIri = projectIri,
      creatorIri = userIri,
    )
    .sparql

  val prettyRes = res.replace("{", "{\n").replace("}", "\n}")
}
