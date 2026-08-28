/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import _root_.org.knora.webapi.responders.IriService
import org.junit.runner.RunWith
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.domain.AssetId as IngestAssetId
import swiss.dasch.domain.AssetInfoService
import swiss.dasch.domain.AssetInfoServiceLive
import swiss.dasch.domain.AssetRef
import swiss.dasch.domain.ProjectShortcode as IngestProjectShortcode
import swiss.dasch.domain.StorageServiceLive
import zio.*
import zio.Scope
import zio.ZIO
import zio.ZLayer
import zio.json.*
import zio.test.*

import java.nio.charset.StandardCharsets
import java.nio.file.Files as JFiles
import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.ApiV2Schema
import org.knora.webapi.GoldenTest
import org.knora.webapi.Rendering
import org.knora.webapi.TestDataFactory
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.responders.v2.OntologyResponderV2
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.domain.model.Permission
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
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.api.v2.VersionDate
import org.knora.webapi.slice.api.v3.`export`.FileLink
import org.knora.webapi.slice.api.v3.`export`.MetadataRecord
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

@RunWith(classOf[DspZTestJUnitRunner])
class ExportServiceSpec extends ZIOSpecDefault with GoldenTest {
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

  // distinct from base-url so the OAI test proves the file link uses external-base-url
  val publicIngestUrl = "https://ingest.example.org"

  // The only asset in the 1612 fixture that has a file value, and so the only OAI record with a `file`.
  val fixtureAssetId   = "B1D0OkEgfFp-Cew2Seur7Wi"
  val fixtureShortcode = "1612"

  // Real AssetInfoService over a throwaway asset dir, laid out the way dsp-ingest lays out its own: the
  // service resolves the .info path itself, so the segment scheme is never hard-coded here. Created
  // eagerly because AppConfig — which carries the dir — is built before any test effect runs.
  private val assetDir: java.nio.file.Path = JFiles.createTempDirectory("ExportServiceSpec-assets")

  private val assetInfoServiceLayer: ULayer[AssetInfoService] =
    ZLayer.succeed(StorageConfig(assetDir.toString, assetDir.toString)) >>>
      StorageServiceLive.layer >>> ZLayer.derive[AssetInfoServiceLive]

  private def assetRefOf(assetId: String): IO[IllegalArgumentException, AssetRef] =
    ZIO
      .fromEither(for {
        id        <- IngestAssetId.from(assetId)
        shortcode <- IngestProjectShortcode.from(fixtureShortcode)
      } yield AssetRef(id, shortcode))
      .mapError(new IllegalArgumentException(_))

  /** Writes `content` verbatim as the asset's sidecar, at the path dsp-ingest would read it from. */
  private def writeSidecarRaw(assetId: String, content: String): ZIO[AssetInfoService, Throwable, Unit] =
    for {
      ref      <- assetRefOf(assetId)
      infoFile <- ZIO.serviceWithZIO[AssetInfoService](_.getInfoFilePath(ref))
      path      = java.nio.file.Path.of(infoFile.toString)
      _        <- ZIO.attemptBlocking {
             JFiles.createDirectories(path.getParent)
             JFiles.writeString(path, content)
           }.unit
    } yield ()

  /**
   * Writes a well-formed sidecar for `assetId`. `extraFields` is raw JSON appended to the object, so a test
   * can add or omit optional fields (`sizeOriginal`, `originalMimeType`) exactly as a real sidecar would.
   */
  private def writeSidecar(assetId: String, extraFields: String): ZIO[AssetInfoService, Throwable, Unit] =
    writeSidecarRaw(
      assetId,
      s"""{
         |  "internalFilename": "$assetId.jp2",
         |  "originalInternalFilename": "$assetId.jpg.orig",
         |  "originalFilename": "the-original.jpg",
         |  "checksumOriginal": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
         |  "checksumDerivative": "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"$extraFields
         |}""".stripMargin,
    )

  private def deleteSidecar(assetId: String): ZIO[AssetInfoService, Throwable, Unit] =
    for {
      ref      <- assetRefOf(assetId)
      infoFile <- ZIO.serviceWithZIO[AssetInfoService](_.getInfoFilePath(ref))
      _        <- ZIO.attemptBlocking(JFiles.deleteIfExists(java.nio.file.Path.of(infoFile.toString))).unit
    } yield ()

