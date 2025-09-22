/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.*

import dsp.errors.ForbiddenException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.slice.admin.api.MessageResponse
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class StoreRestService(
  appConfig: AppConfig,
  triplestoreService: TriplestoreService,
  ontologyCache: OntologyCache,
  cacheManager: CacheManager,
) {

  /**
   * Resets the triplestore with provided data, adding defaults optionally.
   *
   * @param rdfDataObjects the payload consisting of a list of [[RdfDataObject]] send inside the message.
   * @param prependDefaults denotes if the rdfDataObjects list should be prepended with a default set.
   * @return a [[MessageResponse]].
   */
  def resetTriplestoreContent(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean = true,
  ): Task[MessageResponse] =
    for {
      _ <- ZIO.when(!appConfig.allowReloadOverHttp) {
             val msg =
               "The ResetTriplestoreContent operation is not allowed. Did you start the server with the right flag?"
             ZIO.fail(ForbiddenException(msg))
           }
      _ <- ZIO.logWarning(s"Resetting triplestore content with ${rdfDataObjects.map(_.name).mkString(", ")}")
      _ <- triplestoreService.resetTripleStoreContent(rdfDataObjects, prependDefaults).logError
      _ <- ontologyCache.refreshCache().logError
      _ <- cacheManager.clearAll()
    } yield MessageResponse("success")
}

object StoreRestService {
  val layer = ZLayer.derive[StoreRestService]
}
