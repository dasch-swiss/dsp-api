/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology
import zio.URLayer
import zio.ZLayer

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyCacheHelpers
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.domain.service.OntologyTriplestoreHelpers
import org.knora.webapi.slice.ontology.domain.service.StandoffEntityInfoService
import org.knora.webapi.slice.ontology.domain.service.StandoffEntityInfoServiceLive
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheLive
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resources.repo.service.ValueRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService

object OntologyModule { self =>

  type Dependencies = IriConverter & IriService & StringFormatter & TriplestoreService

  // This module exposes StandoffEntityInfoService: a lean, OntologyCache-only standoff lookup extracted from
  // OntologyResponderV2. Standoff callers (StandoffMappingService, StandoffTagUtilV2) depend on it instead of the full
  // responder, which keeps the layer graph acyclic. StandoffMappingService and OntologyTransformer belong to other
  // slices and stay wired in core/LayersLive.
  type Provided =
    // format: off
    CardinalityService &
    OntologyCache &
    OntologyCacheHelpers &
    OntologyRepo &
    OntologyTriplestoreHelpers &
    StandoffEntityInfoService &
    ValueRepo
    // format: on

  val layer: URLayer[self.Dependencies, self.Provided] =
    (OntologyCacheLive.layer ++ PredicateRepositoryLive.layer ++ ValueRepo.layer) >+>
      OntologyRepoLive.layer >+>
      (CardinalityService.layer ++ OntologyCacheHelpers.layer ++ OntologyTriplestoreHelpers.layer ++
        StandoffEntityInfoServiceLive.layer)
}
