/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.service

import zio.*

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.PredicateRepository
import org.knora.webapi.slice.ontology.repo.CountPropertyUsedWithClassQuery
import org.knora.webapi.store.triplestore.api.TriplestoreService

final class PredicateRepositoryLive(tripleStore: TriplestoreService, iriConverter: IriConverter)
    extends PredicateRepository {

  /**
   * Checks how many times a property entity is used in resource instances.
   *
   * @param classIri    the IRI of the class that is being checked for usage.
   * @param propertyIri the IRI of the entity that is being checked for usage.
   * @return list of tuples containing all instance of the `classIri` with the count of
   *         how often this instance is using the property as a predicate
   */
  def getCountForPropertyUsedNumberOfTimesWithClass(
    propertyIri: PropertyIri,
    classIri: ResourceClassIri,
  ): Task[List[(ResourceClassIri, Int)]] =
    tripleStore
      .select(CountPropertyUsedWithClassQuery.build(propertyIri, classIri))
      .map(_.map(row => (row.rowMap("subject"), row.rowMap("count").toInt)).toList)
      .flatMap(row =>
        ZIO.foreach(row) { case (subjectIri, count) =>
          for {
            resourceClassIri <-
              iriConverter
                .asResourceClassIri(subjectIri)
                .mapError(e =>
                  InconsistentRepositoryDataException(s"Failed to convert IRI $subjectIri to ResourceClassIri: $e"),
                )
          } yield (resourceClassIri, count)
        },
      )
}

object PredicateRepositoryLive {
  val layer = ZLayer.derive[PredicateRepositoryLive]
}
