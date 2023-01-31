package org.knora.webapi.slice.ontology.domain.service
import zio.Task
import zio.URLayer
import zio.ZLayer
import zio.macros.accessible

import org.knora.webapi.messages.twirl.queries.sparql.v2.txt.countPropertyUsedWithClass
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService

@accessible
trait PredicateRepository {

  /**
   * Checks if a how many times a property entity is used in resource instances.
   *
   * @param classIri    the IRI of the class that is being checked for usage.
   * @param propertyIri the IRI of the entity that is being checked for usage.
   * @return [[Int]] denoting number of times used
   */
  def getCountForPropertyUseNumberOfTimesWithClass(classIri: InternalIri, propertyIri: InternalIri): Task[Int]
}

final case class PredicateRepositoryLive(private val tripleStore: TriplestoreService) extends PredicateRepository {

  /**
   * Checks if a how many times a property entity is used in resource instances.
   *
   * @param classIri    the IRI of the class that is being checked for usage.
   * @param propertyIri the IRI of the entity that is being checked for usage.
   * @return [[Int]] denoting number of times used
   */
  def getCountForPropertyUseNumberOfTimesWithClass(classIri: InternalIri, propertyIri: InternalIri): Task[Int] = {
    val query = countPropertyUsedWithClass(propertyIri, classIri)
    tripleStore
      .sparqlHttpSelect(query.toString)
      .map(result => result.results.bindings.headOption.map(_.rowMap("total").toInt).getOrElse(0))
  }
}

object PredicateRepositoryLive {
  val layer: URLayer[TriplestoreService, PredicateRepositoryLive] = ZLayer.fromFunction(PredicateRepositoryLive.apply _)
}
