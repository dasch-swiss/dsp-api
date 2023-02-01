package org.knora.webapi.slice.ontology.repo.service

import zio.Task
import zio.URLayer
import zio.ZLayer

import org.knora.webapi.messages.twirl.queries.sparql.v2.txt.countPropertyUsedWithClass
import org.knora.webapi.slice.ontology.domain.service.PredicateRepository
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class PredicateRepositoryLive(private val tripleStore: TriplestoreService) extends PredicateRepository {

  /**
   * Checks if a how many times a property entity is used in resource instances.
   *
   * @param classIri    the IRI of the class that is being checked for usage.
   * @param propertyIri the IRI of the entity that is being checked for usage.
   * @return [[Int]] denoting number of times used the property is used a predicate with instances of the the class
   */
  def getCountForPropertyUsedNumberOfTimesWithClass(
    propertyIri: InternalIri,
    classIri: InternalIri
  ): Task[List[(InternalIri, Int)]] = {
    val query = countPropertyUsedWithClass(propertyIri, classIri)
    tripleStore
      .sparqlHttpSelect(query.toString)
      .map(result =>
        result.results.bindings.map(row => (InternalIri(row.rowMap("subject")), row.rowMap("count").toInt)).toList
      )
  }
}
object PredicateRepositoryLive {
  val layer: URLayer[TriplestoreService, PredicateRepositoryLive] = ZLayer.fromFunction(PredicateRepositoryLive.apply _)
}
