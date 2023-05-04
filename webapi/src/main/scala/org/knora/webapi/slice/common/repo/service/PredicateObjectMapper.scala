/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.repo.service

import zio._

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.IRI
import org.knora.webapi.messages.store.triplestoremessages.LiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

/**
 * The [[PredicateObjectMapper]] is a service which provides methods to extract values from a [[ConstructPredicateObjects]].
 *
 * @param iriConverter A service that maps between internal and external IRIs.
 */
final case class PredicateObjectMapper(private val iriConverter: IriConverter) {

  /**
   * Returns an optional list of values for the given key.
   *
   * @param key           the key to look for.
   * @param propertiesMap the map to look in.
   * @tparam A the type of the values.
   * @return a list of values, [[None]] if key was not present.
   *         Fails if the value could not be cast to the given type.
   */
  def getListOption[A <: LiteralV2](key: IRI, propertiesMap: ConstructPredicateObjects): Task[Option[List[A]]] =
    for {
      smartIri <- iriConverter.asInternalSmartIri(key)
      props     = propertiesMap.get(smartIri)
      values   <- ZIO.foreach(props)(ZIO.foreach(_)(cast[A]).map(_.toList))
    } yield values

  private def cast[A <: LiteralV2](prop: LiteralV2): Task[A] =
    ZIO.attempt(prop.asInstanceOf[A]).logError(s"Could not cast $prop.")

  /**
   * Returns a list of values for the given key.
   * Fails during runtime if the value could not be cast to the given type.
   *
   * @param key           the key to look for.
   * @param propertiesMap the map to look in.
   * @tparam A the type of the values.
   * @return a list of values, an empty list if key was not present or no values are defined.
   */
  def getList[A <: LiteralV2](key: IRI, propertiesMap: ConstructPredicateObjects): Task[List[A]] =
    getListOption[A](key, propertiesMap).map(_.getOrElse(List.empty[A]))

  /**
   * Returns a list of values for the given key.
   * Fails during runtime if the value could not be cast to the given type.
   *
   * @param key           the key to look for.
   * @param propertiesMap the map to look in.
   * @tparam A the type of the values.
   * @return a list of values, fails with an [[InconsistentRepositoryDataException]] if key was not present.
   */
  def getListOrFail[A <: LiteralV2](key: IRI, propertiesMap: ConstructPredicateObjects): Task[List[A]] =
    getListOption[A](key, propertiesMap)
      .flatMap(ZIO.fromOption(_))
      .orElseFail(InconsistentRepositoryDataException(s"PropertiesMap has no $key defined."))

  /**
   * Returns an optional single value for the given key.
   * Fails during runtime if the value could not be cast to the given type.
   *
   * @param key           the key to look for.
   * @param propertiesMap the map to look in.
   * @tparam A the type of the values.
   * @return a single value, [[None]] if key was not present.
   */
  def getSingleOption[A <: LiteralV2](key: IRI, propertiesMap: ConstructPredicateObjects): Task[Option[A]] =
    getListOption[A](key, propertiesMap).map(_.flatMap(_.headOption))

  /**
   * Returns a single value for the given key.
   * Fails during runtime if the value could not be cast to the given type.
   *
   * @param key           the key to look for.
   * @param propertiesMap the map to look in.
   * @tparam A the type of the values.
   * @return a single value, fails with an [[InconsistentRepositoryDataException]] if key was not present.
   */
  def getSingleOrFail[A <: LiteralV2](key: IRI, propertiesMap: ConstructPredicateObjects): Task[A] =
    getSingleOption[A](key, propertiesMap)
      .flatMap(ZIO.fromOption(_))
      .orElseFail(InconsistentRepositoryDataException(s"PropertiesMap has no value for $key defined."))
}

object PredicateObjectMapper {
  val layer: URLayer[IriConverter, PredicateObjectMapper] = ZLayer.fromFunction(PredicateObjectMapper.apply _)
}