  private def fileLinkOf(json: String): Either[String, Option[FileLink]] =
    json
      .fromJson[List[MetadataRecord]]
      .map(_.flatMap(_.file) match {
        case Nil           => None
        case single :: Nil => Some(single)
        case many          => throw new AssertionError(s"expected at most one file link, got ${many.size}")
      })

  private val expectedUrl =
    s"$publicIngestUrl/projects/$fixtureShortcode/assets/$fixtureAssetId/original"

  // the resource's creationDate, not a sidecar field
  private val expectedDateCreated = Some("2026-03-19T10:00:00Z")

  private val fullFileLink = FileLink(
    mimeType = Some("image/jpeg"),
    url = expectedUrl,
    checksum = Some("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
    checksumAlgorithm = Some("SHA-256"),
    fileName = Some("the-original.jpg"),
    fileSize = Some(123456L),
    dateCreated = expectedDateCreated,
  )

  private val noSidecarFileLink = FileLink(
    mimeType = None,
    url = expectedUrl,
    checksum = None,
    checksumAlgorithm = None,
    fileName = None,
    fileSize = None,
    dateCreated = expectedDateCreated,
  )

  // A complete, post-size-migration sidecar: every exported field present.
  private val fullSidecarFields =
    """,
      |  "sizeOriginal": 123456,
      |  "sizeDerivative": 654321,
      |  "internalMimeType": "image/jp2",
      |  "originalMimeType": "image/jpeg"""".stripMargin

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

  override def spec: Spec[TestEnvironment & Scope, Any] =
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
              .flatMap(_.runCollect)
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
              .flatMap(_.runCollect)
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
              .flatMap(_.runCollect)
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
              .flatMap(_.runCollect)
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
              .flatMap(_.runCollect)
          csv = new String(bytes.toArray, StandardCharsets.UTF_8)
        } yield assertGolden(csv, "withFootnotes")
      },
      test("exportResourcesOai") {
        for {
          _             <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          _             <- writeSidecar(fixtureAssetId, fullSidecarFields)
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          json          <- exportService.exportResourcesOai(project, user)
        } yield assertGolden(json, "oai") &&
          assertTrue(json.contains(s"$publicIngestUrl/projects/"))
      },
      test("exportResourcesOai reports the original's mimetype, not the derivative's") {
        for {
          _             <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          _             <- writeSidecar(fixtureAssetId, fullSidecarFields)
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          json          <- exportService.exportResourcesOai(project, user)
        } yield assertTrue(fileLinkOf(json) == Right(Some(fullFileLink)))
      },
      test("exportResourcesOai omits fileSize when the sidecar predates the size migration") {
        for {
          _ <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          _ <- writeSidecar(
                 fixtureAssetId,
                 """,
                   |  "internalMimeType": "image/jp2",
                   |  "originalMimeType": "image/jpeg"""".stripMargin,
               )
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          json          <- exportService.exportResourcesOai(project, user)
        } yield assertTrue(fileLinkOf(json) == Right(Some(fullFileLink.copy(fileSize = None))))
      },
      test("exportResourcesOai omits mimeType when the sidecar has no originalMimeType") {
        for {
          _ <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          _ <- writeSidecar(
                 fixtureAssetId,
                 """,
                   |  "sizeOriginal": 123456,
                   |  "internalMimeType": "image/jp2"""".stripMargin,
               )
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          json          <- exportService.exportResourcesOai(project, user)
        } yield assertTrue(fileLinkOf(json) == Right(Some(fullFileLink.copy(mimeType = None))))
      },
      test("exportResourcesOai still exports when the sidecar is present but malformed") {
        for {
          _             <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          _             <- writeSidecarRaw(fixtureAssetId, """{"internalFilename": "x.jp2"}""")
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          json          <- exportService.exportResourcesOai(project, user)
        } yield assertTrue(fileLinkOf(json) == Right(Some(noSidecarFileLink)))
      },
      test("exportResourcesOai emits the link and creation date but no sidecar fields when the sidecar is missing") {
        for {
          _             <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          _             <- deleteSidecar(fixtureAssetId)
          project       <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          exportService <- ZIO.service[ExportService]
          json          <- exportService.exportResourcesOai(project, user)
        } yield assertTrue(fileLinkOf(json) == Right(Some(noSidecarFileLink)))
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
              .flatMap(_.runCollect)
          csv   = new String(bytes.toArray, StandardCharsets.UTF_8)
          lines = csv.trim.split("\r\n").toList
          // "Label" is the second column (after "Resource IRI"); read it by index since legal columns follow it.
          labels = lines.tail.map(line => line.split(",")(1))
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
              .flatMap(_.runCollect)
          csv   = new String(bytes.toArray, StandardCharsets.UTF_8)
          lines = csv.trim.split("\r\n").toList
          // "Resource IRI" is column 0 and "Label" is column 1 (legal columns follow). Two resources share the
          // label "Mike" and were inserted in reverse-IRI order; they must come out sorted by resource IRI asc.
          mikeIris = lines.tail.filter(_.split(",")(1) == "Mike").map(_.split(",").head)
        } yield assertTrue(
          mikeIris == List("http://rdfh.ch/1612/ordering-Mike-1", "http://rdfh.ch/1612/ordering-Mike-2"),
        )
      },
      test("deleted values are excluded from the export") {
        // DEV-7008: Resource1 carries a deleted link value (target Resource0, still alive) and a deleted text
        // value alongside its live ones. The export read collapses deleted values the way /v2/resources does,
        // so neither may surface — not the deleted target's label, not its IRI, not the deleted paragraph.
        // Before the fix the link cell read "Resource2 :: Resource0" and its IRI cell carried Resource0's IRI.
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
                  PropertyIri.unsafeFrom(sf.toSmartIri("http://www.knora.org/ontology/1612/Data#LinkPropertyValue")),
                  PropertyIri.unsafeFrom(sf.toSmartIri("http://www.knora.org/ontology/1612/Data#TextParagraph")),
                ),
                user,
                LanguageCode.EN,
                includeIris = true,
                includeArkUrls = false,
              )
              .flatMap(_.runCollect)
          csv          = new String(bytes.toArray, StandardCharsets.UTF_8)
          resource1Row =
            csv.linesIterator.find(_.startsWith("http://rdfh.ch/1612/TfZ6cOzQThSMAkCoEeJFjA,")).getOrElse("")
        } yield
          // The live link is still exported in full ...
          assertTrue(resource1Row.contains("Resource2")) &&
            assertTrue(resource1Row.contains("http://rdfh.ch/1612/wQtVscpOTt-Sc_cH-dQL-w")) &&
            // ... while the deleted link contributes neither its target's label nor its target's IRI ...
            assertTrue(!resource1Row.contains("Resource0")) &&
            assertTrue(!resource1Row.contains("http://rdfh.ch/1612/Htygzo8mQDubd8gyinQ3Zw")) &&
            // ... and the deleted text value is gone too: the collapsing is not link-specific.
            assertTrue(!csv.contains("Deleted paragraph that must not be exported")) &&
            // Finally the reported shape: a live link value whose *target* is deleted must not leave a bare IRI
            // behind in the _IRI column next to an empty label.
            assertTrue(!resource1Row.contains("DeletedTarget")) &&
            assertTrue(!resource1Row.contains("http://rdfh.ch/1612/DeletedTargetRes01"))
      },
      test("link-value labels resolve across batch boundaries") {
        // Resource1 (TfZ6…) links to Resource2 (wQtV…) and vice versa. With batchSize = 1 each resource is its
        // own batch, so a link target lives in a different batch than its source. The cross-batch link-label
        // map (built once over all exported IRIs, not per batch) must still resolve each link to the target's
        // label — a regression to a per-batch map would yield an empty link column here.
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
                  PropertyIri.unsafeFrom(sf.toSmartIri("http://www.knora.org/ontology/1612/Data#LinkPropertyValue")),
                ),
                user,
                LanguageCode.EN,
                includeIris = false,
                includeArkUrls = false,
                batchSize = 1,
              )
              .flatMap(_.runCollect)
          csv          = new String(bytes.toArray, StandardCharsets.UTF_8)
          lines        = csv.trim.split("\r\n").toList
          resource1Row = lines.find(_.startsWith("http://rdfh.ch/1612/TfZ6cOzQThSMAkCoEeJFjA,")).getOrElse("")
          resource2Row = lines.find(_.startsWith("http://rdfh.ch/1612/wQtVscpOTt-Sc_cH-dQL-w,")).getOrElse("")
        } yield assertTrue(resource1Row.split(",").last == "Resource2") &&
          assertTrue(resource2Row.split(",").last == "Resource1")
      },
    ).provide(
      assetInfoServiceLayer,
      ConstructResponseUtilV2.layer,
      AppConfig.layer.map(env =>
        env.update[AppConfig](c => c.copy(dspIngest = c.dspIngest.copy(externalBaseUrl = publicIngestUrl))),
      ),
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
    ) @@ TestAspect.sequential // the tests share one triplestore and one asset dir

  private val streamingBehaviorSuite = {
    val nIris    = 600
    val fakeIris = (0 until nIris)
      .map(i => ResourceIri.from(s"http://rdfh.ch/0001/test$i").fold(e => throw new RuntimeException(e), identity))
      .toSeq

    val stubFindResources: FindResourcesService = new FindResourcesService {
      def findResources(p: KnoraProject, c: Option[ResourceClassIri]): Task[Seq[ResourceIri]] =
        ZIO.succeed(Seq.empty)
      def findResourceIrisOrderedByLabel(p: KnoraProject, c: ResourceClassIri): Task[Seq[(ResourceIri, String)]] =
        ZIO.succeed(fakeIris.map(iri => (iri, "")))
    }

    def mkExportService(
      iriConverter: IriConverter,
      ontologyRepo: OntologyRepo,
      readResources: ReadResourcesService,
      listsResponder: ListsResponder,
      csvService: CsvService,
      sf: StringFormatter,
      appConfig: AppConfig,
      assetInfoService: AssetInfoService,
    ): ExportService =
      ExportService(
        iriConverter,
        ontologyRepo,
        readResources,
        stubFindResources,
        listsResponder,
        csvService,
        sf,
        appConfig,
        assetInfoService,
      )

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
      test("batchSize defaults to the configured app.export.batch-size") {
        // Fix-confirmation for the review point that batch size must be a config value, not a hard-coded literal.
        // stubFindResources yields 600 IRIs; with the configured batch size of 100 the export must issue 6 batches
        // of 100 (the old hard-coded 500 would have produced batches of 500 + 100). exportResources is called
        // WITHOUT an explicit batchSize, so the value can only come from AppConfig.
        for {
          _              <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          project        <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          iriConverter   <- ZIO.service[IriConverter]
          ontologyRepo   <- ZIO.service[OntologyRepo]
          listsResponder <- ZIO.service[ListsResponder]
          csvService     <- ZIO.service[CsvService]
          sf             <- ZIO.service[StringFormatter]
          appConfig      <- ZIO.service[AppConfig]
          assetInfoSvc   <- ZIO.service[AssetInfoService]
          configured      = appConfig.copy(`export` = appConfig.`export`.copy(batchSize = 100))
          batchSizes     <- Ref.make(Chunk.empty[Int])
          readStub       <- mkReadStub { (_, iris) =>
                        batchSizes.update(_ :+ iris.size).as(ReadResourcesSequenceV2(Seq.empty))
                      }
          exportService =
            mkExportService(
              iriConverter,
              ontologyRepo,
              readStub,
              listsResponder,
              csvService,
              sf,
              configured,
              assetInfoSvc,
            )
          _ <- exportService
                 .exportResources(project, orderingTestClassIri, List.empty, user, LanguageCode.EN, false, false)
                 .flatMap(_.runDrain)
          sizes <- batchSizes.get
        } yield assertTrue(sizes == Chunk.fill(6)(100))
      },
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
          appConfig      <- ZIO.service[AppConfig]
          assetInfoSvc   <- ZIO.service[AssetInfoService]
          readStub       <- mkReadStub { (n, _) =>
                        if n == 0 then ZIO.succeed(ReadResourcesSequenceV2(Seq.empty))
                        else ZIO.fail(failure)
                      }
          exportService =
            mkExportService(
              iriConverter,
              ontologyRepo,
              readStub,
              listsResponder,
              csvService,
              sf,
              appConfig,
              assetInfoSvc,
            )
          result <- exportService
                      .exportResources(project, orderingTestClassIri, List.empty, user, LanguageCode.EN, false, false)
                      .flatMap(_.runDrain)
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
          appConfig      <- ZIO.service[AppConfig]
          assetInfoSvc   <- ZIO.service[AssetInfoService]
          readStub       <- mkReadStub((_, _) => ZIO.never)
          exportService   =
            mkExportService(
              iriConverter,
              ontologyRepo,
              readStub,
              listsResponder,
              csvService,
              sf,
              appConfig,
              assetInfoSvc,
            )
          resultOpt <-
            exportService
              .exportResources(project, orderingTestClassIri, List.empty, user, LanguageCode.EN, false, false)
              .flatMap(_.take(1).runCollect)
              .timeout(2.seconds)
        } yield assertTrue(resultOpt.isDefined) && assertTrue(resultOpt.get.nonEmpty)
      },
      test("setup failure surfaces as an effect failure before the stream is consumed") {
        // Regression guard: setup (ordered-IRI fetch, link-labels, vocabularies, header) runs inside the returned
        // Task, so a setup failure must fail the effect itself — observable WITHOUT draining the stream body. If
        // setup were deferred into the ZStream, this failure would only appear after the 200 OK was committed,
        // yielding a silently-truncated success instead of a 5xx.
        val failure                                    = new RuntimeException("setup failure")
        val failingFindResources: FindResourcesService = new FindResourcesService {
          def findResources(p: KnoraProject, c: Option[ResourceClassIri]): Task[Seq[ResourceIri]] =
            ZIO.succeed(Seq.empty)
          def findResourceIrisOrderedByLabel(p: KnoraProject, c: ResourceClassIri): Task[Seq[(ResourceIri, String)]] =
            ZIO.fail(failure)
        }
        for {
          _              <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          project        <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          iriConverter   <- ZIO.service[IriConverter]
          ontologyRepo   <- ZIO.service[OntologyRepo]
          listsResponder <- ZIO.service[ListsResponder]
          csvService     <- ZIO.service[CsvService]
          sf             <- ZIO.service[StringFormatter]
          appConfig      <- ZIO.service[AppConfig]
          assetInfoSvc   <- ZIO.service[AssetInfoService]
          readStub       <- mkReadStub((_, _) => ZIO.never)
          exportService   =
            ExportService(
              iriConverter,
              ontologyRepo,
              readStub,
              failingFindResources,
              listsResponder,
              csvService,
              sf,
              appConfig,
              assetInfoSvc,
            )
          exit <- exportService
                    .exportResources(project, orderingTestClassIri, List.empty, user, LanguageCode.EN, false, false)
                    .exit
        } yield assertTrue(exit.isFailure)
      },
      test("legal columns carry the project license/holder and per-resource authorship when set") {
        // Project-wide data license + copyright holder appear on every row; authorship is per-resource. The
        // license IRI is resolved to its human label ("CC BY 4.0"). Multi-value authorship joins with " :: ".
        val iri1 = ResourceIri.from("http://rdfh.ch/0001/legal-1").fold(e => throw new RuntimeException(e), identity)
        val iri2 = ResourceIri.from("http://rdfh.ch/0001/legal-2").fold(e => throw new RuntimeException(e), identity)

        def mkResource(iri: ResourceIri, label: String, authors: Seq[String], projectADM: Project): ReadResourceV2 =
          ReadResourceV2(
            resourceIri = iri,
            label = label,
            resourceClassIri = sf.toSmartIri("http://www.knora.org/ontology/1612/Data#Class1"),
            attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
            projectADM = projectADM,
            permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
            userPermission = Permission.ObjectAccess.ChangeRights,
            values = Map.empty,
            creationDate = Instant.parse("2016-10-17T17:16:04.916Z"),
            lastModificationDate = None,
            versionDate = None,
            deletionInfo = None,
            resourceAuthorship = authors.map(Authorship.unsafeFrom),
          )

        val legalFindResources: FindResourcesService = new FindResourcesService {
          def findResources(p: KnoraProject, c: Option[ResourceClassIri]): Task[Seq[ResourceIri]] =
            ZIO.succeed(Seq.empty)
          def findResourceIrisOrderedByLabel(p: KnoraProject, c: ResourceClassIri): Task[Seq[(ResourceIri, String)]] =
            ZIO.succeed(Seq((iri1, "Alice"), (iri2, "Bob")))
        }

        for {
          _           <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          baseProject <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          projectADM  <- ZIO.serviceWithZIO[ProjectService](_.findById(projectIri)).map(_.get)
          project      = baseProject.copy(
                      dataLicense = Some(LicenseIri.CC_BY_4_0),
                      dataCopyrightHolder = Some(CopyrightHolder.unsafeFrom("University of Basel")),
                    )
          iriConverter   <- ZIO.service[IriConverter]
          ontologyRepo   <- ZIO.service[OntologyRepo]
          listsResponder <- ZIO.service[ListsResponder]
          csvService     <- ZIO.service[CsvService]
          sfSvc          <- ZIO.service[StringFormatter]
          appConfig      <- ZIO.service[AppConfig]
          assetInfoSvc   <- ZIO.service[AssetInfoService]
          readStub       <- mkReadStub((_, _) =>
                        ZIO.succeed(
                          ReadResourcesSequenceV2(
                            Seq(
                              mkResource(iri1, "Alice", Seq("Lewis Carroll"), projectADM),
                              mkResource(iri2, "Bob", Seq("Ada Lovelace", "Alan Turing"), projectADM),
                            ),
                          ),
                        ),
                      )
          exportService =
            ExportService(
              iriConverter,
              ontologyRepo,
              readStub,
              legalFindResources,
              listsResponder,
              csvService,
              sfSvc,
              appConfig,
              assetInfoSvc,
            )
          bytes <- exportService
                     .exportResources(project, orderingTestClassIri, List.empty, user, LanguageCode.EN, false, false)
                     .flatMap(_.runCollect)
          csv   = new String(bytes.toArray, StandardCharsets.UTF_8)
          lines = csv.trim.split("\r\n").toList
        } yield assertTrue(lines.head == "Resource IRI,Label,Data License,Copyright Holder,Authorship") &&
          assertTrue(
            lines.exists(l =>
              l.contains("Alice") && l.contains("CC BY 4.0") && l.contains("University of Basel") &&
                l.contains("Lewis Carroll"),
            ),
          ) &&
          assertTrue(lines.exists(l => l.contains("Bob") && l.contains("Ada Lovelace :: Alan Turing")))
      },
      test("legal columns are always present but blank when the project has no resource-side legal metadata") {
        // The columns are added unconditionally: a project without data license / copyright holder and a resource
        // without authorship still gets the three columns, with empty cells.
        val iri = ResourceIri.from("http://rdfh.ch/0001/no-legal-1").fold(e => throw new RuntimeException(e), identity)

        val noLegalFindResources: FindResourcesService = new FindResourcesService {
          def findResources(p: KnoraProject, c: Option[ResourceClassIri]): Task[Seq[ResourceIri]] =
            ZIO.succeed(Seq.empty)
          def findResourceIrisOrderedByLabel(p: KnoraProject, c: ResourceClassIri): Task[Seq[(ResourceIri, String)]] =
            ZIO.succeed(Seq((iri, "NoLegal")))
        }

        for {
          _              <- ZIO.serviceWithZIO[TriplestoreService](_.insertDataIntoTriplestore(dataSets.toList, false))
          project        <- ZIO.serviceWithZIO[KnoraProjectService](_.findById(projectIri)).map(_.get)
          projectADM     <- ZIO.serviceWithZIO[ProjectService](_.findById(projectIri)).map(_.get)
          iriConverter   <- ZIO.service[IriConverter]
          ontologyRepo   <- ZIO.service[OntologyRepo]
          listsResponder <- ZIO.service[ListsResponder]
          csvService     <- ZIO.service[CsvService]
          sfSvc          <- ZIO.service[StringFormatter]
          appConfig      <- ZIO.service[AppConfig]
          assetInfoSvc   <- ZIO.service[AssetInfoService]
          resource        = ReadResourceV2(
                       resourceIri = iri,
                       label = "NoLegal",
                       resourceClassIri = sf.toSmartIri("http://www.knora.org/ontology/1612/Data#Class1"),
                       attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
                       projectADM = projectADM,
                       permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
                       userPermission = Permission.ObjectAccess.ChangeRights,
                       values = Map.empty,
                       creationDate = Instant.parse("2016-10-17T17:16:04.916Z"),
                       lastModificationDate = None,
                       versionDate = None,
                       deletionInfo = None,
                       resourceAuthorship = Seq.empty,
                     )
          readStub     <- mkReadStub((_, _) => ZIO.succeed(ReadResourcesSequenceV2(Seq(resource))))
          exportService =
            ExportService(
              iriConverter,
              ontologyRepo,
              readStub,
              noLegalFindResources,
              listsResponder,
              csvService,
              sfSvc,
              appConfig,
              assetInfoSvc,
            )
          bytes <- exportService
                     .exportResources(project, orderingTestClassIri, List.empty, user, LanguageCode.EN, false, false)
                     .flatMap(_.runCollect)
          csv   = new String(bytes.toArray, StandardCharsets.UTF_8)
          lines = csv.trim.split("\r\n").toList
        } yield assertTrue(lines.head == "Resource IRI,Label,Data License,Copyright Holder,Authorship") &&
          assertTrue(lines(1) == s"${iri.value},NoLegal,,,")
      },
    ).provide(
      assetInfoServiceLayer,
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
      ProjectService.layer,
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
