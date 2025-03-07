/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology
import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.URModule
import org.knora.webapi.slice.common.BaseModule
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.OntologyCacheHelpers
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.domain.service.OntologyService
import org.knora.webapi.slice.ontology.domain.service.OntologyServiceLive
import org.knora.webapi.slice.ontology.domain.service.OntologyTriplestoreHelpers
import org.knora.webapi.slice.ontology.domain.service.PredicateRepository
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheLive
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.store.triplestore.api.TriplestoreService

object CoreModule
    extends URModule[
      AppConfig.AppConfigurations & BaseModule.Provided,
      CardinalityService & IriConverter & OntologyCache & OntologyCacheHelpers & OntologyRepo & OntologyService &
        OntologyTriplestoreHelpers,
    ] {
  self =>

  val layer: URLayer[self.Dependencies, self.Provided] = ZLayer.makeSome[self.Dependencies, self.Provided](
    CardinalityService.layer,
    IriConverter.layer,
    OntologyCacheHelpers.layer,
    OntologyCacheLive.layer,
    OntologyRepoLive.layer,
    OntologyServiceLive.layer,
    OntologyTriplestoreHelpers.layer,
    PredicateRepositoryLive.layer,
  )
}
