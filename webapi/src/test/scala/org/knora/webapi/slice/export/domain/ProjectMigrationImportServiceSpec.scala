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
import zio.stream.ZStream
import zio.test.*

import java.nio.charset.StandardCharsets

import org.knora.bagit.BagIt
import org.knora.bagit.domain.BagInfo
import org.knora.bagit.domain.PayloadEntry
import org.knora.webapi.KnoraBaseVersion
import org.knora.webapi.TestDataFactory
import org.knora.webapi.http.version.BuildInfo
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.store.triplestore.api.TriplestoreService

object ProjectMigrationImportServiceSpec extends ZIOSpecDefault {

  // === Test Constants ===
  private val testProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/9999")
  private val testUser       = TestDataFactory.User.rootUser

  private val KnoraAdminPrefix = "http://www.knora.org/ontology/knora-admin#"
  private val RdfType          = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
  private val AdminGraph       = "http://www.knora.org/data/admin"

  // Minimal NQuads test data
  private val adminNq =
    s"""<http://rdfh.ch/projects/9999> <$RdfType> <${KnoraAdminPrefix}knoraProject> <$AdminGraph> .
       |<http://rdfh.ch/projects/9999> <${KnoraAdminPrefix}projectShortcode> "9999" <$AdminGraph> .
       |<http://rdfh.ch/users/test001> <$RdfType> <${KnoraAdminPrefix}User> <$AdminGraph> .
       |<http://rdfh.ch/users/test001> <${KnoraAdminPrefix}email> "test@example.com" <$AdminGraph> .
       |<http://rdfh.ch/users/test001> <${KnoraAdminPrefix}username> "testImportUser" <$AdminGraph> .
       |<http://rdfh.ch/groups/9999/testgroup> <$RdfType> <${KnoraAdminPrefix}UserGroup> <$AdminGraph> .
       |<http://rdfh.ch/groups/9999/testgroup> <${KnoraAdminPrefix}groupName> "TestImportGroup" <$AdminGraph> .
       |""".stripMargin

  private val dataNq =
    s"""<http://rdfh.ch/9999/resource001> <$RdfType> <http://www.knora.org/ontology/9999/test#TestResource> <http://www.knora.org/data/9999/test> .
       |""".stripMargin

  private val ontologyNq =
    s"""<http://www.knora.org/ontology/9999/test> <$RdfType> <http://www.w3.org/2002/07/owl#Ontology> <http://www.knora.org/ontology/9999/test> .
       |""".stripMargin

  private val permissionNq =
    s"""<http://rdfh.ch/permissions/9999/perm001> <$RdfType> <${KnoraAdminPrefix}Permission> <http://www.knora.org/data/permissions> .
       |""".stripMargin

  // === BagIt Zip Builder ===
  private def buildBagItZip(
    bagInfoFields: List[(String, String)] = List(
      "KnoraBase-Version" -> KnoraBaseVersion.toString,
      "Dsp-Api-Version"   -> BuildInfo.version,
    ),
    externalIdentifier: Option[String] = Some(testProjectIri.value),
    payloadFiles: Map[String, String] = Map(
      "rdf/admin.nq"      -> adminNq,
      "rdf/data.nq"       -> dataNq,
      "rdf/ontology-0.nq" -> ontologyNq,
    ),
  ): ZIO[Scope, Throwable, ZStream[Any, Throwable, Byte]] =
    for {
      tempDir <- Files.createTempDirectoryScoped(Some("bagit-builder"), Seq.empty)
      _       <- ZIO.foreachDiscard(payloadFiles) { case (name, content) =>
             val filePath = tempDir / name
             Files.createDirectories(filePath.parent.get) *>
               Files.writeBytes(filePath, Chunk.fromArray(content.getBytes(StandardCharsets.UTF_8)))
           }
      bagInfo = BagInfo(
                  externalIdentifier = externalIdentifier,
                  additionalFields = bagInfoFields,
                )
      entries = payloadFiles.keys.map(name => PayloadEntry.File(name, tempDir / name)).toList
      zipPath = tempDir / "output.zip"
      _      <- BagIt.create(entries, zipPath, bagInfo = Some(bagInfo))
    } yield ZStream.fromFile(zipPath.toFile)

  // === Stub Implementations ===

