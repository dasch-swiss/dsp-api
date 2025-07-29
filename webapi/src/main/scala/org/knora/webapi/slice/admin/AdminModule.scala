/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin

import zio.URLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.Features
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.AdminDomainModule
import org.knora.webapi.slice.admin.domain.service.DspIngestClient
import org.knora.webapi.slice.admin.repo.AdminRepoModule
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.infrastructure.CacheManager
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService

object AdminModule { self =>
  type Dependencies =
      // format: off
      AppConfig &
      CacheManager &
      DspIngestClient &
      Features &
      IriConverter &
      IriService &
      OntologyCache &
      OntologyRepo &
      PredicateObjectMapper &
      TriplestoreService
      // format: on

  type Provided = AdminDomainModule.Provided

  val layer: URLayer[self.Dependencies, self.Provided] =
    AdminRepoModule.layer >>> AdminDomainModule.layer
}
