/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.*
import zio.nio.file.Files
import zio.nio.file.Path
import zio.test.*

import org.knora.webapi.TestDataFactory
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

object FilesystemDataTaskPersistenceSpec extends ZIOSpecDefault {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val user       = TestDataFactory.User.rootUser

  private def withTempDir(test: Path => ZIO[Any, Throwable, TestResult]): ZIO[Any, Throwable, TestResult] =
    Files.createTempDirectory(Some("fs-persistence-test-"), Seq.empty).flatMap { tmpDir =>
      test(tmpDir).ensuring(Files.deleteRecursive(tmpDir).ignore)
    }

  private def makePersistence(basePath: Path): FilesystemDataTaskPersistence =
    FilesystemDataTaskPersistence.make(basePath)

  private def makeTask(status: DataTaskStatus = DataTaskStatus.InProgress): UIO[CurrentDataTask] =
    for {
      id  <- DataTaskId.makeNew
      now <- Clock.instant
    } yield CurrentDataTask.restore(id, projectIri, status, user.userIri, now)

  def spec: Spec[Any, Any] = suite("FilesystemDataTaskPersistence")(
    suite("onChanged")(
      test("should write task.json when a task is created") {
        withTempDir { tmpDir =>
          val persistence = makePersistence(tmpDir)
          for {
            task   <- makeTask()
            _      <- persistence.onChanged(task)
            exists <- Files.exists(tmpDir / task.id.value / "task.json")
          } yield assertTrue(exists)
        }
      },
      test("should write valid JSON that can be read back") {
        withTempDir { tmpDir =>
          val persistence = makePersistence(tmpDir)
          for {
            task   <- makeTask()
            _      <- persistence.onChanged(task)
            bytes  <- Files.readAllBytes(tmpDir / task.id.value / "task.json")
            json    = new String(bytes.toArray, "UTF-8")
            parsed <-
              ZIO.fromEither(zio.json.DecoderOps(json).fromJson[PersistedDataTask]).mapError(new RuntimeException(_))
            roundTrip = PersistedDataTask.toCurrentDataTask(parsed)
          } yield assertTrue(
            roundTrip.id == task.id,
            roundTrip.projectIri == task.projectIri,
            roundTrip.status == task.status,
            roundTrip.createdAt == task.createdAt,
          )
        }
      },
      test("should update task.json when status changes") {
        withTempDir { tmpDir =>
          val persistence = makePersistence(tmpDir)
          for {
            task     <- makeTask()
            _        <- persistence.onChanged(task)
            completed = CurrentDataTask.restore(
                          task.id,
                          task.projectIri,
                          DataTaskStatus.Completed,
                          task.createdBy,
                          task.createdAt,
                        )
            _      <- persistence.onChanged(completed)
            bytes  <- Files.readAllBytes(tmpDir / task.id.value / "task.json")
            json    = new String(bytes.toArray, "UTF-8")
            parsed <-
              ZIO.fromEither(zio.json.DecoderOps(json).fromJson[PersistedDataTask]).mapError(new RuntimeException(_))
          } yield assertTrue(parsed.status == DataTaskStatus.Completed)
        }
      },
    ),
    suite("onDeleted")(
      test("should delete task.json") {
        withTempDir { tmpDir =>
          val persistence = makePersistence(tmpDir)
          for {
            task         <- makeTask()
            _            <- persistence.onChanged(task)
            existsBefore <- Files.exists(tmpDir / task.id.value / "task.json")
            _            <- persistence.onDeleted(task.id)
            existsAfter  <- Files.exists(tmpDir / task.id.value / "task.json")
          } yield assertTrue(existsBefore, !existsAfter)
        }
      },
      test("should not fail when task.json does not exist") {
        withTempDir { tmpDir =>
          val persistence = makePersistence(tmpDir)
          for {
            taskId <- DataTaskId.makeNew
            _      <- persistence.onDeleted(taskId)
          } yield assertCompletes
        }
      },
    ),
    suite("restore")(
      test("should return None when base directory does not exist") {
        withTempDir { tmpDir =>
          val persistence = makePersistence(tmpDir / "nonexistent")
          for {
            result <- persistence.restore()
          } yield assertTrue(result.isEmpty)
        }
      },
      test("should return None when base directory is empty") {
        withTempDir { tmpDir =>
          val persistence = makePersistence(tmpDir)
          for {
            result <- persistence.restore()
          } yield assertTrue(result.isEmpty)
        }
      },
      test("should restore a completed task") {
        withTempDir { tmpDir =>
          val persistence = makePersistence(tmpDir)
          for {
            task     <- makeTask(DataTaskStatus.Completed)
            _        <- persistence.onChanged(task)
            restored <- persistence.restore()
          } yield assertTrue(
            restored.isDefined,
            restored.get.id == task.id,
            restored.get.status == DataTaskStatus.Completed,
            restored.get.projectIri == task.projectIri,
          )
        }
      },
      test("should restore a failed task") {
        withTempDir { tmpDir =>
          val persistence = makePersistence(tmpDir)
          for {
            task     <- makeTask(DataTaskStatus.Failed)
            _        <- persistence.onChanged(task)
            restored <- persistence.restore()
          } yield assertTrue(
            restored.isDefined,
            restored.get.status == DataTaskStatus.Failed,
          )
        }
      },
      test("should restore an in-progress task as-is (caller marks it failed)") {
        withTempDir { tmpDir =>
          val persistence = makePersistence(tmpDir)
          for {
            task     <- makeTask(DataTaskStatus.InProgress)
            _        <- persistence.onChanged(task)
            restored <- persistence.restore()
          } yield assertTrue(
            restored.isDefined,
            restored.get.status == DataTaskStatus.InProgress,
          )
        }
      },
      test("should return None for corrupted task.json") {
        withTempDir { tmpDir =>
          val persistence = makePersistence(tmpDir)
          for {
            taskId <- DataTaskId.makeNew
            dir     = tmpDir / taskId.value
            _      <- Files.createDirectories(dir)
            _      <- Files.writeBytes(dir / "task.json", Chunk.fromArray("not valid json".getBytes("UTF-8")))
            result <- persistence.restore()
          } yield assertTrue(result.isEmpty)
        }
      },
    ),
  )
}
