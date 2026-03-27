/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import com.typesafe.config.ConfigFactory
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.nio.file.Files
import zio.nio.file.Path
import zio.test.*

import org.knora.bagit.BagIt
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.util.rdf.QuadFormat
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.SparqlSelectResultBody
import org.knora.webapi.messages.util.rdf.SparqlSelectResultHeader
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.api.admin.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService

object ProjectMigrationExportServiceSpec extends ZIOSpecDefault {

  private val testProject = TestDataFactory.someProject
  private val testUser    = TestDataFactory.User.rootUser

  private case class TestEnv(
    service: ProjectMigrationExportService,
    storage: ProjectMigrationStorageService,
    exportProjectCalled: Ref[Boolean],
    selectResultRef: Ref[SparqlSelectResult],
    constructRdfRef: Ref[String],
    capturedConstructQueryRef: Ref[Option[String]],
  )

  private val configLayer: ULayer[Unit] = Runtime.setConfigProvider(
    TypesafeConfigProvider.fromTypesafeConfig(ConfigFactory.load().getConfig("app").resolve),
  )

  private def stubDspIngestClient(exportProjectCalled: Ref[Boolean]): DspIngestClient = new DspIngestClient {
    override def getAssetInfo(shortcode: Shortcode, assetId: AssetId): Task[AssetInfoResponse] =
      ZIO.die(new UnsupportedOperationException("not used in export tests"))
    override def exportProject(shortcode: Shortcode, outputFile: Path): Task[Option[Path]] =
      exportProjectCalled.set(true) *> Files.createDirectories(outputFile.parent.get) *>
        Files.writeBytes(outputFile, Chunk.fromArray("fake-zip".getBytes)).as(Some(outputFile))
    override def eraseProject(shortcode: Shortcode): Task[Unit] =
      ZIO.die(new UnsupportedOperationException("not used in export tests"))
    override def importProject(shortcode: Shortcode, fileToImport: Path): Task[Path] =
      ZIO.die(new UnsupportedOperationException("not used in export tests"))
  }

  private def stubTriplestoreService(
    selectResultRef: Ref[SparqlSelectResult],
    constructRdfRef: Ref[String],
    capturedConstructQueryRef: Ref[Option[String]],
  ): TriplestoreService = new TriplestoreService {
    import org.knora.webapi.messages.store.triplestoremessages.*
    import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.*
    import org.knora.webapi.store.triplestore.domain.TriplestoreStatus
    import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration

    override def uploadNQuads(stream: zio.stream.ZStream[Any, Throwable, Byte]): Task[Unit] = ZIO.unit
    override def query(sparql: Ask): Task[Boolean]                                          = ZIO.succeed(false)
    override def query(sparql: Construct): Task[SparqlConstructResponse]                    =
      ZIO.die(new UnsupportedOperationException)
    override def queryRdf(sparql: Construct): Task[String] =
      capturedConstructQueryRef.set(Some(sparql.sparql)) *> constructRdfRef.get
    override def query(sparql: Select): Task[SparqlSelectResult] = selectResultRef.get
    override def query(sparql: Update): Task[Unit]               = ZIO.unit
    override def queryToFile(
      sparql: Construct,
      graphIri: InternalIri,
      outputFile: Path,
      outputFormat: QuadFormat,
    ): Task[Unit]                                                                                                   = ZIO.unit
    override def downloadGraph(graphIri: InternalIri, outputFile: Path, outputFormat: QuadFormat): Task[Unit]       = ZIO.unit
    override def resetTripleStoreContent(rdfDataObjects: List[RdfDataObject], prependDefaults: Boolean): Task[Unit] =
      ZIO.unit
    override def dropDataGraphByGraph(): Task[Unit]                                                                   = ZIO.unit
    override def insertDataIntoTriplestore(rdfDataObjects: List[RdfDataObject], prependDefaults: Boolean): Task[Unit] =
      ZIO.unit
    override def checkTriplestore(): Task[TriplestoreStatus]                                                = ZIO.succeed(TriplestoreStatus.Available)
    override def downloadRepository(outputFile: java.nio.file.Path, graphs: GraphsForMigration): Task[Unit] = ZIO.unit
    override def uploadRepository(inputFile: java.nio.file.Path): Task[Unit]                                = ZIO.unit
    override def dropGraph(graphName: String): Task[Unit]                                                   = ZIO.unit
    override def compact(): Task[Boolean]                                                                   = ZIO.succeed(false)
  }

