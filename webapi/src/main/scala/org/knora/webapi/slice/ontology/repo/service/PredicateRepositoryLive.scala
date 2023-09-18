/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.service

import zio.Task
import zio.URLayer
import zio.ZLayer

import org.knora.webapi.messages.twirl.queries.sparql.v2.txt.countPropertyUsedWithClass
import org.knora.webapi.slice.ontology.domain.service.PredicateRepository
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

final case class PredicateRepositoryLive(private val tripleStore: TriplestoreService) extends PredicateRepository {

  /**
   * Checks how many times a property entity is used in resource instances.
   *
   * @param classIri    the IRI of the class that is being checked for usage.
   * @param propertyIri the IRI of the entity that is being checked for usage.
   * @return list of tuples containing all instance of the `classIri` with the count of
   *         how often this instance is using the property as a predicate
   */
  def getCountForPropertyUsedNumberOfTimesWithClass(
    propertyIri: InternalIri,
    classIri: InternalIri
  ): Task[List[(InternalIri, Int)]] =
    tripleStore
      .query(Select(countPropertyUsedWithClass(propertyIri, classIri)))
      .map(_.results.bindings.map(row => (InternalIri(row.rowMap("subject")), row.rowMap("count").toInt)).toList)
}

object PredicateRepositoryLive {
  val layer: URLayer[TriplestoreService, PredicateRepositoryLive] = ZLayer.fromFunction(PredicateRepositoryLive.apply _)
}
