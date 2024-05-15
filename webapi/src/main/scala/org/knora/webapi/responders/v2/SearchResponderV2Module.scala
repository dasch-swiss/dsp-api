/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import zio.*

import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.messages.util.search.gravsearch.prequery.InferenceOptimizationService
import org.knora.webapi.messages.util.search.gravsearch.transformers.ConstructTransformer
import org.knora.webapi.messages.util.search.gravsearch.transformers.OntologyInferencer
import org.knora.webapi.messages.util.search.gravsearch.types.*
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreService

object SearchResponderV2Module {
  type Dependencies = StandoffTagUtilV2 & AppConfig & TriplestoreService & ConstructResponseUtilV2 & OntologyCache &
    IriConverter & MessageRelay & StringFormatter & ProjectService

  type Provided = SearchResponderV2Live & OntologyInferencer & QueryTraverser & GravsearchTypeInspectionRunner

  val layer: URLayer[Dependencies, Provided] =
    ZLayer.makeSome[Dependencies, Provided](
      GravsearchTypeInspectionRunner.layer,
      ZLayer.derive[SearchResponderV2Live],
      InferenceOptimizationService.layer,
      QueryTraverser.layer,
      ConstructTransformer.layer,
      OntologyInferencer.layer,
    )
}
