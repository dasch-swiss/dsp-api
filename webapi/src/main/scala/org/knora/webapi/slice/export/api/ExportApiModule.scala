/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.api

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.v3.export_.ExportService
import org.knora.webapi.slice.api.v3.export_.FindResourcesService
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.service.ReadResourcesService
import org.knora.webapi.store.triplestore.api.TriplestoreService

object ExportApiModule { self =>
  type Dependencies =
    // format: off
    ConstructResponseUtilV2 &
    CsvService &
    IriConverter &
    KnoraProjectService &
    ListsResponder &
    OntologyRepo &
    ReadResourcesService &
    TriplestoreService
    // format: on

  type Provided =
    // format: off
    ExportService
    // format: on

  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      ExportService.layer,
      FindResourcesService.layer,
    )
}
