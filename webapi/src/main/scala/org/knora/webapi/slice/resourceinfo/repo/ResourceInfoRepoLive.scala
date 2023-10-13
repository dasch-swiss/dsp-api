/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.repo

import zio._

import java.time.Instant

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfo
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfoRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

final case class ResourceInfoRepoLive(triplestore: TriplestoreService) extends ResourceInfoRepo {

  override def findByProjectAndResourceClass(
    projectIri: IriIdentifier,
    resourceClass: InternalIri
  ): Task[List[ResourceInfo]] = {
    val select = selectResourcesByCreationDate(resourceClass, projectIri)
    triplestore.query(select).logError.flatMap(toResourceInfoList)
  }

  private def selectResourcesByCreationDate(resourceClassIri: InternalIri, projectIri: IriIdentifier): Select = Select(
    s"""
       |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
       |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
       |
       |SELECT DISTINCT ?resource ?creationDate ?isDeleted ?lastModificationDate ?deleteDate
       |WHERE {
       |    ?resource a <${resourceClassIri.value}> ;
       |              knora-base:attachedToProject <${projectIri.value.value}> ;
       |              knora-base:creationDate ?creationDate ;
       |              knora-base:isDeleted ?isDeleted ;
       |    OPTIONAL { ?resource knora-base:lastModificationDate ?lastModificationDate .}
       |    OPTIONAL { ?resource knora-base:deleteDate ?deleteDate . }
       |}
       |""".stripMargin
  )

  private def toResourceInfoList(result: SparqlSelectResult) =
    ZIO.attempt(result.results.bindings.map(toResourceInfo).toList)

  private def toResourceInfo(row: VariableResultsRow) = {
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
  val layer = ZLayer.derive[ResourceInfoRepoLive]
}
