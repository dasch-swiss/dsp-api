package org.knora.webapi.slice.ontology.domain.service
import zio.Task
import zio.macros.accessible
import zio.ZIO

import org.knora.webapi.slice.resourceinfo.domain.InternalIri

@accessible
trait PredicateRepository {

  /**
   * Checks if a how many times a property entity is used in resource instances.
   *
   * @param classIri    the IRI of the class that is being checked for usage.
   * @param propertyIri the IRI of the entity that is being checked for usage.
   * @return list of tuples containing all instance of the `classIri` with the count of
   *         how often this instance is using the property as a predicate
   */
  def getCountForPropertyUsedNumberOfTimesWithClass(
    propertyIri: InternalIri,
    classIri: InternalIri
  ): Task[List[(InternalIri, Int)]]

  def getCountForPropertyUsedNumberOfTimesWithClass(propertyIri: InternalIri, classIri: List[InternalIri]): Task[List[(InternalIri, Int)]] =
    ZIO.foreach(classIri)(getCountForPropertyUsedNumberOfTimesWithClass(propertyIri, _)).map(_.flatten)
}

object PredicateRepository
