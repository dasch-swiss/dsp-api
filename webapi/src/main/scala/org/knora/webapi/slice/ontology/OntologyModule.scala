/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology
import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.Features
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyCacheHelpers
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.domain.service.OntologyTriplestoreHelpers
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheLive
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resources.repo.service.ValueRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService

object OntologyModule { self =>

  type Dependencies = Features & IriConverter & IriService & StringFormatter & TriplestoreService

  type Provided =
    // format: off
    CardinalityService &
    OntologyCache &
    OntologyCacheHelpers &
    OntologyRepo &
    OntologyTriplestoreHelpers &
    ValueRepo
    // format: on

  val layer: URLayer[self.Dependencies, self.Provided] = ZLayer.makeSome[self.Dependencies, self.Provided](
    CardinalityService.layer,
    OntologyCacheHelpers.layer,
    OntologyCacheLive.layer,
    OntologyRepoLive.layer,
    OntologyTriplestoreHelpers.layer,
    PredicateRepositoryLive.layer,
    ValueRepo.layer,
  )
}
