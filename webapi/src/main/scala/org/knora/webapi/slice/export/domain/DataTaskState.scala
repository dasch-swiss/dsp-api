/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User

/// Errors that can occur when managing the state of a data task.
final case class StatesExistError(t: CurrentDataTask)
final case class StateInProgressError(t: CurrentDataTask)
final case class StateFailedError(t: CurrentDataTask)
final case class StateCompletedError(t: CurrentDataTask)

/**
 * Manages the state of a single data task (e.g. export or import) .
 * The state is stored in a Ref, which allows for atomic updates and thread safety.
 * Only one task can exist at a time for an instance of [[DataTaskState]], and the state transitions are managed through the provided methods.
 *
 * The state can be in one of three statuses: InProgress, Failed, or Completed.
 * The methods allow for creating a new task, finding an existing task by id, deleting a task if it's not in progress,
 * completing a task, and failing a task. Each method ensures that the state transitions are valid and handles errors appropriately.
 */
final class DataTaskState(ref: Ref[Option[CurrentDataTask]], persistence: DataTaskPersistence) { self =>

  /**
   * Create a new task for the given project and user.
   * If a task already exists in any state, return an error.
   *
   * @param projectIri the [[ProjectIri]] for which the task is being created
   * @param user the [[User]] who is creating the task
   * @return An IO that fails with [[StatesExistError]] if a task already exists.
   *         An IO that succeeds with [[CurrentDataTask]] when created successfully.
   */
  def makeNew(projectIri: ProjectIri, user: User): IO[StatesExistError, CurrentDataTask] = for {
    newState <- CurrentDataTask.makeNew(projectIri, user.userIri)
    result   <- self.ref.modify {
                case Some(exp) => (ZIO.fail(StatesExistError(exp)), Some(exp))
                case None      => (ZIO.succeed(newState), Some(newState))
              }.flatten
    _ <- persistence.onChanged(result)
  } yield result

  def find(taskId: DataTaskId): IO[Option[Nothing], CurrentDataTask] =
    self.ref.get.flatMap {
      case Some(exp) if exp.id == taskId => ZIO.succeed(exp)
      case _                             => ZIO.fail(None)
    }

  /**
   * Delete the task with the given id if it exists and is not in progress.
   * @param taskId the [[DataTaskId]] of the task to delete
   * @return An IO that fails with [[None]] if the task is not found.
   *         An IO that fails with [[StateInProgressError]] if the task is found but is still in progress.
   *         An IO that succeeds with [[Unit]] if the task was successfully deleted.
   */
  def deleteIfNotInProgress(taskId: DataTaskId): IO[Option[StateInProgressError], Unit] =
    atomicFindAndUpdate[StateInProgressError](
      taskId,
      {
        case exp if exp.isInProgress => Left(StateInProgressError(exp))
        case _                       => Right(None)
      },
    ).unit *> persistence.onDeleted(taskId)

  /**
   * Atomically find the task by id and update it using the provided function.
   * The function takes the current task and returns either an error or an optional new state.
   * If the task is found and the function returns Right, the state will be updated with the new state.
   *
   * @param taskId   the id [[DataTaskId]] of the task to find and update
   * @param f        the function to update the task if found, returns either an error or an new state
   * @tparam E       the type of the error that can be returned by the function
   * @return An IO that fails with a [[None]] if the task is not found,
   *         An IO that fails with an [[E]] if the task is found but the function returns an error
   *         An IO that succeeds with the new state [[Option[CurrentDataTask]]]
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
   * @param taskId the [[DataTaskId]] of the task to complete
   * @return An IO that fails with [[None]] if the task is not found.
   *         An IO that fails with [[StateFailedError]] if the task is found but is already failed.
   *         An IO that succeeds with the new state [[CurrentDataTask]].
   */
  def complete(taskId: DataTaskId): IO[Option[StateFailedError], CurrentDataTask] =
    self
      .atomicFindAndUpdate(taskId, _.complete().map(Some(_)))
      .someOrFail(None)
      .tap(task => persistence.onChanged(task))

  /**
   * Fail the task with the given id if it exists and is in progress.
   * @param taskId the  [[DataTaskId]] of the task to fail
   * @return An IO that fails with [[None]] if the task is not found.
   *         An IO that fails with [[StateCompletedError]] if the task is found but is already completed.
   *         An IO that succeeds with the new state [[CurrentDataTask]].
   */
  def fail(taskId: DataTaskId): IO[Option[StateCompletedError], CurrentDataTask] =
    self
      .atomicFindAndUpdate(taskId, _.fail().map(Some(_)))
      .someOrFail(None)
      .tap(task => persistence.onChanged(task))
}

object DataTaskState {
  val layer: URLayer[DataTaskPersistence, DataTaskState] =
    ZLayer.fromZIO(Ref.make[Option[CurrentDataTask]](None)) >>> ZLayer.derive[DataTaskState]

  def layerWithInitialState(initial: Option[CurrentDataTask]): URLayer[DataTaskPersistence, DataTaskState] =
    ZLayer.fromZIO(Ref.make(initial)) >>> ZLayer.derive[DataTaskState]
}
