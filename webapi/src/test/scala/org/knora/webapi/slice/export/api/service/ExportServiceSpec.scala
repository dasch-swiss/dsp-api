/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import zio.Scope
import zio.ZIO
import zio.ZLayer
import zio.test.*

import java.time.Instant

import org.knora.webapi.GoldenTest
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.resources.service.ReadResourcesServiceFake

object ExportServiceSpec extends ZIOSpecDefault with GoldenTest {
  override val rewriteAll: Boolean = true

  UnsafeZioRun.runOrThrow(ZIO.service[StringFormatter].provide(StringFormatter.test))(zio.Runtime.default)

  def resourceClassIri: ResourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#Thing")(using
      StringFormatter.getGeneralInstance,
    )

  val user       = TestDataFactory.User.rootUser
  val project    = TestDataFactory.someProject
  val projectADM = TestDataFactory.someProjectADM

  val readResource =
    ReadResourceV2(
      "http://a/b",
      "resource1",
      resourceClassIri.smartIri,
      user.id,
      projectADM,
      "",
      ObjectAccess.maxPermission,
      Map.empty, // values
      Instant.now,
      Some(Instant.now),
      None,
      None,
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ExportServiceSpec")(
      test("basic") {
        for {
          exportService <- ZIO.service[ExportService]
          exportedCsv <-
            exportService.exportResources(
              project,
              resourceClassIri,
              List(),
              user,
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
      OntologyCacheFake.emptyCache,
      OntologyRepoLive.layer,
      CsvService.layer,
      ZLayer.succeed(ReadResourcesServiceFake(Seq(readResource))),
      ExportService.layer,
    )

  private val findAllResourcesServiceEmptyLayer: ZLayer[Any, Nothing, FindAllResourcesService] =
    ZLayer.succeed[FindAllResourcesService]((_: KnoraProject, _: ResourceClassIri) =>
      ZIO.succeed(Seq.empty),
    )
}
