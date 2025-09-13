/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3

import zio.URLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.Features
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.lists.domain.ListsService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.v3.api.ApiV3Endpoints
import org.knora.webapi.slice.v3.projects.ProjectsModule
import org.knora.webapi.store.triplestore.api.TriplestoreService

object V3Module { self =>
  type Dependencies =
      // format: off
      AppConfig &
      AuthorizationRestService &
      BaseEndpoints &
      CacheManager &
      Features &
      HandlerMapper &
      IriConverter &
      IriService &
      KnoraProjectService &
      KnoraResponseRenderer &
      ListsResponder &
      ListsService &
      OntologyCache &
      OntologyRepo &
      PredicateObjectMapper &
      TapirToPekkoInterpreter &
      TriplestoreService
      // format: on

  type Provided = V3Routes & ProjectsModule.Provided & ApiV3Endpoints

  val layer: URLayer[self.Dependencies, self.Provided] =
    ProjectsModule.layer >+> (V3Routes.layer ++ ApiV3Endpoints.layer)
}
