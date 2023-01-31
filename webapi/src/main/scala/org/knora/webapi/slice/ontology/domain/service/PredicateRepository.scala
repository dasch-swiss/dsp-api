package org.knora.webapi.slice.ontology.domain.service
import zio.Task
import zio.macros.accessible

import org.knora.webapi.slice.resourceinfo.domain.InternalIri

@accessible
trait PredicateRepository {

  /**
   * Checks if a how many times a property entity is used in resource instances.
   *
   * @param classIri    the IRI of the class that is being checked for usage.
   * @param propertyIri the IRI of the entity that is being checked for usage.
   * @return [[Int]] denoting number of times used
   */
  def getCountForPropertyUsedNumberOfTimesWithClass(
    propertyIri: InternalIri,
    classIri: InternalIri
  ): Task[Int]
}

object PredicateRepository {}
