/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects.domain
import zio.IO
import zio.Ref
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User

final case class StateExist(t: CurrentDataTask)

final class DataTaskState(ref: Ref[Option[CurrentDataTask]]) { self =>

  /**
   * Create a new task for the given project and user.
   * If a task already exists in any state, return an error.
   *
   * @param projectIri the IRI of the project for which the task is being created
   * @param user the user who is creating the task
   * @return An IO that fails with StateExist if a task already exists.
   *         An IO that succeeds with the new task if it was created successfully.
   */
  def makeNew(projectIri: ProjectIri, user: User): IO[StateExist, CurrentDataTask] = for {
    newState <- CurrentDataTask.makeNew(projectIri, user)
    result   <- self.ref.modify {
                case Some(exp) => (ZIO.fail(StateExist(exp)), Some(exp))
                case None      => (ZIO.succeed(newState), Some(newState))
              }.flatten
  } yield result

  def find(taskId: DataTaskId): IO[Option[Nothing], CurrentDataTask] =
    self.ref.get.flatMap {
      case Some(exp) if exp.id == taskId => ZIO.succeed(exp)
      case _                             => ZIO.fail(None)
    }

  /**
   * Delete the task with the given id if it exists and is not in progress.
   * @param taskId the id of the task to delete
   * @return An IO that fails with None if the task is not found.
   *         An IO that fails with StateExist if the task is found but is still in progress.
   *         An IO that succeeds with Unit if the task was successfully deleted.
   */
  def deleteIfNotInProgress(taskId: DataTaskId): IO[Option[StateExist], Unit] =
    atomicFindAndUpdate[StateExist](
      taskId,
      {
        case exp if exp.isInProgress => Left(StateExist(exp))
        case _                       => Right(None)
      },
    ).unit

  /**
   * Atomically find the task by id and update it using the provided function.
   * The function takes the current task and returns either an error or an optional new state.
   * If the task is found and the function returns Right, the state will be updated with the new state.
   *
   * @param taskId   the id of the task to find and update
   * @param f        the function to update the task if found, returns either an error or an new state
   * @tparam E       the type of the error that can be returned by the function
   * @return An IO that fails with a None if the task is not found,
   *         An IO that fails if the task is found but the function returns an error
   *         An IO that succeeds with the new state of the task
   */
  def atomicFindAndUpdate[E](
    taskId: DataTaskId,
    f: CurrentDataTask => Either[E, Option[CurrentDataTask]],
  ): IO[Option[E], Option[CurrentDataTask]] =
    self.ref.modify {
      case Some(exp) if exp.id == taskId =>
        f(exp) match
          case Left(e)         => (ZIO.fail(Some(e)), Some(exp))
          case Right(newState) => (ZIO.succeed(newState), newState)
      case other => (ZIO.fail(None), other)
    }.flatten

  /**
   * Complete the task with the given id if it exists and is in progress.
   * @param taskId the id of the task to complete
   * @return An IO that fails with None if the task is not found.
   *         An IO that succeeds with the new task state.
   */
  def complete(taskId: DataTaskId): IO[Option[Nothing], CurrentDataTask] =
    self
      .atomicFindAndUpdate[Nothing](taskId, t => Right(Some(t.complete())))
      .someOrFail(None)

  def fail(taskId: DataTaskId): IO[Option[Nothing], CurrentDataTask] =
    self
      .atomicFindAndUpdate[Nothing](taskId, t => Right(Some(t.fail())))
      .someOrFail(None)
}

object DataTaskState {
  def layer = ZLayer.fromZIO(Ref.make[Option[CurrentDataTask]](None)) >>> ZLayer.derive[DataTaskState]
}