  // KnoraProjectRepo stub using Ref for configurable behavior
  private def stubProjectRepo(
    findByIdFn: Ref[ProjectIri => Task[Option[KnoraProject]]],
    findByShortcodeFn: Ref[Shortcode => Task[Option[KnoraProject]]],
  ): KnoraProjectRepo = new KnoraProjectRepo {
    override def findById(id: ProjectIri): Task[Option[KnoraProject]] =
      findByIdFn.get.flatMap(_(id))
    override def findByShortcode(shortcode: Shortcode): Task[Option[KnoraProject]] =
      findByShortcodeFn.get.flatMap(_(shortcode))
    override def findByShortname(shortname: Shortname): Task[Option[KnoraProject]] = ZIO.none
    override def findAll(): Task[Chunk[KnoraProject]]                              = ZIO.succeed(Chunk.empty)
    override def save(project: KnoraProject): Task[KnoraProject]                   = ZIO.succeed(project)
    override def delete(entity: KnoraProject): Task[Unit]                          = ZIO.unit
    override def deleteById(id: ProjectIri): Task[Unit]                            = ZIO.unit
  }

  // KnoraUserRepo stub using Ref for configurable behavior
  private def stubUserRepo(
    findByIdFn: Ref[UserIri => Task[Option[KnoraUser]]],
    findByEmailFn: Ref[Email => Task[Option[KnoraUser]]],
    findByUsernameFn: Ref[Username => Task[Option[KnoraUser]]],
  ): KnoraUserRepo = new KnoraUserRepo {
    override def findById(id: UserIri): Task[Option[KnoraUser]]     = findByIdFn.get.flatMap(_(id))
    override def findByEmail(email: Email): Task[Option[KnoraUser]] =
      findByEmailFn.get.flatMap(_(email))
    override def findByUsername(username: Username): Task[Option[KnoraUser]] =
      findByUsernameFn.get.flatMap(_(username))
    override def findByProjectMembership(projectIri: ProjectIri): Task[Chunk[KnoraUser]] =
      ZIO.succeed(Chunk.empty)
    override def findByProjectAdminMembership(projectIri: ProjectIri): Task[Chunk[KnoraUser]] =
      ZIO.succeed(Chunk.empty)
    override def findByGroupMembership(groupIri: GroupIri): Task[Chunk[KnoraUser]] =
      ZIO.succeed(Chunk.empty)
    override def findAll(): Task[Chunk[KnoraUser]]      = ZIO.succeed(Chunk.empty)
    override def save(user: KnoraUser): Task[KnoraUser] = ZIO.succeed(user)
  }

  // KnoraGroupRepo stub using Ref for configurable behavior
  private def stubGroupRepo(
    findByIdFn: Ref[GroupIri => Task[Option[KnoraGroup]]],
    findByNameFn: Ref[GroupName => Task[Option[KnoraGroup]]],
  ): KnoraGroupRepo = new KnoraGroupRepo {
    override def findById(id: GroupIri): Task[Option[KnoraGroup]]      = findByIdFn.get.flatMap(_(id))
    override def findByName(name: GroupName): Task[Option[KnoraGroup]] =
      findByNameFn.get.flatMap(_(name))
    override def findByProjectIri(projectIri: ProjectIri): Task[Chunk[KnoraGroup]] = ZIO.succeed(Chunk.empty)
    override def findAll(): Task[Chunk[KnoraGroup]]                                = ZIO.succeed(Chunk.empty)
    override def save(entity: KnoraGroup): Task[KnoraGroup]                        = ZIO.succeed(entity)
    override def delete(entity: KnoraGroup): Task[Unit]                            = ZIO.unit
    override def deleteById(id: GroupIri): Task[Unit]                              = ZIO.unit
  }

