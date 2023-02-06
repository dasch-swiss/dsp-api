/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service

import zio.Task
import zio.ZIO
import zio.macros.accessible

import org.knora.webapi.slice.resourceinfo.domain.InternalIri

@accessible
trait PredicateRepository {

  /**
   * Checks how many times a property entity is used in resource instances.
   *
   * @param propertyIri the IRI of the entity that is being checked for usage.
   * @param classIri    the IRI of the class that is being checked for usage.
   * @return list of tuples containing all instance of the `classIri` with the count of
   *         how often this instance is using the property as a predicate
   */
  def getCountForPropertyUsedNumberOfTimesWithClass(
    propertyIri: InternalIri,
    classIri: InternalIri
  ): Task[List[(InternalIri, Int)]]

  /**
   * Checks how many times a property entity is used in resource instances.
   *
   * @param propertyIri  the IRI of the entity that is being checked for usage.
   * @param classIris    the IRIs of the classes that are being checked for usage.
   * @return list of tuples containing all instance of one of the `classIri`s with the count of
   *         how often this instance is using the property as a predicate
   */
  def getCountForPropertyUsedNumberOfTimesWithClasses(
    propertyIri: InternalIri,
    classIris: List[InternalIri]
  ): Task[List[(InternalIri, Int)]] =
    ZIO.foreach(classIris)(getCountForPropertyUsedNumberOfTimesWithClass(propertyIri, _)).map(_.flatten)
}

object PredicateRepository
