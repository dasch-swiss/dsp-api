/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import zio.Scope
import zio.ZIO
import zio.ZLayer
import zio.test.*

import org.knora.webapi.GoldenTest
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.resources.service.ReadResourcesServiceFake
import org.knora.webapi.slice.infrastructure.CsvService

object ExportServiceSpec extends ZIOSpecDefault with GoldenTest {
  override val rewriteAll: Boolean = true

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ExportServiceSpec")(
      test("basic") {
        for {
          iriConverter  <- ZIO.service[IriConverter]
          exportService <- ZIO.service[ExportService]
          exportedCsv <-
            exportService.exportResources(
              TestDataFactory.someProject,
              ResourceClassIri.unsafeFrom(
                "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri(iriConverter.sf),
              ),
              List(),
              TestDataFactory.User.rootUser,
              LanguageCode.EN,
              includeResourceIri = true,
            )
          csv <- exportService.toCsv(exportedCsv)
        } yield assertGolden(csv, "basic")
      },
    ).provide(
      StringFormatter.test,
      IriConverter.layer,
      findAllResourcesServiceEmptyLayer,
      ReadResourcesServiceFake.layer,
      OntologyCacheFake.emptyCache,
      OntologyRepoLive.layer,
      CsvService.layer,
      ExportService.layer,
    )

  private val findAllResourcesServiceEmptyLayer: ZLayer[Any, Nothing, FindAllResourcesService] =
    ZLayer.succeed[FindAllResourcesService]((project: KnoraProject, classIri: ResourceClassIri) =>
      ZIO.succeed(Seq.empty),
    )
}