  // TriplestoreService stub that captures uploaded bytes
  private def stubTriplestoreService(uploadedRef: Ref[Chunk[Byte]]): TriplestoreService =
    new TriplestoreService {
      import org.knora.webapi.messages.store.triplestoremessages.*
      import org.knora.webapi.messages.util.rdf.QuadFormat
      import org.knora.webapi.messages.util.rdf.SparqlSelectResult
      import org.knora.webapi.messages.util.rdf.SparqlSelectResultBody
      import org.knora.webapi.messages.util.rdf.SparqlSelectResultHeader
      import org.knora.webapi.slice.common.domain.InternalIri
      import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.*
      import org.knora.webapi.store.triplestore.domain.TriplestoreStatus
      import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration

      override def uploadNQuads(stream: ZStream[Any, Throwable, Byte]): Task[Unit] =
        stream.runCollect.flatMap(bytes => uploadedRef.set(bytes))

      override def query(sparql: Ask): Task[Boolean]                       = ZIO.succeed(false)
      override def query(sparql: Construct): Task[SparqlConstructResponse] =
        ZIO.die(new UnsupportedOperationException("not used in import tests"))
      override def queryRdf(sparql: Construct): Task[String]       = ZIO.succeed("")
      override def query(sparql: Select): Task[SparqlSelectResult] =
        ZIO.succeed(SparqlSelectResult(SparqlSelectResultHeader(Seq.empty), SparqlSelectResultBody(Seq.empty)))
      override def query(sparql: Update): Task[Unit] = ZIO.unit
      override def queryToFile(
        sparql: Construct,
        graphIri: InternalIri,
        outputFile: Path,
        outputFormat: QuadFormat,
      ): Task[Unit] = ZIO.unit
      override def downloadGraph(
        graphIri: InternalIri,
        outputFile: Path,
        outputFormat: QuadFormat,
      ): Task[Unit]                                                                                                   = ZIO.unit
      override def resetTripleStoreContent(rdfDataObjects: List[RdfDataObject], prependDefaults: Boolean): Task[Unit] =
        ZIO.unit
      override def dropDataGraphByGraph(): Task[Unit] = ZIO.unit
      override def insertDataIntoTriplestore(
        rdfDataObjects: List[RdfDataObject],
        prependDefaults: Boolean,
      ): Task[Unit] =
        ZIO.unit
      override def checkTriplestore(): Task[TriplestoreStatus] =
        ZIO.succeed(TriplestoreStatus.Available)
      override def downloadRepository(
        outputFile: java.nio.file.Path,
        graphs: GraphsForMigration,
      ): Task[Unit]                                                            = ZIO.unit
      override def uploadRepository(inputFile: java.nio.file.Path): Task[Unit] = ZIO.unit
      override def dropGraph(graphName: String): Task[Unit]                    = ZIO.unit
      override def compact(): Task[Boolean]                                    = ZIO.succeed(false)
    }

  // Polling helper: retries getImportStatus until Completed or Failed
  private def pollUntilDone(
    service: ProjectMigrationImportService,
    taskId: DataTaskId,
  ): IO[Option[Nothing], CurrentDataTask] =
    service
      .getImportStatus(taskId)
      .flatMap { task =>
        task.status match {
          case DataTaskStatus.Completed  => ZIO.succeed(task)
          case DataTaskStatus.Failed     => ZIO.succeed(task)
          case DataTaskStatus.InProgress => ZIO.fail(None)
        }
      }
      .retry(Schedule.spaced(100.millis) && Schedule.recurs(100))

  // === Layer Setup ===
  // Creates a fresh test environment with all stubs, returns Refs for configuration
  private case class TestEnv(
    service: ProjectMigrationImportService,
    storage: ProjectMigrationStorageService,
    projectFindByIdRef: Ref[ProjectIri => Task[Option[KnoraProject]]],
    projectFindByShortcodeRef: Ref[Shortcode => Task[Option[KnoraProject]]],
    userFindByIdRef: Ref[UserIri => Task[Option[KnoraUser]]],
    userFindByEmailRef: Ref[Email => Task[Option[KnoraUser]]],
    userFindByUsernameRef: Ref[Username => Task[Option[KnoraUser]]],
    groupFindByIdRef: Ref[GroupIri => Task[Option[KnoraGroup]]],
    groupFindByNameRef: Ref[GroupName => Task[Option[KnoraGroup]]],
    uploadedBytesRef: Ref[Chunk[Byte]],
  )

  private val configLayer: ULayer[Unit] = Runtime.setConfigProvider(
    TypesafeConfigProvider.fromTypesafeConfig(ConfigFactory.load().getConfig("app").resolve),
  )

