/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import _root_.org.knora.webapi.responders.IriService
import zio.*
import zio.Scope
import zio.ZIO
import zio.ZLayer
import zio.test.*

import java.nio.charset.StandardCharsets

import org.knora.webapi.ApiV2Schema
import org.knora.webapi.GoldenTest
import org.knora.webapi.IRI
import org.knora.webapi.Rendering
import org.knora.webapi.TestDataFactory
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.responders.v2.OntologyResponderV2
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.slice.api.v2.VersionDate
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyCacheHelpers
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.domain.service.OntologyTriplestoreHelpers
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheLive
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resources.service.ReadResourcesService
import org.knora.webapi.slice.resources.service.ReadResourcesServiceLive
import org.knora.webapi.slice.standoff.service.StandoffMappingServiceFake
import org.knora.webapi.store.triplestore.TestDatasetBuilder.emptyDataset
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdatePlan.builtInNamedGraphs

object ExportServiceSpec extends ZIOSpecDefault with GoldenTest {
  // override val rewriteAll: Boolean = true

  given sf: StringFormatter = StringFormatter.getInitializedTestInstance

  def resourceClassIri: ResourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/1612/Data#Class1")(using sf)

  def superClassIri: ResourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/1612/Data#SuperClass1")(using sf)

  def footnoteClassIri: ResourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/1612/Data#FootnoteTestClass")(using sf)

