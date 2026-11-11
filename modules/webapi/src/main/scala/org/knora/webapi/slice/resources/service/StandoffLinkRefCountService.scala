/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import zio.*

import dsp.errors.ConflictException
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.resources.repo.service.StandoffLinkRefCountMismatch
import org.knora.webapi.slice.resources.repo.service.StandoffLinkRefCountRepo

/** Totals of a check over several projects: projects checked, projects whose check failed, mismatches found. */
final case class StandoffLinkRefCountSummary(projects: Int, projectsFailed: Int, mismatches: Int) {
  def +(other: StandoffLinkRefCountSummary): StandoffLinkRefCountSummary =
    StandoffLinkRefCountSummary(
      projects + other.projects,
      projectsFailed + other.projectsFailed,
      mismatches + other.mismatches,
    )

  def hasFailures: Boolean = projectsFailed > 0

  def describe: String = s"projects=$projects projectsFailed=$projectsFailed mismatches=$mismatches"
}

object StandoffLinkRefCountSummary {
  val zero: StandoffLinkRefCountSummary = StandoffLinkRefCountSummary(0, 0, 0)

  def succeeded(mismatches: Int): StandoffLinkRefCountSummary = StandoffLinkRefCountSummary(1, 0, mismatches)

  val failed: StandoffLinkRefCountSummary = StandoffLinkRefCountSummary(1, 1, 0)
}

/**
 * Runs the check of the standoff link reference counts, one project at a time, in the background. One run is active
 * per instance at a time.
 *
 * The check only reads and logs. It does not log text content: a line names resources and counts.
 *
 * Log lines are documented for operators in `docs/03-endpoints/api-admin/maintenance.md`; keep it in sync.
 */
final class StandoffLinkRefCountService private[service] (
  repo: StandoffLinkRefCountRepo,
  private[service] val running: Ref[Boolean],
) {

  /** Starts a check over the projects in the background; fails if a run is already active on this instance. */
  def startCheck(projects: Seq[KnoraProject]): IO[ConflictException, Unit] =
    startExclusive(checkAll(projects))

  /**
   * Takes the run guard and forks the job as a daemon, which releases the guard when it ends. Taking the guard and
   * forking are uninterruptible together, so an interruption cannot leave the guard set; the job stays interruptible.
   */
  private def startExclusive(job: UIO[Any]): IO[ConflictException, Unit] =
    ZIO.uninterruptibleMask { restore =>
      running.getAndSet(true).flatMap { busy =>
        if (busy) ZIO.fail(ConflictException("A standoff link reference count run is already running on this instance"))
        else restore(job).ensuring(running.set(false)).forkDaemon.unit
      }
    }

  /** Checks each project in sequence; a project whose check fails does not stop the next one. */
  def checkAll(projects: Seq[KnoraProject]): UIO[StandoffLinkRefCountSummary] =
    for {
      summary <-
        ZIO.foldLeft(projects)(StandoffLinkRefCountSummary.zero) { (total, project) =>
          // `check` logs its own failure, so the cause is dropped here: an error or a defect fails the project only.
          // An interruption of the run is not caught.
          check(project)
            .foldCause(
              _ => StandoffLinkRefCountSummary.failed,
              found => StandoffLinkRefCountSummary.succeeded(found.size),
            )
            .map(total + _)
        }
      line = s"standoff link reference count check of all projects finished: ${summary.describe}"
      _   <- if (summary.hasFailures) ZIO.logError(line)
           else if (summary.mismatches > 0) ZIO.logWarning(line)
           else ZIO.logInfo(line)
    } yield summary

  /** Checks the project and returns its mismatches, which are logged as well. */
  def check(project: KnoraProject): Task[Seq[StandoffLinkRefCountMismatch]] =
    ZIO.logAnnotate("shortcode", project.shortcode.value) {
      repo
        .selectMismatches(project)
        .tap { found =>
          val line = s"standoff link reference count check finished: mismatches=${found.size}"
          if (found.isEmpty) ZIO.logInfo(line)
          else ZIO.logWarning(line) *> ZIO.foreachDiscard(found)(logMismatch)
        }
        .tapErrorCause { cause =>
          // The error class only: a triplestore error can repeat data.
          ZIO.logError(s"standoff link reference count check failed with ${cause.squash.getClass.getName}")
        }
    }

  private def logMismatch(m: StandoffLinkRefCountMismatch): UIO[Unit] =
    ZIO.logWarning(
      s"standoff link reference count mismatch: source=${m.source} target=${m.target} " +
        s"stored=${m.storedCount.fold("none")(_.toString)} expected=${m.expectedCount} directLink=${m.directLink}",
    )
}

object StandoffLinkRefCountService {

  val layer: URLayer[StandoffLinkRefCountRepo, StandoffLinkRefCountService] =
    ZLayer.fromZIO(
      for {
        repo    <- ZIO.service[StandoffLinkRefCountRepo]
        running <- Ref.make(false)
      } yield new StandoffLinkRefCountService(repo, running),
    )
}
