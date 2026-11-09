/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.junit.runner.RunWith
import zio.*
import zio.nio.file.Files
import zio.stream.ZStream
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.TestDataFactory
import org.knora.webapi.core.TestAppConfig
import org.knora.webapi.slice.`export`.domain.ProjectDataImportService.*

@RunWith(classOf[DspZTestJUnitRunner])
class ProjectDataImportServiceSpec extends ZIOSpecDefault {

  private def bytes(n: Int): ZStream[Any, Throwable, Byte] = ZStream.fromChunk(Chunk.fill(n)('a'.toByte))

  private val noopPersistence = new DataTaskPersistence {
    def onChanged(task: CurrentDataTask): UIO[Unit] = ZIO.unit
    def onDeleted(taskId: DataTaskId): UIO[Unit]    = ZIO.unit
  }

  private val project = TestDataFactory.someProject
  private val root    = TestDataFactory.User.rootUser

  // The collaborators that only `doImport` reaches are null: a defect there fails the task.
  private final case class Fixture(
    service: ProjectDataImportService,
    state: DataTaskState,
    storage: ProjectDataImportStorageService,
    ref: Ref[Option[CurrentDataTask]],
    slot: ImportSlot,
  )

  private val fixture: URIO[Scope, Fixture] = for {
    ref    <- Ref.make[Option[CurrentDataTask]](None)
    slot   <- ImportSlot.layer.build.map(_.get)
    state   = new DataTaskState(ref, noopPersistence)
    storage = new ProjectDataImportStorageService()
  } yield Fixture(new ProjectDataImportService(state, storage, null, null, null, null, slot), state, storage, ref, slot)

  private def cleanup(fx: Fixture, id: DataTaskId): UIO[Unit] =
    fx.storage.dataImportJsonLdPath(id).flatMap(p => Files.deleteRecursive(p.parent.get)).ignore.unit

  private def slotIsFree(slot: ImportSlot): UIO[Boolean] =
    slot.run(ZIO.unit)(_ => ZIO.unit).as(true).orElse(ZIO.succeed(false))

  private def awaitSlotFree(slot: ImportSlot): UIO[Unit] =
    slot
      .run(ZIO.unit)(_ => ZIO.unit)
      .retry(Schedule.spaced(20.millis) && Schedule.recurs(250))
      .orElseFail(new IllegalStateException("The slot was not released."))
      .orDie
      .unit

  private val declaredLengthSuite = suite("checkDeclaredLength")(
    test("passes without a declared length") {
      assertTrue(checkDeclaredLength(None, 10) == Right(()))
    },
    test("passes when the declared length equals the limit") {
      assertTrue(checkDeclaredLength(Some(10), 10) == Right(()))
    },
    test("fails when the declared length is limit + 1") {
      assertTrue(checkDeclaredLength(Some(11), 10) == Left(PayloadTooLargeError(11, 10)))
    },
  )

  private val saveWithinLimitSuite = suite("saveWithinLimit")(
    test("writes the whole file and returns true when the stream equals the limit") {
      ZIO.scoped {
        for {
          dir    <- Files.createTempDirectoryScoped(Some("save-limit"), Seq.empty)
          file    = dir / "data.jsonld"
          within <- saveWithinLimit(bytes(5), file, 5)
          size   <- Files.size(file)
        } yield assertTrue(within, size == 5L)
      }
    },
    test("returns false and deletes the partial file when the stream exceeds the limit") {
      ZIO.scoped {
        for {
          dir    <- Files.createTempDirectoryScoped(Some("save-limit"), Seq.empty)
          file    = dir / "data.jsonld"
          within <- saveWithinLimit(bytes(1000), file, 5)
          exists <- Files.exists(file)
        } yield assertTrue(!within, !exists)
      }
    },
    test("stops pulling from a multi-chunk stream once limit + 1 bytes have been read") {
      ZIO.scoped {
        for {
          dir    <- Files.createTempDirectoryScoped(Some("save-limit"), Seq.empty)
          pulled <- Ref.make(0)
          // 1000 chunks of 3 bytes: only the first two (6 = limit + 1 bytes) are needed to decide.
          source  = ZStream.fromChunks(List.fill(1000)(Chunk[Byte](1, 2, 3))*)
          counted = source.chunks.tap(_ => pulled.update(_ + 1)).flattenChunks
          within <- saveWithinLimit(counted, dir / "data.jsonld", 5)
          n      <- pulled.get
        } yield assertTrue(!within, n <= 3)
      }
    },
  )