  def orderingTestClassIri: ResourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/1612/Data#OrderingTestClass")(using sf)

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
      triplestoreSuite,
      streamingBehaviorSuite,
    )

  private val triplestoreSuite =
    suite("with triplestore")(
      test("basic") {
        for {
          _             <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          _             <- ZIO.serviceWithZIO[OntologyCache](_.refreshCache())
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          bytes         <-
            exportService
              .exportResources(
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
                includeArkUrls = false,
              )
              .runCollect
          csv = new String(bytes.toArray, StandardCharsets.UTF_8)
        } yield assertGolden(csv, "basic")
      },
      test("with includeIris = false and includeArkUrls = false") {
        for {
          _             <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          _             <- ZIO.serviceWithZIO[OntologyCache](_.refreshCache())
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          bytes         <-
            exportService
              .exportResources(
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
                includeArkUrls = false,
              )
              .runCollect
          csv = new String(bytes.toArray, StandardCharsets.UTF_8)
        } yield assertGolden(csv, "includeIrisFalse")
      },
      test("superclass export returns instances of subclasses") {
        for {
          _             <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          _             <- ZIO.serviceWithZIO[OntologyCache](_.refreshCache())
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          bytes         <-
            exportService
              .exportResources(
                project,
                superClassIri,
                List.empty,
                user,
                LanguageCode.EN,
                includeIris = true,
                includeArkUrls = false,
              )
              .runCollect
          csv = new String(bytes.toArray, StandardCharsets.UTF_8)
        } yield assertGolden(csv, "superclassExport")
      },
      test("with includeArkUrls = true") {
        for {
          _             <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          _             <- ZIO.serviceWithZIO[OntologyCache](_.refreshCache())
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          bytes         <-
            exportService
              .exportResources(
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
                includeIris = false,
                includeArkUrls = true,
              )
              .runCollect
          csv = new String(bytes.toArray, StandardCharsets.UTF_8)
        } yield assertGolden(csv, "includeArkUrlsTrue")
      },
      test("with footnotes in text value") {
        for {
          _             <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          _             <- ZIO.serviceWithZIO[OntologyCache](_.refreshCache())
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          bytes         <-
            exportService
              .exportResources(
                project,
                footnoteClassIri,
                List(
                  PropertyIri.unsafeFrom(sf.toSmartIri("http://www.knora.org/ontology/1612/Data#TextRich")),
                ),
                user,
                LanguageCode.EN,
                includeIris = false,
                includeArkUrls = false,
              )
              .runCollect
          csv = new String(bytes.toArray, StandardCharsets.UTF_8)
        } yield assertGolden(csv, "withFootnotes")
      },
      test("exportResourcesOai") {
        for {
          _             <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          json          <- exportService.exportResourcesOai(project, user)
        } yield assertGolden(json, "oai")
      },
      test("resources are exported in alphabetical label order") {
        for {
          _             <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          _             <- ZIO.serviceWithZIO[OntologyCache](_.refreshCache())
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          bytes         <-
            exportService
              .exportResources(
                project,
                orderingTestClassIri,
                List.empty,
                user,
                LanguageCode.EN,
                includeIris = false,
                includeArkUrls = false,
              )
              .runCollect
          csv    = new String(bytes.toArray, StandardCharsets.UTF_8)
          lines  = csv.trim.split("\r\n").toList
          labels = lines.tail.map(line => line.split(",").last)
        } yield assertTrue(labels == List("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Mike", "Mike"))
      },
      test("resources sharing a label are ordered by resource IRI") {
        for {
          _             <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          _             <- ZIO.serviceWithZIO[OntologyCache](_.refreshCache())
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          bytes         <-
            exportService
              .exportResources(
                project,
                orderingTestClassIri,
                List.empty,
                user,
                LanguageCode.EN,
                includeIris = false,
                includeArkUrls = false,
              )
              .runCollect
          csv      = new String(bytes.toArray, StandardCharsets.UTF_8)
          lines    = csv.trim.split("\r\n").toList
          // The "Resource IRI" column is always the first column. Two resources share the label "Mike"
          // and were inserted in reverse-IRI order; they must come out sorted by resource IRI ascending.
          mikeIris = lines.tail.filter(_.endsWith(",Mike")).map(_.split(",").head)
        } yield assertTrue(
          mikeIris == List("http://rdfh.ch/1612/ordering-Mike-1", "http://rdfh.ch/1612/ordering-Mike-2"),
        )
      },
    ).provide(
      ConstructResponseUtilV2.layer,
      AppConfig.layer,
      CacheManager.layer,
      CsvService.layer,
      emptyDataset,
      ExportService.layer,
      FindResourcesService.layer,
      IriConverter.layer,
      KnoraProjectRepoLive.layer,
      KnoraProjectService.layer,
      LicenseRepo.layer,
      CardinalityHandler.layer,
      CardinalityService.layer,
      OntologyCacheHelpers.layer,
      OntologyCacheLive.layer,
      OntologyRepoLive.layer,
      OntologyResponderV2.layer,
      OntologyTriplestoreHelpers.layer,
      PredicateRepositoryLive.layer,
      ProjectService.layer,
      ReadResourcesServiceLive.layer,
      StandoffMappingServiceFake.layer,
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

  private val streamingBehaviorSuite = {
    val nIris    = 600
    val fakeIris = (0 until nIris)
      .map(i => ResourceIri.from(s"http://rdfh.ch/0001/test$i").fold(e => throw new RuntimeException(e), identity))
      .toSeq

    val stubFindResources: FindResourcesService = new FindResourcesService {
      def findResources(p: KnoraProject, c: Option[ResourceClassIri]): Task[Seq[ResourceIri]] =
        ZIO.succeed(Seq.empty)
      def findResourceIrisOrderedByLabel(p: KnoraProject, c: ResourceClassIri): Task[Seq[ResourceIri]] =
        ZIO.succeed(fakeIris)
      def findLabelsFor(iris: Seq[ResourceIri]): Task[Map[IRI, String]] =
        ZIO.succeed(Map.empty)
    }

    def mkExportService(
      iriConverter: IriConverter,
      ontologyRepo: OntologyRepo,
      readResources: ReadResourcesService,
      listsResponder: ListsResponder,
      csvService: CsvService,
      sf: StringFormatter,
    ): ExportService =
      ExportService(iriConverter, ontologyRepo, readResources, stubFindResources, listsResponder, csvService, sf)

    def mkReadStub(
      onCall: (Int, Seq[ResourceIri]) => Task[ReadResourcesSequenceV2],
    ): ZIO[Any, Nothing, ReadResourcesService] =
      Ref.make(0).map { counter =>
        new ReadResourcesService {
          def readResourcesSequence(
            resourceIris: Seq[ResourceIri],
            propertyIri: Option[SmartIri] = None,
            valueUuid: Option[java.util.UUID] = None,
            preview: Boolean = false,
            targetSchema: ApiV2Schema,
            requestingUser: User,
            withDeleted: Boolean = true,
            queryStandoff: Boolean = false,
            skipRetrievalChecks: Boolean = false,
            standoffTagFilter: Option[SmartIri] = None,
          ): Task[ReadResourcesSequenceV2] =
            counter.getAndUpdate(_ + 1).flatMap(n => onCall(n, resourceIris))
          def readResourcesSequencePar(
            resourceIris: Seq[ResourceIri],
            propertyIri: Option[SmartIri] = None,
            valueUuid: Option[java.util.UUID] = None,
            preview: Boolean = false,
            targetSchema: ApiV2Schema,
            requestingUser: User,
            withDeleted: Boolean = true,
            queryStandoff: Boolean = false,
            skipRetrievalChecks: Boolean = false,
            standoffTagFilter: Option[SmartIri] = None,
          ): Task[ReadResourcesSequenceV2] = null
          def getResources(
            resourceIris: Seq[ResourceIri],
            propertyIri: Option[SmartIri] = None,
            targetSchema: ApiV2Schema,
            schemaOptions: Set[Rendering],
            requestingUser: User,
          ): Task[ReadResourcesSequenceV2] = null
          def getResourcesWithDeletedResource(
            resourceIris: Seq[ResourceIri],
            propertyIri: Option[SmartIri] = None,
            valueUuid: Option[java.util.UUID] = None,
            versionDate: Option[VersionDate] = None,
            withDeleted: Boolean = true,
            showDeletedValues: Boolean = false,
            targetSchema: ApiV2Schema,
            schemaOptions: Set[Rendering],
            requestingUser: User,
          ): Task[ReadResourcesSequenceV2] = null
          def getResourcePreviewWithDeletedResource(
            resourceIris: Seq[ResourceIri],
            withDeleted: Boolean = true,
            targetSchema: ApiV2Schema,
            requestingUser: User,
          ): Task[ReadResourcesSequenceV2] = null
          def getResourcePreview(
            resourceIris: Seq[ResourceIri],
            withDeleted: Boolean = true,
            targetSchema: ApiV2Schema,
            requestingUser: User,
          ): Task[ReadResourcesSequenceV2] = null
        }
      }

    suite("streaming behavior")(
      test("mid-stream batch failure propagates through stream error channel") {
        val failure = new RuntimeException("mid-stream batch failure")
        for {
          _              <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          project        <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          iriConverter   <- ZIO.service[IriConverter]
          ontologyRepo   <- ZIO.service[OntologyRepo]
          listsResponder <- ZIO.service[ListsResponder]
          csvService     <- ZIO.service[CsvService]
          sf             <- ZIO.service[StringFormatter]
          readStub       <- mkReadStub { (n, _) =>
                        if n == 0 then ZIO.succeed(ReadResourcesSequenceV2(Seq.empty))
                        else ZIO.fail(failure)
                      }
          exportService = mkExportService(iriConverter, ontologyRepo, readStub, listsResponder, csvService, sf)
          result       <- exportService
                      .exportResources(project, orderingTestClassIri, List.empty, user, LanguageCode.EN, false, false)
                      .runDrain
                      .exit
        } yield assertTrue(result.isFailure)
      },
      test("stream emits first byte before any batch completes") {
        for {
          _              <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          project        <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          iriConverter   <- ZIO.service[IriConverter]
          ontologyRepo   <- ZIO.service[OntologyRepo]
          listsResponder <- ZIO.service[ListsResponder]
          csvService     <- ZIO.service[CsvService]
          sf             <- ZIO.service[StringFormatter]
          readStub       <- mkReadStub((_, _) => ZIO.never)
          exportService   = mkExportService(iriConverter, ontologyRepo, readStub, listsResponder, csvService, sf)
          resultOpt      <-
            exportService
              .exportResources(project, orderingTestClassIri, List.empty, user, LanguageCode.EN, false, false)
              .take(1)
              .runCollect
              .timeout(2.seconds)
        } yield assertTrue(resultOpt.isDefined) && assertTrue(resultOpt.get.nonEmpty)
      },
    ).provide(
      AppConfig.layer,
      CacheManager.layer,
      CsvService.layer,
      emptyDataset,
      IriConverter.layer,
      KnoraProjectRepoLive.layer,
      KnoraProjectService.layer,
      LicenseRepo.layer,
      OntologyCacheLive.layer,
      OntologyRepoLive.layer,
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
}
