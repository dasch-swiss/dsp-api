/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects.domain

import zio.*
import zio.test.*

import org.knora.webapi.TestDataFactory
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

object DataTaskStateSpec extends ZIOSpecDefault {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val user       = TestDataFactory.User.rootUser

  def spec: Spec[Any, Any] = suite("DataTaskState")(
    suite("makeNew")(
      test("should create a new task when no task exists") {
        for {
          state <- ZIO.service[DataTaskState]
          task  <- state.makeNew(projectIri, user)
        } yield assertTrue(
          task.projectIri == projectIri,
          task.createdBy == user,
          task.status == DataTaskStatus.InProgress,
        )
      },
      test("should fail with StatesExistError when a task already exists") {
        for {
          state    <- ZIO.service[DataTaskState]
          existing <- state.makeNew(projectIri, user)
          result   <- state.makeNew(projectIri, user).either
        } yield assertTrue(result == Left(StatesExistError(existing)))
      },
      test("should preserve existing task when makeNew fails") {
        for {
          state    <- ZIO.service[DataTaskState]
          existing <- state.makeNew(projectIri, user)
          _        <- state.makeNew(projectIri, user).either
          found    <- state.find(existing.id)
        } yield assertTrue(found == existing)
      },
    ),
    suite("find")(
      test("should return the task when it exists and id matches") {
        for {
          state <- ZIO.service[DataTaskState]
          task  <- state.makeNew(projectIri, user)
          found <- state.find(task.id)
        } yield assertTrue(found == task)
      },
      test("should fail with None when no task exists") {
        for {
          state  <- ZIO.service[DataTaskState]
          taskId <- DataTaskId.makeNew
          result <- state.find(taskId).either
        } yield assertTrue(result == Left(None))
      },
      test("should fail with None when id does not match") {
        for {
          state       <- ZIO.service[DataTaskState]
          _           <- state.makeNew(projectIri, user)
          otherTaskId <- DataTaskId.makeNew
          result      <- state.find(otherTaskId).either
        } yield assertTrue(result == Left(None))
      },
    ),
    suite("complete")(
      test("should mark task as completed") {
        for {
          state     <- ZIO.service[DataTaskState]
          task      <- state.makeNew(projectIri, user)
          completed <- state.complete(task.id)
          saved     <- state.find(task.id).orElseFail(Exception("should not happen"))
        } yield assertTrue(
          completed.id == task.id,
          completed.isCompleted,
          saved == completed,
        )
      },
      test("should be idempotent when task is already completed") {
        for {
          state      <- ZIO.service[DataTaskState]
          task       <- state.makeNew(projectIri, user)
          completed1 <- state.complete(task.id)
          completed2 <- state.complete(task.id)
        } yield assertTrue(completed1 == completed2)
      },
      test("should fail with StateFailedError when task is already failed") {
        for {
          state  <- ZIO.service[DataTaskState]
          task   <- state.makeNew(projectIri, user)
          failed <- state.fail(task.id)
          result <- state.complete(task.id).either
        } yield assertTrue(result == Left(Some(StateFailedError(failed))))
      },
      test("should fail with None when task does not exist") {
        for {
          state  <- ZIO.service[DataTaskState]
          taskId <- DataTaskId.makeNew
          result <- state.complete(taskId).either
        } yield assertTrue(result == Left(None))
      },
    ),
    suite("fail")(
      test("should mark task as failed") {
        for {
          state  <- ZIO.service[DataTaskState]
          task   <- state.makeNew(projectIri, user)
          failed <- state.fail(task.id)
          saved  <- state.find(task.id).orElseFail(Exception("should not happen"))
        } yield assertTrue(
          failed.id == task.id,
          failed.isFailed,
          saved == failed,
        )
      },
      test("should be idempotent when task is already failed") {
        for {
          state   <- ZIO.service[DataTaskState]
          task    <- state.makeNew(projectIri, user)
          failed1 <- state.fail(task.id)
          failed2 <- state.fail(task.id)
        } yield assertTrue(failed1 == failed2)
      },
      test("should fail with StateCompletedError when task is already completed") {
        for {
          state     <- ZIO.service[DataTaskState]
          task      <- state.makeNew(projectIri, user)
          completed <- state.complete(task.id)
          result    <- state.fail(task.id).either
        } yield assertTrue(result == Left(Some(StateCompletedError(completed))))
      },
      test("should fail with None when task does not exist") {
        for {
          state  <- ZIO.service[DataTaskState]
          taskId <- DataTaskId.makeNew
          result <- state.fail(taskId).either
        } yield assertTrue(result == Left(None))
      },
    ),
    suite("deleteIfNotInProgress")(
      test("should delete a completed task") {
        for {
          state <- ZIO.service[DataTaskState]
          task  <- state.makeNew(projectIri, user)
          _     <- state.complete(task.id)
          _     <- state.deleteIfNotInProgress(task.id)
          find  <- state.find(task.id).either
        } yield assertTrue(find == Left(None))
      },
      test("should delete a failed task") {
        for {
          state <- ZIO.service[DataTaskState]
          task  <- state.makeNew(projectIri, user)
          _     <- state.fail(task.id)
          _     <- state.deleteIfNotInProgress(task.id)
          find  <- state.find(task.id).either
        } yield assertTrue(find == Left(None))
      },
      test("should fail with StateInProgressError when task is in progress") {
        for {
          state  <- ZIO.service[DataTaskState]
          task   <- state.makeNew(projectIri, user)
          result <- state.deleteIfNotInProgress(task.id).either
        } yield assertTrue(result == Left(Some(StateInProgressError(task))))
      },
      test("should not delete when task is in progress") {
        for {
          state <- ZIO.service[DataTaskState]
          task  <- state.makeNew(projectIri, user)
          _     <- state.deleteIfNotInProgress(task.id).either
          found <- state.find(task.id)
        } yield assertTrue(found == task)
      },
      test("should fail with None when task does not exist") {
        for {
          state  <- ZIO.service[DataTaskState]
          taskId <- DataTaskId.makeNew
          result <- state.deleteIfNotInProgress(taskId).either
        } yield assertTrue(result == Left(None))
      },
    ),
    suite("concurrent makeNew")(
      test("only one of two concurrent makeNew calls should succeed") {
        for {
          state    <- ZIO.service[DataTaskState]
          results  <- ZIO.collectAllPar(Chunk.fill(2)(state.makeNew(projectIri, user).either))
          successes = results.collect { case Right(task) => task }
          failures  = results.collect { case Left(_: StatesExistError) => () }
        } yield assertTrue(successes.size == 1, failures.size == 1)
      },
    ),
    suite("atomicFindAndUpdate")(
      test("should update the task when found and function returns Right") {
        for {
          state   <- ZIO.service[DataTaskState]
          task    <- state.makeNew(projectIri, user)
          updated <- state.atomicFindAndUpdate[Nothing](task.id, t => Right(Some(t.complete())))
          saved   <- state.find(task.id).unsome
        } yield assertTrue(updated.get.isCompleted, updated == saved)
      },
      test("should clear the state when function returns Right(None)") {
        for {
          state  <- ZIO.service[DataTaskState]
          task   <- state.makeNew(projectIri, user)
          result <- state.atomicFindAndUpdate[Nothing](task.id, _ => Right(None))
          find   <- state.find(task.id).either
        } yield assertTrue(
          result.isEmpty,
          find == Left(None),
        )
      },
      test("should fail with Some(error) when function returns Left") {
        for {
          state  <- ZIO.service[DataTaskState]
          task   <- state.makeNew(projectIri, user)
          result <- state.atomicFindAndUpdate[String](task.id, _ => Left("some error")).either
        } yield assertTrue(result == Left(Some("some error")))
      },
      test("should not modify state when function returns Left") {
        for {
          state <- ZIO.service[DataTaskState]
          task  <- state.makeNew(projectIri, user)
          _     <- state.atomicFindAndUpdate[String](task.id, _ => Left("error")).either
          found <- state.find(task.id)
        } yield assertTrue(found == task)
      },
      test("should fail with None when task does not exist") {
        for {
          state  <- ZIO.service[DataTaskState]
          taskId <- DataTaskId.makeNew
          result <- state.atomicFindAndUpdate[String](taskId, _ => Right(None)).either
        } yield assertTrue(result == Left(None))
      },
    ),
  ).provide(DataTaskState.layer)
}