  private val makeTestEnv: ZIO[Any, Nothing, TestEnv] = for {
    projectFindByIdRef        <- Ref.make[ProjectIri => Task[Option[KnoraProject]]](_ => ZIO.none)
    projectFindByShortcodeRef <- Ref.make[Shortcode => Task[Option[KnoraProject]]](_ => ZIO.none)
    userFindByIdRef           <- Ref.make[UserIri => Task[Option[KnoraUser]]](_ => ZIO.none)
    userFindByEmailRef        <- Ref.make[Email => Task[Option[KnoraUser]]](_ => ZIO.none)
    userFindByUsernameRef     <- Ref.make[Username => Task[Option[KnoraUser]]](_ => ZIO.none)
    groupFindByIdRef          <- Ref.make[GroupIri => Task[Option[KnoraGroup]]](_ => ZIO.none)
    groupFindByNameRef        <- Ref.make[GroupName => Task[Option[KnoraGroup]]](_ => ZIO.none)
    uploadedBytesRef          <- Ref.make[Chunk[Byte]](Chunk.empty)

    projectRepo = stubProjectRepo(projectFindByIdRef, projectFindByShortcodeRef)
    userRepo    = stubUserRepo(userFindByIdRef, userFindByEmailRef, userFindByUsernameRef)
    groupRepo   = stubGroupRepo(groupFindByIdRef, groupFindByNameRef)
    triplestore = stubTriplestoreService(uploadedBytesRef)

    // Construct services with mock repos; null for unused dependencies
    projectService = KnoraProjectService(projectRepo, null, null)
    userService    = KnoraUserService(userRepo, null, null)
    groupService   = KnoraGroupService(groupRepo, null, null)
    storage        = new ProjectMigrationStorageService()

    // Build the import service layer
    persistence = new DataTaskPersistence {
                    def onChanged(task: CurrentDataTask): UIO[Unit] = ZIO.unit
                    def onDeleted(taskId: DataTaskId): UIO[Unit]    = ZIO.unit
                  }
    taskStateRef <- Ref.make[Option[CurrentDataTask]](None)
    taskState     = new DataTaskState(taskStateRef, persistence)
    service       =
      new ProjectMigrationImportService(taskState, groupService, projectService, storage, triplestore, userService)
  } yield TestEnv(
    service,
    storage,
    projectFindByIdRef,
    projectFindByShortcodeRef,
    userFindByIdRef,
    userFindByEmailRef,
    userFindByUsernameRef,
    groupFindByIdRef,
    groupFindByNameRef,
    uploadedBytesRef,
  )

