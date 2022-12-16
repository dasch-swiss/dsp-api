/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.repo

import zio.UIO
import zio.ZIO
import zio.ZLayer

import java.time.Instant

import org.knora.webapi.messages.twirl.queries.sparql.v2.txt.resourcesByCreationDate
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfo
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfoRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class ResourceInfoRepoLive(ts: TriplestoreService) extends ResourceInfoRepo {

  override def findByProjectAndResourceClass(
    projectIri: InternalIri,
    resourceClass: InternalIri
  ): UIO[List[ResourceInfo]] = {
    val query = resourcesByCreationDate(resourceClass, projectIri).toString
    ZIO.debug(query) *> ts.sparqlHttpSelect(query).map(toResourceInfoList)
  }

  private def toResourceInfoList(result: SparqlSelectResult): List[ResourceInfo] =
    result.results.bindings.map(toResourceInfo).toList

  private def toResourceInfo(row: VariableResultsRow): ResourceInfo = {
    val rowMap = row.rowMap
    ResourceInfo(
      rowMap("resource"),
      Instant.parse(rowMap("creationDate")),
      rowMap.get("lastModificationDate").map(Instant.parse(_)),
      rowMap.get("deleteDate").map(Instant.parse),
      rowMap("isDeleted").toBoolean
    )
  }
}

object ResourceInfoRepoLive {
  val layer: ZLayer[TriplestoreService, Nothing, ResourceInfoRepoLive] =
    ZLayer.fromZIO(ZIO.service[TriplestoreService].map(ResourceInfoRepoLive(_)))
}