  private def stubOntologyRepo: OntologyRepo = new OntologyRepo {
    import org.knora.webapi.messages.v2.responder.ontologymessages.*
    import org.knora.webapi.slice.common.KnoraIris.*
    import org.knora.webapi.slice.ontology.domain.model.RepresentationClass

    override def findById(id: InternalIri): Task[Option[ReadOntologyV2]]                       = ZIO.none
    override def findByProject(projectId: KnoraProject.ProjectIri): Task[List[ReadOntologyV2]] =
      ZIO.succeed(List.empty)
    override def findAll(): Task[Chunk[ReadOntologyV2]]                                       = ZIO.succeed(Chunk.empty)
    override def findClassBy(classIri: InternalIri): Task[Option[ReadClassInfoV2]]            = ZIO.none
    override def findDirectSuperClassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] =
      ZIO.succeed(List.empty)
    override def findAllSuperClassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] =
      ZIO.succeed(List.empty)
    override def findAllSuperClassesBy(classIris: List[InternalIri]): Task[List[ReadClassInfoV2]] =
      ZIO.succeed(List.empty)
    override def findAllSuperClassesBy(
      classIris: List[InternalIri],
      upToClass: InternalIri,
    ): Task[List[ReadClassInfoV2]]                                                          = ZIO.succeed(List.empty)
    override def findDirectSubclassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] =
      ZIO.succeed(List.empty)
    override def findAllSubclassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] =
      ZIO.succeed(List.empty)
    override def findRepresentationClass(classIri: ResourceClassIri): Task[RepresentationClass] =
      ZIO.die(new UnsupportedOperationException)
    override def findProperty(propertyIri: PropertyIri): Task[Option[ReadPropertyInfoV2]] = ZIO.none
  }

  private val emptySelectResult =
    SparqlSelectResult(SparqlSelectResultHeader(Seq.empty), SparqlSelectResultBody(Seq.empty))

  private val makeTestEnv: ZIO[Any, Nothing, TestEnv] = for {
    exportProjectCalled       <- Ref.make[Boolean](false)
    selectResultRef           <- Ref.make[SparqlSelectResult](emptySelectResult)
    constructRdfRef           <- Ref.make[String]("")
    capturedConstructQueryRef <- Ref.make[Option[String]](None)
    dspIngestClient            = stubDspIngestClient(exportProjectCalled)
    triplestore                = stubTriplestoreService(selectResultRef, constructRdfRef, capturedConstructQueryRef)
    ontologyRepo               = stubOntologyRepo
    projectRepo               <- Ref.make[KnoraProject.ProjectIri => Task[Option[KnoraProject]]](_ => ZIO.none)
    projectRepoStub            = new KnoraProjectRepo {
                        override def findById(id: KnoraProject.ProjectIri): Task[Option[KnoraProject]] =
                          projectRepo.get.flatMap(_(id))
                        override def findByShortcode(
                          shortcode: KnoraProject.Shortcode,
                        ): Task[Option[KnoraProject]]                                                               = ZIO.none
                        override def findByShortname(shortname: KnoraProject.Shortname): Task[Option[KnoraProject]] =
                          ZIO.none
                        override def findAll(): Task[Chunk[KnoraProject]]                = ZIO.succeed(Chunk.empty)
                        override def save(project: KnoraProject): Task[KnoraProject]     = ZIO.succeed(project)
                        override def delete(entity: KnoraProject): Task[Unit]            = ZIO.unit
                        override def deleteById(id: KnoraProject.ProjectIri): Task[Unit] = ZIO.unit
                      }
    projectService = KnoraProjectService(projectRepoStub, null, ontologyRepo)
    storage        = new ProjectMigrationStorageService()
    persistence    = new DataTaskPersistence {
                    def onChanged(task: CurrentDataTask): UIO[Unit] = ZIO.unit
                    def onDeleted(taskId: DataTaskId): UIO[Unit]    = ZIO.unit
                  }
    taskStateRef <- Ref.make[Option[CurrentDataTask]](None)
    taskState     = new DataTaskState(taskStateRef, persistence)
    service       = new ProjectMigrationExportService(taskState, dspIngestClient, projectService, storage, triplestore)
  } yield TestEnv(service, storage, exportProjectCalled, selectResultRef, constructRdfRef, capturedConstructQueryRef)

  private def pollUntilDone(
    service: ProjectMigrationExportService,
    taskId: DataTaskId,
  ): IO[Option[Nothing], CurrentDataTask] =
    service
      .getExportStatus(taskId)
      .flatMap { task =>
        task.status match {
          case DataTaskStatus.Completed  => ZIO.succeed(task)
          case DataTaskStatus.Failed     => ZIO.succeed(task)
          case DataTaskStatus.InProgress => ZIO.fail(None)
        }
      }
      .retry(Schedule.spaced(100.millis) && Schedule.recurs(100))

  override def spec: Spec[Any, Any] = suite("ProjectMigrationExportService")(
    test("export with skipAssets=false calls ingest exportProject") {
      for {
        env    <- makeTestEnv
        task   <- env.service.createExport(testProject, testUser, skipAssets = false)
        result <- pollUntilDone(env.service, task.id)
        called <- env.exportProjectCalled.get
        _      <- env.service.deleteExport(task.id).catchAllCause(_ => ZIO.unit)
      } yield assertTrue(
        result.status == DataTaskStatus.Completed,
        called,
      )
    },
    test("export with skipAssets=true does not call ingest exportProject") {
      for {
        env    <- makeTestEnv
        task   <- env.service.createExport(testProject, testUser, skipAssets = true)
        result <- pollUntilDone(env.service, task.id)
        called <- env.exportProjectCalled.get
        _      <- env.service.deleteExport(task.id).catchAllCause(_ => ZIO.unit)
      } yield assertTrue(
        result.status == DataTaskStatus.Completed,
        !called,
      )
    },
    test("export with skipAssets=true produces BagIt without assets directory") {
      for {
        env     <- makeTestEnv
        task    <- env.service.createExport(testProject, testUser, skipAssets = true)
        result  <- pollUntilDone(env.service, task.id)
        zipFile <- env.storage.exportBagItZipPath(task.id)
        // Read the BagIt zip and check its payload
        bagResult <- ZIO.scoped {
                       Files.createTempDirectoryScoped(Some("verify-export"), Seq.empty).flatMap { tempDir =>
                         BagIt.readAndValidateZip(zipFile, Some(tempDir)).map { case (_, bagRoot) =>
                           val assetsDir = (bagRoot / "data" / "assets").toFile.exists()
                           val rdfDir    = (bagRoot / "data" / "rdf").toFile.exists()
                           (assetsDir, rdfDir)
                         }
                       }
                     }
        _ <- env.service.deleteExport(task.id).catchAllCause(_ => ZIO.unit)
      } yield assertTrue(
        result.status == DataTaskStatus.Completed,
        !bagResult._1, // no assets directory
        bagResult._2,  // rdf directory exists
      )
    },
    test("export with skipAssets=false produces BagIt with assets directory") {
      for {
        env       <- makeTestEnv
        task      <- env.service.createExport(testProject, testUser, skipAssets = false)
        result    <- pollUntilDone(env.service, task.id)
        zipFile   <- env.storage.exportBagItZipPath(task.id)
        bagResult <- ZIO.scoped {
                       Files.createTempDirectoryScoped(Some("verify-export"), Seq.empty).flatMap { tempDir =>
                         BagIt.readAndValidateZip(zipFile, Some(tempDir)).map { case (_, bagRoot) =>
                           val assetsZip = (bagRoot / "data" / "assets" / "assets.zip").toFile.exists()
                           val rdfDir    = (bagRoot / "data" / "rdf").toFile.exists()
                           (assetsZip, rdfDir)
                         }
                       }
                     }
        _ <- env.service.deleteExport(task.id).catchAllCause(_ => ZIO.unit)
      } yield assertTrue(
        result.status == DataTaskStatus.Completed,
        bagResult._1, // assets/assets.zip exists
        bagResult._2, // rdf directory exists
      )
    },
    suite("collectAdminGraphData with referenced users")(
      test("export with referenced users includes VALUES in CONSTRUCT query") {
        val referencedUserIri = "http://rdfh.ch/users/referenced001"
        val selectResult      = SparqlSelectResult(
          SparqlSelectResultHeader(Seq("user")),
          SparqlSelectResultBody(Seq(VariableResultsRow(Map("user" -> referencedUserIri)))),
        )
        // Minimal Turtle that the CONSTRUCT query returns — project + user + group
        val ka              = "http://www.knora.org/ontology/knora-admin#"
        val constructResult =
          s"""@prefix knora-admin: <$ka> .
             |<http://rdfh.ch/projects/0001> a knora-admin:knoraProject ;
             |  knora-admin:projectShortcode "0001" .
             |<$referencedUserIri> a knora-admin:User ;
             |  knora-admin:username "refUser" ;
             |  knora-admin:email "ref@example.com" ;
             |  knora-admin:isInProject <http://rdfh.ch/projects/0001> .
             |""".stripMargin
        for {
          env    <- makeTestEnv
          _      <- env.selectResultRef.set(selectResult)
          _      <- env.constructRdfRef.set(constructResult)
          task   <- env.service.createExport(testProject, testUser, skipAssets = true)
          result <- pollUntilDone(env.service, task.id)
          query  <- env.capturedConstructQueryRef.get
          _      <- env.service.deleteExport(task.id).catchAllCause(_ => ZIO.unit)
        } yield assertTrue(
          result.status == DataTaskStatus.Completed,
          query.isDefined,
          query.get.contains("VALUES"),
          query.get.contains(referencedUserIri),
        )
      },
      test("export without referenced users does not use VALUES") {
        for {
          env    <- makeTestEnv
          _      <- env.constructRdfRef.set("") // empty Turtle
          task   <- env.service.createExport(testProject, testUser, skipAssets = true)
          result <- pollUntilDone(env.service, task.id)
          query  <- env.capturedConstructQueryRef.get
          _      <- env.service.deleteExport(task.id).catchAllCause(_ => ZIO.unit)
        } yield assertTrue(
          result.status == DataTaskStatus.Completed,
          // When no referenced users, the base query is used (no VALUES)
          query.exists(!_.contains("VALUES")),
        )
      },
      test("export scopes cross-project memberships in admin.nq output") {
        val ka         = "http://www.knora.org/ontology/knora-admin#"
        val projectIri = testProject.id.value
        // User with memberships to both this project and another project
        val constructResult =
          s"""@prefix knora-admin: <$ka> .
             |<$projectIri> a knora-admin:knoraProject ;
             |  knora-admin:projectShortcode "0001" .
             |<http://rdfh.ch/users/user001> a knora-admin:User ;
             |  knora-admin:username "user1" ;
             |  knora-admin:email "user1@example.com" ;
             |  knora-admin:isInProject <$projectIri> ;
             |  knora-admin:isInProject <http://rdfh.ch/projects/OTHER> .
             |<http://rdfh.ch/groups/0001/grp1> a knora-admin:UserGroup ;
             |  knora-admin:belongsToProject <$projectIri> .
             |""".stripMargin
        for {
          env    <- makeTestEnv
          _      <- env.constructRdfRef.set(constructResult)
          task   <- env.service.createExport(testProject, testUser, skipAssets = true)
          result <- pollUntilDone(env.service, task.id)
          // Read the admin.nq output from the BagIt zip
          zipFile        <- env.storage.exportBagItZipPath(task.id)
          adminNqContent <- ZIO.scoped {
                              Files.createTempDirectoryScoped(Some("verify-scoping"), Seq.empty).flatMap { tempDir =>
                                BagIt.readAndValidateZip(zipFile, Some(tempDir)).flatMap { case (_, bagRoot) =>
                                  val adminNq = bagRoot / "data" / "rdf" / "admin.nq"
                                  Files.readAllBytes(adminNq).map(bytes => new String(bytes.toArray))
                                }
                              }
                            }
          _ <- env.service.deleteExport(task.id).catchAllCause(_ => ZIO.unit)
        } yield assertTrue(
          result.status == DataTaskStatus.Completed,
          adminNqContent.contains(projectIri),       // project membership retained
          !adminNqContent.contains("projects/OTHER"), // cross-project membership stripped
        )
      },
    ),
  ).provide(configLayer) @@ TestAspect.withLiveClock @@ TestAspect.withLiveRandom @@ TestAspect.timeout(30.seconds)
}
