/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.*
import zio.json.DecoderOps
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.nio.file.Files
import zio.nio.file.Path

import java.nio.file.StandardCopyOption
import java.time.Instant

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.UserIri

final case class PersistedDataTask(
  id: DataTaskId,
  projectIri: ProjectIri,
  status: DataTaskStatus,
  createdBy: UserIri,
  createdAt: Instant,
)

object PersistedDataTask {
  given JsonCodec[PersistedDataTask] = DeriveJsonCodec.gen[PersistedDataTask]

  def from(task: CurrentDataTask): PersistedDataTask =
    PersistedDataTask(
      id = task.id,
      projectIri = task.projectIri,
      status = task.status,
      createdBy = task.createdBy,
      createdAt = task.createdAt,
    )

  def toCurrentDataTask(persisted: PersistedDataTask): CurrentDataTask =
    CurrentDataTask.restore(
      persisted.id,
      persisted.projectIri,
      persisted.status,
      persisted.createdBy,
      persisted.createdAt,
    )
}

final class FilesystemDataTaskPersistence(basePath: Path) extends DataTaskPersistence {

  private def taskDir(taskId: DataTaskId): Path      = basePath / taskId.value
  private def taskJsonPath(taskId: DataTaskId): Path = taskDir(taskId) / "task.json"

  def onChanged(task: CurrentDataTask): UIO[Unit] = writeTaskJson(task)

  def onDeleted(taskId: DataTaskId): UIO[Unit] = {
    val target = taskJsonPath(taskId)
    Files.deleteIfExists(target).logError(s"Failed to delete task.json for $taskId").ignore
  }

  private def writeTaskJson(task: CurrentDataTask): UIO[Unit] = {
    val json    = zio.json.EncoderOps(PersistedDataTask.from(task)).toJson
    val dir     = taskDir(task.id)
    val target  = taskJsonPath(task.id)
    val tmpFile = dir / "task.json.tmp"
    (for {
      _ <- Files.createDirectories(dir).unlessZIO(Files.exists(dir))
      _ <- Files.writeBytes(tmpFile, Chunk.fromArray(json.getBytes("UTF-8")))
      _ <- Files.move(tmpFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } yield ()).logError(s"Failed to persist task ${task.id}").ignore
  }

  def restore(): UIO[Option[CurrentDataTask]] =
    (for {
      exists <- Files.exists(basePath)
      result <- if (!exists) ZIO.none
                else
                  for {
                    entries  <- Files.list(basePath).runCollect
                    dirs     <- ZIO.filter(entries)(p => Files.isDirectory(p))
                    taskJsons = dirs.map(_ / "task.json")
                    existing <- ZIO.filter(taskJsons)(Files.exists(_))
                    _        <-
                      ZIO.when(existing.size > 1)( // ideally this should never happen, but we want to log it if it does
                        ZIO.logWarning(
                          s"Multiple task.json files found in $basePath, only the first one will be restored",
                        ),
                      )
                    valid <- ZIO.foreach(existing.headOption)(readTaskJson)
                  } yield valid.flatten
    } yield result)
      .logError(s"Failed to restore task state from filesystem $basePath")
      .catchAll(_ => ZIO.none)

  private def readTaskJson(path: Path): UIO[Option[CurrentDataTask]] =
    (for {
      bytes  <- Files.readAllBytes(path)
      json    = new String(bytes.toArray, "UTF-8")
      parsed <- ZIO.fromEither(json.fromJson[PersistedDataTask]).mapError(new RuntimeException(_))
      task    = PersistedDataTask.toCurrentDataTask(parsed)
    } yield Some(task))
      .logError(s"Failed to read task.json at $path, treating as corrupted")
      .catchAll(_ => ZIO.none)
}

object FilesystemDataTaskPersistence {
  def exportLayer: URLayer[ProjectMigrationStorageService, DataTaskState] =
    ZLayer(ZIO.serviceWithZIO[ProjectMigrationStorageService](_.exportsDir)) >>> layer >>> ZLayer(restore)
  def importLayer: URLayer[ProjectMigrationStorageService, DataTaskState] =
    ZLayer(ZIO.serviceWithZIO[ProjectMigrationStorageService](_.importsDir)) >>> layer >>> ZLayer(restore)

  private def restore = for {
    // Restore the current task from the filesystem if it exists,
    // and mark it as failed if it was in progress
    fsPersistence <- ZIO.service[FilesystemDataTaskPersistence]
    restored      <- fsPersistence.restore()
    wasInProgress  = restored.exists(_.isInProgress)
    corrected      = restored.map { task =>
                  if (task.isInProgress) task.fail().getOrElse(task) else task
                }
    _ <- ZIO.when(wasInProgress && corrected.isDefined) {
           val task = corrected.get
           ZIO.logWarning(s"${task.id}: Marking previously in-progress export as failed due to service restart") *>
             fsPersistence.onChanged(task)
         }
    ref  <- Ref.make(corrected)
    state = new DataTaskState(ref, fsPersistence)
  } yield state

  def layer = ZLayer.derive[FilesystemDataTaskPersistence]

  def make(basePath: Path): FilesystemDataTaskPersistence = new FilesystemDataTaskPersistence(basePath)
}