  override def spec: Spec[Any, Any] = suite("ProjectMigrationImportService")(
    test("rejects corrupt archive") {
      ZIO.scoped {
        for {
          env <- makeTestEnv
          // Create a corrupt zip (valid zip, not valid BagIt — no bagit.txt)
          tempDir <- Files.createTempDirectoryScoped(Some("corrupt-zip"), Seq.empty)
          zipPath  = tempDir / "corrupt.zip"
          _       <- ZIO.attempt {
                 val fos = new java.io.FileOutputStream(zipPath.toFile)
                 val zos = new java.util.zip.ZipOutputStream(fos)
                 zos.putNextEntry(new java.util.zip.ZipEntry("dummy.txt"))
                 zos.write("not a bagit bag".getBytes)
                 zos.closeEntry()
                 zos.close()
               }
          stream = ZStream.fromFile(zipPath.toFile)

          // Import the corrupt zip
          task <- env.service.importDataExport(testProjectIri, testUser, stream)

          // Poll until done
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(
          result.status == DataTaskStatus.Failed,
        )
      }
    },
    test("rejects KnoraBase-Version mismatch") {
      ZIO.scoped {
        for {
          env    <- makeTestEnv
          stream <- buildBagItZip(
                      bagInfoFields = List(
                        "KnoraBase-Version" -> "999",
                        "Dsp-Api-Version"   -> BuildInfo.version,
                      ),
                    )
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(result.status == DataTaskStatus.Failed)
      }
    },
    test("rejects missing KnoraBase-Version") {
      ZIO.scoped {
        for {
          env    <- makeTestEnv
          stream <- buildBagItZip(
                      bagInfoFields = List(
                        "Dsp-Api-Version" -> BuildInfo.version,
                      ),
                    )
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(result.status == DataTaskStatus.Failed)
      }
    },
    test("rejects non-integer KnoraBase-Version") {
      ZIO.scoped {
        for {
          env    <- makeTestEnv
          stream <- buildBagItZip(
                      bagInfoFields = List(
                        "KnoraBase-Version" -> "abc",
                        "Dsp-Api-Version"   -> BuildInfo.version,
                      ),
                    )
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(result.status == DataTaskStatus.Failed)
      }
    },
    test("Dsp-Api-Version mismatch warns but does not fail") {
      ZIO.scoped {
        for {
          env    <- makeTestEnv
          stream <- buildBagItZip(
                      bagInfoFields = List(
                        "KnoraBase-Version" -> KnoraBaseVersion.toString,
                        "Dsp-Api-Version"   -> "0.0.0-test",
                      ),
                    )
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(result.status == DataTaskStatus.Completed)
      }
    },
    test("rejects missing admin.nq") {
      ZIO.scoped {
        for {
          env    <- makeTestEnv
          stream <- buildBagItZip(
                      payloadFiles = Map(
                        "rdf/data.nq"       -> dataNq,
                        "rdf/ontology-0.nq" -> ontologyNq,
                      ),
                    )
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(result.status == DataTaskStatus.Failed)
      }
    },
    test("rejects missing data.nq") {
      ZIO.scoped {
        for {
          env    <- makeTestEnv
          stream <- buildBagItZip(
                      payloadFiles = Map(
                        "rdf/admin.nq"      -> adminNq,
                        "rdf/ontology-0.nq" -> ontologyNq,
                      ),
                    )
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(result.status == DataTaskStatus.Failed)
      }
    },
    test("rejects missing ontology files") {
      ZIO.scoped {
        for {
          env    <- makeTestEnv
          stream <- buildBagItZip(
                      payloadFiles = Map(
                        "rdf/admin.nq" -> adminNq,
                        "rdf/data.nq"  -> dataNq,
                      ),
                    )
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(result.status == DataTaskStatus.Failed)
      }
    },
    test("rejects External-Identifier mismatch") {
      ZIO.scoped {
        for {
          env    <- makeTestEnv
          stream <- buildBagItZip(
                      externalIdentifier = Some("http://rdfh.ch/projects/8888"),
                    )
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(result.status == DataTaskStatus.Failed)
      }
    },
    test("rejects existing project by IRI") {
      ZIO.scoped {
        for {
          env    <- makeTestEnv
          _      <- env.projectFindByIdRef.set(_ => ZIO.some(TestDataFactory.someProject))
          stream <- buildBagItZip()
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(result.status == DataTaskStatus.Failed)
      }
    },
    test("rejects existing project by shortcode") {
      ZIO.scoped {
        for {
          env <- makeTestEnv
          // existsById returns false (no conflict by IRI)
          _ <- env.projectFindByIdRef.set(_ => ZIO.none)
          // findByShortcode returns Some for shortcode "9999" (from adminNq)
          _      <- env.projectFindByShortcodeRef.set(_ => ZIO.some(TestDataFactory.someProject))
          stream <- buildBagItZip()
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(result.status == DataTaskStatus.Failed)
      }
    },
    test("rejects existing user by IRI") {
      ZIO.scoped {
        for {
          env    <- makeTestEnv
          _      <- env.userFindByIdRef.set(_ => ZIO.some(TestDataFactory.User.testUser))
          stream <- buildBagItZip()
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(result.status == DataTaskStatus.Failed)
      }
    },
    test("rejects existing user by email") {
      ZIO.scoped {
        for {
          env <- makeTestEnv
          // findById returns None (no conflict by IRI), but findByEmail returns a user
          _      <- env.userFindByEmailRef.set(_ => ZIO.some(TestDataFactory.User.testUser))
          stream <- buildBagItZip()
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(result.status == DataTaskStatus.Failed)
      }
    },
    test("rejects existing user by username") {
      ZIO.scoped {
        for {
          env <- makeTestEnv
          // findById and findByEmail return None, but findByUsername returns a user
          _      <- env.userFindByUsernameRef.set(_ => ZIO.some(TestDataFactory.User.testUser))
          stream <- buildBagItZip()
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(result.status == DataTaskStatus.Failed)
      }
    },
    test("rejects existing group by IRI") {
      ZIO.scoped {
        for {
          env    <- makeTestEnv
          _      <- env.groupFindByIdRef.set(_ => ZIO.some(TestDataFactory.UserGroup.testUserGroup))
          stream <- buildBagItZip()
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(result.status == DataTaskStatus.Failed)
      }
    },
    test("rejects existing group by name") {
      ZIO.scoped {
        for {
          env <- makeTestEnv
          // findById returns None (no conflict by IRI), but findByName returns a group
          _      <- env.groupFindByNameRef.set(_ => ZIO.some(TestDataFactory.UserGroup.testUserGroup))
          stream <- buildBagItZip()
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
        } yield assertTrue(result.status == DataTaskStatus.Failed)
      }
    },
    test("successful import uploads all NQuads to triplestore") {
      ZIO.scoped {
        for {
          env    <- makeTestEnv
          stream <- buildBagItZip(
                      payloadFiles = Map(
                        "rdf/admin.nq"      -> adminNq,
                        "rdf/data.nq"       -> dataNq,
                        "rdf/ontology-0.nq" -> ontologyNq,
                        "rdf/permission.nq" -> permissionNq,
                      ),
                    )
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
          bytes  <- env.uploadedBytesRef.get
        } yield assertTrue(
          result.status == DataTaskStatus.Completed,
          bytes.nonEmpty,
        )
      }
    },
    test("uploaded stream contains data from all NQuads files") {
      ZIO.scoped {
        for {
          env    <- makeTestEnv
          stream <- buildBagItZip(
                      payloadFiles = Map(
                        "rdf/admin.nq"      -> adminNq,
                        "rdf/data.nq"       -> dataNq,
                        "rdf/ontology-0.nq" -> ontologyNq,
                        "rdf/permission.nq" -> permissionNq,
                      ),
                    )
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
          bytes  <- env.uploadedBytesRef.get
          content = new String(bytes.toArray, StandardCharsets.UTF_8)
        } yield assertTrue(
          result.status == DataTaskStatus.Completed,
          content.contains("projectShortcode"),
          content.contains("resource001"),
          content.contains("owl#Ontology"),
          content.contains("Permission"),
        )
      }
    },
    test("rejects duplicate import while one exists") {
      ZIO.scoped {
        for {
          env     <- makeTestEnv
          stream1 <- buildBagItZip()
          stream2 <- buildBagItZip()
          task    <- env.service.importDataExport(testProjectIri, testUser, stream1)
          exit    <- env.service.importDataExport(testProjectIri, testUser, stream2).exit
        } yield assertTrue(
          exit.isFailure,
          exit == Exit.fail(ImportExistsError(task)),
        )
      }
    },
    test("temp directory is cleaned up on success") {
      ZIO.scoped {
        for {
          env    <- makeTestEnv
          stream <- buildBagItZip()
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
          // Give scope cleanup a moment to complete (cleanup runs after task is marked Completed)
          _          <- ZIO.sleep(200.millis)
          zipPath    <- env.storage.importBagItZipPath(task.id)
          tempDir     = zipPath.parent.get / "temp"
          zipExists  <- Files.exists(zipPath)
          tempExists <- Files.exists(tempDir)
        } yield assertTrue(
          result.status == DataTaskStatus.Completed,
          zipExists,
          !tempExists,
        )
      }
    },
    test("temp directory is cleaned up on failure") {
      ZIO.scoped {
        for {
          env    <- makeTestEnv
          stream <- buildBagItZip(
                      bagInfoFields = List(
                        "KnoraBase-Version" -> "999",
                        "Dsp-Api-Version"   -> BuildInfo.version,
                      ),
                    )
          task   <- env.service.importDataExport(testProjectIri, testUser, stream)
          result <- pollUntilDone(env.service, task.id)
          // Cleanup runs before task is marked Failed, so no extra sleep needed
          zipPath    <- env.storage.importBagItZipPath(task.id)
          tempDir     = zipPath.parent.get / "temp"
          zipExists  <- Files.exists(zipPath)
          tempExists <- Files.exists(tempDir)
        } yield assertTrue(
          result.status == DataTaskStatus.Failed,
          zipExists,
          !tempExists,
        )
      }
    },
  ).provide(configLayer) @@ TestAspect.withLiveClock @@ TestAspect.withLiveRandom @@ TestAspect.timeout(30.seconds)
}
