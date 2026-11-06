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
          state   = new DataTaskState(ref, noopPersistence)
          storage = new ProjectDataImportStorageService()
          // null collaborators are only reachable from `doImport`; a call would die with an NPE
          service = new ProjectDataImportService(state, storage, null, null, null, null)
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
        } yield assertTrue(
          current.isFailed,
          current.errorMessage.contains(payloadTooLargeMessage(5L)),
          !exists,
        )
      }
    },
  ).provide(TestAppConfig.layer())

  def spec: Spec[Any, Any] =
    suite("ProjectDataImportService")(declaredLengthSuite, saveWithinLimitSuite, serviceSuite)
}
