/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.repo

import zio.URLayer

import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.lists.domain.ListsService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.v3.projects.domain.model.ProjectsRepo
import org.knora.webapi.slice.v3.projects.repo.service.ProjectsRepoDb
import org.knora.webapi.slice.v3.projects.repo.service.ProjectsRepoLive
import org.knora.webapi.store.triplestore.api.TriplestoreService

object ProjectsRepoModule { self =>
  type Dependencies =
      // format: off
      KnoraProjectService &
      ListsResponder &
      ListsService &
      OntologyRepo &
      TriplestoreService
      // format: on

  type Provided = ProjectsRepo

  val layer: URLayer[self.Dependencies, self.Provided] =
    ProjectsRepoDb.layer >>> ProjectsRepoLive.layer
}
