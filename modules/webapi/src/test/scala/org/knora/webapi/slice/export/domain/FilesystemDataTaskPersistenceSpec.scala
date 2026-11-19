/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.junit.runner.RunWith
import zio.*
import zio.nio.file.Files
import zio.nio.file.Path
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.TestDataFactory
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.domain.ServiceName

@RunWith(classOf[DspZTestJUnitRunner])
class FilesystemDataTaskPersistenceSpec extends ZIOSpecDefault {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val user       = TestDataFactory.User.rootUser
  private val creator    = TaskCreator.ByUser(user.userIri)

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
    } yield CurrentDataTask.restore(id, projectIri, status, creator, now)

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
    suite("onBehalfOf field")(
      test("decodes a task.json written before the field (no onBehalfOf key) as None") {
        for {
          id        <- DataTaskId.makeNew
          now       <- Clock.instant
          legacyJson =
            s"""{"id":"${id.value}","projectIri":"${projectIri.value}","status":"in_progress","createdBy":"${user.userIri.value}","createdAt":"${now.toString}"}"""
          parsed <-
            ZIO
              .fromEither(zio.json.DecoderOps(legacyJson).fromJson[PersistedDataTask])
              .mapError(new RuntimeException(_))
        } yield assertTrue(
          parsed.onBehalfOf.isEmpty,
          parsed.id == id,
          parsed.status == DataTaskStatus.InProgress,
        )
      },
      test("persists and restores the onBehalfOf subject") {
        withTempDir { tmpDir =>
          val persistence = makePersistence(tmpDir)
          val subject     = UserIri.unsafeFrom("http://rdfh.ch/users/incunabulaMemberUser")
          for {
            id  <- DataTaskId.makeNew
            now <- Clock.instant
            task =
              CurrentDataTask.restore(id, projectIri, DataTaskStatus.Completed, creator, now, None, Some(subject))
            _        <- persistence.onChanged(task)
            restored <- persistence.restore()
          } yield assertTrue(
            restored.isDefined,
            restored.get.onBehalfOf.contains(subject),
            restored.get.createdBy == creator,
          )
        }
      },
    ),
    suite("createdBy field")(
      test("restores a task file whose createdBy is a bare user IRI") {
        withTempDir { tmpDir =>
          for {
            id       <- DataTaskId.makeNew
            _        <- writeTaskFile(tmpDir, id, s""""createdBy":"${user.userIri.value}"""")
            restored <- makePersistence(tmpDir).restore()
          } yield assertTrue(
            restored.map(_.id).contains(id),
            restored.map(_.createdBy).contains(creator),
            restored.exists(_.onBehalfOf.isEmpty),
          )
        }
      },
      test("restores a task file whose createdBy is a bare user IRI, with onBehalfOf") {
        withTempDir { tmpDir =>
          val subject = UserIri.unsafeFrom("http://rdfh.ch/users/incunabulaMemberUser")
          for {
            id <- DataTaskId.makeNew
            _  <- writeTaskFile(
                   tmpDir,
                   id,
                   s""""createdBy":"${user.userIri.value}","onBehalfOf":"${subject.value}"""",
                 )
            restored <- makePersistence(tmpDir).restore()
          } yield assertTrue(
            restored.map(_.createdBy).contains(creator),
            restored.flatMap(_.onBehalfOf).contains(subject),
          )
        }
      },
      test("persists and restores a service creator") {
        withTempDir { tmpDir =>
          val persistence = makePersistence(tmpDir)
          val service     = TaskCreator.ByService(ServiceName.unsafeFrom("dsp-repository"))
          for {
            id       <- DataTaskId.makeNew
            now      <- Clock.instant
            _        <- persistence.onChanged(CurrentDataTask.restore(id, projectIri, DataTaskStatus.Completed, service, now))
            bytes    <- Files.readAllBytes(tmpDir / id.value / "task.json")
            restored <- persistence.restore()
          } yield assertTrue(
            new String(bytes.toArray, "UTF-8").contains(""""createdBy":"urn:dsp:service:dsp-repository""""),
            restored.map(_.createdBy).contains(service),
          )
        }
      },
      test("treats a task file whose createdBy is a service with an invalid name as corrupt") {
        withTempDir { tmpDir =>
          for {
            id       <- DataTaskId.makeNew
            _        <- writeTaskFile(tmpDir, id, """"createdBy":"urn:dsp:service:Not_Valid"""")
            restored <- makePersistence(tmpDir).restore()
          } yield assertTrue(restored.isEmpty)
        }
      },
    ),
  )

  /** Writes a completed task's `task.json` by hand, with `createdBy` (and any further fields) given as raw JSON. */
  private def writeTaskFile(baseDir: Path, id: DataTaskId, createdByFields: String): Task[Unit] =
    for {
      now <- Clock.instant
      json =
        s"""{"id":"${id.value}","projectIri":"${projectIri.value}","status":"completed",$createdByFields,"createdAt":"$now"}"""
      dir = baseDir / id.value
      _  <- Files.createDirectories(dir)
      _  <- Files.writeBytes(dir / "task.json", Chunk.fromArray(json.getBytes("UTF-8")))
    } yield ()
}
