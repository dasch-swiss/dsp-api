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
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheLive
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.resources.service.ReadResourcesServiceLive
import org.knora.webapi.store.triplestore.TestDatasetBuilder.emptyDataset
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdatePlan.builtInNamedGraphs

object ExportServiceSpec extends ZIOSpecDefault with GoldenTest {
  // override val rewriteAll: Boolean = true

  UnsafeZioRun.runOrThrow(ZIO.service[StringFormatter].provide(StringFormatter.test))(zio.Runtime.default)
  given sf: StringFormatter = StringFormatter.getGeneralInstance

  def resourceClassIri: ResourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/1612/Data#Class1")(using sf)

  val user       = TestDataFactory.User.rootUser
  val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/Vk0NruDmRyeZCZvOVwXOnw")

  val dataSets: Set[RdfDataObject] = builtInNamedGraphs ++ List(
    RdfDataObject(
      path = "webapi/src/test/resources/org/knora/webapi/slice/export/api/service/ExportServiceSpec-1612-onto.ttl",
      name = "http://www.knora.org/ontology/1612/Data",
    ),
    RdfDataObject(
      path = "webapi/src/test/resources/org/knora/webapi/slice/export/api/service/ExportServiceSpec-1612-data.ttl",
      name = "http://www.knora.org/data/1612/funk",
    ),
    RdfDataObject(
      path = "webapi/src/test/resources/org/knora/webapi/slice/export/api/service/ExportServiceSpec-1612-admin.ttl",
      name = "http://www.knora.org/data/admin",
    ),
  ).toSet

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ExportServiceSpec")(
      test("basic") {
        for {
          _             <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          _             <- ZIO.serviceWithZIO[OntologyCache](_.refreshCache())
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          exportedCsv <-
            exportService.exportResources(
              project,
              resourceClassIri,
              List(
                PropertyIri.unsafeFrom(sf.toSmartIri("http://www.knora.org/ontology/1612/Data#Place")),
                PropertyIri.unsafeFrom(sf.toSmartIri("http://www.knora.org/ontology/1612/Data#LinkPropertyValue")),
                PropertyIri.unsafeFrom(sf.toSmartIri("http://www.knora.org/ontology/1612/Data#TextParagraph")),
                PropertyIri.unsafeFrom(sf.toSmartIri("http://www.knora.org/ontology/1612/Data#TextRich")),
                PropertyIri.unsafeFrom(sf.toSmartIri("http://www.knora.org/ontology/1612/Data#FunkList")),
              ),
              user,
              LanguageCode.EN,
              includeIris = true,
            )
          csv <- exportService.toCsv(exportedCsv)
        } yield assertGolden(csv, "basic")
      },
      test("with includeIris = false") {
        for {
          _             <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          _             <- ZIO.serviceWithZIO[OntologyCache](_.refreshCache())
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          exportedCsv <-
            exportService.exportResources(
              project,
              resourceClassIri,
              List(
                PropertyIri.unsafeFrom(sf.toSmartIri("http://www.knora.org/ontology/1612/Data#Place")),
                PropertyIri.unsafeFrom(sf.toSmartIri("http://www.knora.org/ontology/1612/Data#LinkPropertyValue")),
                PropertyIri.unsafeFrom(sf.toSmartIri("http://www.knora.org/ontology/1612/Data#TextParagraph")),
                PropertyIri.unsafeFrom(sf.toSmartIri("http://www.knora.org/ontology/1612/Data#TextRich")),
                PropertyIri.unsafeFrom(sf.toSmartIri("http://www.knora.org/ontology/1612/Data#FunkList")),
              ),
              user,
              LanguageCode.DE,
              includeIris = false,
            )
          csv <- exportService.toCsv(exportedCsv)
        } yield assertGolden(csv, "includeIrisFalse")
      },
    ).provide(
      ConstructResponseUtilV2.layer,
      AppConfig.layer,
      CacheManager.layer,
      CsvService.layer,
      emptyDataset,
      ExportService.layer,
      FindAllResourcesService.layer,
      IriConverter.layer,
      KnoraProjectRepoLive.layer,
      KnoraProjectService.layer,
      LicenseRepo.layer,
      MessageRelayLive.layer,
      OntologyCacheLive.layer,
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
}
