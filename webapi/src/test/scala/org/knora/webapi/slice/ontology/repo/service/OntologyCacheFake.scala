/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.service
import zio.Ref
import zio.Task
import zio.UIO
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.slice.ontology.repo.model.OntologyCacheData

case class OntologyCacheFake(ref: Ref[OntologyCacheData]) extends OntologyCache {

  override def getCacheData: Task[OntologyCacheData] = ref.get

  def set(data: OntologyCacheData): UIO[Unit] = ref.set(data)

  /**
   * Loads and caches all ontology information.
   *
   * @param requestingUser the user making the request.
   * @return a [[SuccessResponseV2]].
   */
  override def loadOntologies(requestingUser: UserADM): Task[SuccessResponseV2] =
    throw new UnsupportedOperationException("Not possible in tests. Provide the respective test data as Ref.")

  /**
   * Updates an existing ontology in the cache without updating the cache lookup maps. This should only be used if only the ontology metadata has changed.
   *
   * @param updatedOntologyIri  the IRI of the updated ontology
   * @param updatedOntologyData the [[ReadOntologyV2]] representation of the updated ontology
   * @return the updated cache data
   */
  override def cacheUpdatedOntologyWithoutUpdatingMaps(
    updatedOntologyIri: SmartIri,
    updatedOntologyData: ReadOntologyV2
  ): Task[OntologyCacheData] = ???

  /**
   * Deletes an ontology from the cache.
   *
   * @param ontologyIri the IRI of the ontology to delete
   * @return the updated cache data
   */
  override def deleteOntology(ontologyIri: SmartIri): Task[OntologyCacheData] = ???

  /**
   * Updates an existing ontology in the cache. If a class has changed, use `cacheUpdatedOntologyWithClass()`.
   *
   * @param updatedOntologyIri  the IRI of the updated ontology
   * @param updatedOntologyData the [[ReadOntologyV2]] representation of the updated ontology
   * @return the updated cache data
   */
  override def cacheUpdatedOntology(
    updatedOntologyIri: SmartIri,
    updatedOntologyData: ReadOntologyV2
  ): Task[OntologyCacheData] = ???

  /**
   * Updates an existing ontology in the cache and ensures that the sub- and superclasses of a (presumably changed) class get updated correctly.
   *
   * @param updatedOntologyIri  the IRI of the updated ontology
   * @param updatedOntologyData the [[ReadOntologyV2]] representation of the updated ontology
   * @param updatedClassIri     the IRI of the changed class
   * @return the updated cache data
   */
  override def cacheUpdatedOntologyWithClass(
    updatedOntologyIri: SmartIri,
    updatedOntologyData: ReadOntologyV2,
    updatedClassIri: SmartIri
  ): Task[OntologyCacheData] = ???
}

object OntologyCacheFake {

  def set(data: OntologyCacheData): ZIO[OntologyCacheFake, Nothing, Unit] =
    ZIO.service[OntologyCacheFake].flatMap(_.set(data))

  def withCache(data: OntologyCacheData): ZLayer[Any, Nothing, OntologyCacheFake] = ZLayer.fromZIO {
    for {
      ref <- Ref.make[OntologyCacheData](data)
    } yield OntologyCacheFake(ref)
  }

  val emptyData: OntologyCacheData =
    OntologyCacheData(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Set.empty)

  val emptyCache: ZLayer[Any, Nothing, OntologyCacheFake] = withCache(emptyData)
}
