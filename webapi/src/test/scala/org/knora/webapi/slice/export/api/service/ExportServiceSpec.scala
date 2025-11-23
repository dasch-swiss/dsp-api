/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import _root_.org.knora.webapi.responders.IriService
import zio.Scope
import zio.ZIO
import zio.ZLayer
import zio.test.*

import org.knora.webapi.GoldenTest
import org.knora.webapi.TestDataFactory
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelayLive
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.resources.service.ReadResourcesServiceLive
import org.knora.webapi.store.triplestore.TestDatasetBuilder.datasetLayerFromDataObjects
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject

object ExportServiceSpec extends ZIOSpecDefault with GoldenTest {
  override val rewriteAll: Boolean = true

  UnsafeZioRun.runOrThrow(ZIO.service[StringFormatter].provide(StringFormatter.test))(zio.Runtime.default)
  given sf: StringFormatter = StringFormatter.getGeneralInstance

  def resourceClassIri: ResourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0803/incunabula#page")(using sf)

  val user       = TestDataFactory.User.rootUser
  val project    = TestDataFactory.someProject
  val projectADM = TestDataFactory.someProjectADM

  val dataSets = List(
    // TODO: rename to relative paths
    RdfDataObject(
      path = "/Users/raitisveinbahs/work/dsp-api/test_data/project_ontologies/incunabula-onto.ttl",
      name = "http://www.knora.org/ontology/0803/incunabula",
    ),
    RdfDataObject(
      path = "/Users/raitisveinbahs/work/dsp-api/test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
    RdfDataObject(
      path = "/Users/raitisveinbahs/work/dsp-api/webapi/src/main/resources/knora-ontologies/knora-base.ttl",
      name = "http://www.knora.org/ontology/knora-admin",
    ),
    RdfDataObject(
      path = "/Users/raitisveinbahs/work/dsp-api/test_data/project_data/admin-data.ttl",
      name = "http://www.knora.org/data/admin",
    ),
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
              List(), // List(PropertyIri.from(TextValueSmartIri).toOption.get),
              user,
              LanguageCode.EN,
              includeResourceIri = true,
            )
          csv <- exportService.toCsv(exportedCsv)
        } yield assertGolden(csv, "basic")
      },
    ).provide(
      // almost all of these are for ReadResourcesService, which I would call a "little much", if I'm being prefectly honest
      ConstructResponseUtilV2.layer,
      AppConfig.layer,
      CacheManager.layer,
      CsvService.layer,
      datasetLayerFromDataObjects(dataSets),
      ExportService.layer,
      findAllResourcesServiceEmptyLayer,
      IriConverter.layer,
      KnoraProjectRepoLive.layer,
      KnoraProjectService.layer,
      LicenseRepo.layer,
      MessageRelayLive.layer,
      OntologyCacheFake.emptyCache,
      OntologyRepoLive.layer,
      ProjectService.layer,
      ReadResourcesServiceLive.layer,
      StandoffTagUtilV2Live.layer,
      StringFormatter.test,
      TriplestoreServiceInMemory.layer,
      ListsResponder.layer,
      IriService.layer,
      AuthorizationRestService.layer,
      PredicateObjectMapper.layer,
      KnoraGroupService.layer,
      KnoraGroupRepoLive.layer,
      KnoraUserRepoLive.layer,
      PasswordService.layer,
      KnoraUserService.layer,
    )

  private val findAllResourcesServiceEmptyLayer: ZLayer[Any, Nothing, FindAllResourcesService] =
    ZLayer.succeed[FindAllResourcesService]((_: KnoraProject, _: ResourceClassIri) =>
      ZIO.succeed(
        Seq("http://rdfh.ch/0803/00014b43f902".toSmartIri),
      ),
    )
}
