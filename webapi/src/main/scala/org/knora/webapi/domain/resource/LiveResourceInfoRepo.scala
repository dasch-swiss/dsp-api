package org.knora.webapi.domain.resource

import org.knora.webapi.IRI
import org.knora.webapi.messages.util.rdf.{SparqlSelectResult, VariableResultsRow}
import org.knora.webapi.store.triplestore.api.TriplestoreService
import zio.{UIO, ZIO, ZLayer}

import java.time.Instant

final case class LiveResourceInfoRepo(ts: TriplestoreService) extends ResourceInfoRepo {

  override def findByResourceClass(resourceClass: IRI): UIO[List[ResourceInfo]] = {
    val query = org.knora.webapi.messages.twirl.queries.sparql.v2.txt.resourcesByCreationDate(resourceClass).toString
    ts.sparqlHttpSelect(query).map(toResourceInfoList)
  }

  private def toResourceInfoList(result: SparqlSelectResult): List[ResourceInfo] =
    result.results.bindings.map(toResourceInfo).toList

  private def toResourceInfo(row: VariableResultsRow): ResourceInfo =
    ResourceInfo(row.rowMap("resource"), Instant.parse(row.rowMap("creationDate")))
}

object LiveResourceInfoRepo {
  val layer: ZLayer[TriplestoreService, Nothing, LiveResourceInfoRepo] =
    ZLayer.fromZIO(ZIO.service[TriplestoreService].map(LiveResourceInfoRepo(_)))
}