  private val serviceSuite = suite("importDataGraph")(
    test("an overflowing payload fails the task, removes the partial file and never starts the import") {
      ZIO.scoped {
        for {
          ref    <- Ref.make[Option[CurrentDataTask]](None)
          slot   <- ImportSlot.layer.build.map(_.get)
          state   = new DataTaskState(ref, noopPersistence)
          storage = new ProjectDataImportStorageService()
          // null collaborators are only reachable from `doImport`; a call would die with an NPE
          service = new ProjectDataImportService(state, storage, null, null, null, null, slot)
          task   <- service.importDataGraph(
                    TestDataFactory.someProject,
                    TestDataFactory.User.rootUser,
                    TestDataFactory.User.rootUser,
                    bytes(1000),
                    5L,
                  )
          current <- state.find(task.id)
          path    <- storage.dataImportJsonLdPath(task.id)
          exists  <- Files.exists(path)
          _       <- Files.deleteRecursive(path.parent.get).ignore
          free    <- slotIsFree(slot)
        } yield assertTrue(
          current.isFailed,
          current.errorMessage.contains(payloadTooLargeMessage(5L)),
          !exists,
          free,
        )
      }
    },
    test("an import that fails with a defect fails the task and frees the slot") {
      ZIO.scoped {
        for {
          fx   <- fixture
          task <- fx.service.importDataGraph(project, root, root, bytes(3))
          _    <- awaitSlotFree(fx.slot)
          cur  <- fx.state.find(task.id)
          _    <- cleanup(fx, task.id)
        } yield assertTrue(cur.isFailed)
      }
    },
    test("an interrupted save fails the task, deletes the partial file and frees the slot") {
      ZIO.scoped {
        for {
          fx    <- fixture
          fiber <- fx.service
                     .importDataGraph(project, root, root, bytes(3) ++ ZStream.never)
                     .fork
          task   <- fx.ref.get.repeatUntil(_.isDefined).map(_.get)
          path   <- fx.storage.dataImportJsonLdPath(task.id)
          _      <- Files.exists(path).repeatUntil(identity)
          _      <- fiber.interrupt
          cur    <- fx.state.find(task.id)
          exists <- Files.exists(path)
          free   <- slotIsFree(fx.slot)
          _      <- cleanup(fx, task.id)
        } yield assertTrue(cur.isFailed, !exists, free)
      }
    },
    test("a save defect fails the task, deletes the partial file and frees the slot") {
      ZIO.scoped {
        for {
          fx   <- fixture
          exit <-
            fx.service.importDataGraph(project, root, root, bytes(3) ++ ZStream.die(new RuntimeException("boom"))).exit
          task   <- fx.ref.get.map(_.get)
          path   <- fx.storage.dataImportJsonLdPath(task.id)
          exists <- Files.exists(path)
          free   <- slotIsFree(fx.slot)
          _      <- cleanup(fx, task.id)
        } yield assertTrue(exit.isFailure, task.isFailed, !exists, free)
      }
    },
    test("a taken slot gives ImportSlotBusyError and creates no task") {
      ZIO.scoped {
        for {
          fx     <- fixture
          _      <- fx.slot.run(ZIO.unit)(_ => ZIO.never)
          exit   <- fx.service.importDataGraph(project, root, root, bytes(3)).exit
          stored <- fx.ref.get
        } yield assertTrue(exit == Exit.fail(ImportSlotBusyError()), stored.isEmpty)
      }
    },
  ).provide(TestAppConfig.layer()) @@ TestAspect.withLiveClock @@ TestAspect.withLiveRandom

  def spec: Spec[Any, Any] =
    suite("ProjectDataImportService")(declaredLengthSuite, saveWithinLimitSuite, serviceSuite)
}
