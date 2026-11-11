/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import zio.*

import dsp.errors.ConflictException
import org.knora.webapi.IRI
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate
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
 * The outcome of a repair run over one project.
 *
 * @param dryRun    whether nothing was written
 * @param planned   the repairs a dry run would apply; zero for a real run
 * @param applied   the repairs that took effect
 * @param skipped   the mismatches that are not repairable, by reason
 * @param stale     the repairs not applied because the stored state changed after the check
 * @param failed    the mismatches whose repair raised an error
 * @param remaining the mismatches left after a real run; none for a dry run
 */
final case class StandoffLinkRefCountRepairSummary(
  dryRun: Boolean,
  planned: Int,
  applied: Int,
  skipped: Map[StandoffLinkRefCountSkipReason, Int],
  stale: Int,
  failed: Int,
  remaining: Option[Int],
) {
  def describe: String = {
    val reasons =
      if (skipped.isEmpty) ""
      else s" (${skipped.toSeq.sortBy(_._1.ordinal).map { case (reason, n) => s"$reason=$n" }.mkString(", ")})"
    s"dryRun=$dryRun planned=$planned applied=$applied skipped=${skipped.values.sum}$reasons " +
      s"stale=$stale failed=$failed remaining=${remaining.fold("n/a")(_.toString)}"
  }
}

/**
 * Runs the maintenance actions on the standoff link reference counts, one project at a time, in the background. One
 * run is active per instance at a time.
 *
 * The check only logs. The repair writes the repairs of [[StandoffLinkRefCountRepair]] unless it is a dry run. Neither
 * logs text content: a line names resources and counts.
 *
 * Log lines are documented for operators in `docs/03-endpoints/api-admin/maintenance.md`; keep it in sync.
 */
final class StandoffLinkRefCountService private[service] (
  repo: StandoffLinkRefCountRepo,
  resourceExists: IRI => Task[Boolean],
  mintValueIri: ResourceIri => Task[ValueIri],
  private[service] val running: Ref[Boolean],
)(using sf: StringFormatter) {

  /** Starts a check over the projects in the background; fails if a run is already active on this instance. */
  def startCheck(projects: Seq[KnoraProject]): IO[ConflictException, Unit] =
    startExclusive(checkAll(projects))

  /** Starts a repair of the project in the background; fails if a run is already active on this instance. */
  def startRepair(project: KnoraProject, dryRun: Boolean): IO[ConflictException, Unit] =
    startExclusive(repair(project, dryRun).ignore)

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

  /**
   * Repairs the mismatches of the project one at a time and returns the totals. A dry run reads only and logs what it
   * would apply. A row that fails is logged and does not stop the next one.
   *
   * A real run plans and applies each row under the per-resource lock of its source, the lock the value write path
   * takes. Under the lock the stored-count guard of the update suffices: a write that changes the expected count of a
   * pair also changes its stored count.
   */
  def repair(project: KnoraProject, dryRun: Boolean): Task[StandoffLinkRefCountRepairSummary] =
    ZIO.logAnnotate("shortcode", project.shortcode.value) {
      (for {
        rows      <- repo.selectMismatches(project)
        outcomes  <- ZIO.foreach(rows)(repairRow(project, _, dryRun))
        remaining <- if (dryRun) ZIO.none else repo.selectMismatches(project).map(found => Some(found.size))
        summary    = summarize(dryRun, outcomes, remaining)
        _         <- ZIO.foreachDiscard(remaining)(n =>
               ZIO.logInfo(s"standoff link reference count repair remaining mismatches=$n"),
             )
        _ <- ZIO.logInfo(s"standoff link reference count repair finished: ${summary.describe}")
      } yield summary).tapErrorCause { cause =>
        ZIO.logError(s"standoff link reference count repair failed with ${cause.squash.getClass.getName}")
      }
    }

  private enum RowOutcome {
    case Planned, Applied, Stale, Failed
    case Skipped(reason: StandoffLinkRefCountSkipReason)
  }

  private def summarize(
    dryRun: Boolean,
    outcomes: Seq[RowOutcome],
    remaining: Option[Int],
  ): StandoffLinkRefCountRepairSummary =
    StandoffLinkRefCountRepairSummary(
      dryRun = dryRun,
      planned = outcomes.count(_ == RowOutcome.Planned),
      applied = outcomes.count(_ == RowOutcome.Applied),
      skipped = outcomes.collect { case RowOutcome.Skipped(reason) => reason }.groupMapReduce(identity)(_ => 1)(_ + _),
      stale = outcomes.count(_ == RowOutcome.Stale),
      failed = outcomes.count(_ == RowOutcome.Failed),
      remaining = remaining,
    )

  private def repairRow(
    project: KnoraProject,
    mismatch: StandoffLinkRefCountMismatch,
    dryRun: Boolean,
  ): Task[RowOutcome] =
    val row = planRow(mismatch).flatMap {
      case StandoffLinkRefCountRepairPlan.Skip(m, reason) =>
        ZIO
          .logWarning(
            s"standoff link reference count repair skipped: source=${m.source} target=${m.target} reason=$reason",
          )
          .as(RowOutcome.Skipped(reason))
      case StandoffLinkRefCountRepairPlan.Repair(m, update) if dryRun =>
        ZIO
          .logInfo(s"standoff link reference count repair planned: ${describeRepair(m, update)}")
          .as(RowOutcome.Planned)
      case StandoffLinkRefCountRepairPlan.Repair(m, update) =>
        repo.applyRepair(project, m.source, update).flatMap {
          case true =>
            ZIO
              .logInfo(s"standoff link reference count repair applied: ${describeRepair(m, update)}")
              .as(RowOutcome.Applied)
          case false =>
            ZIO
              .logWarning(
                s"standoff link reference count repair skipped: changed since the check: source=${m.source} target=${m.target}",
              )
              .as(RowOutcome.Stale)
        }
    }
    (if (dryRun) row else underSourceLock(mismatch.source)(row)).catchAllCause { cause =>
      if (cause.isInterruptedOnly) ZIO.failCause(cause)
      else
        // The error class only: a triplestore error can repeat data.
        ZIO
          .logError(
            s"standoff link reference count repair failed: source=${mismatch.source} target=${mismatch.target} " +
              s"error=${cause.squash.getClass.getName}",
          )
          .as(RowOutcome.Failed)
    }

  private def underSourceLock[A](source: IRI)(task: Task[A]): Task[A] =
    Random.nextUUID.flatMap(apiRequestId => IriLocker.runWithIriLock(apiRequestId, source)(task))

  /** A mismatch whose target is missing is skipped before a link value IRI is minted. */
  private def planRow(mismatch: StandoffLinkRefCountMismatch): Task[StandoffLinkRefCountRepairPlan] =
    resourceExists(mismatch.target).flatMap {
      case false =>
        ZIO.succeed(StandoffLinkRefCountRepairPlan.Skip(mismatch, StandoffLinkRefCountSkipReason.TargetMissing))
      case true =>
        for {
          source   <- ZIO.fromEither(ResourceIri.from(mismatch.source)).mapError(new IllegalArgumentException(_))
          valueIri <- mintValueIri(source)
        } yield StandoffLinkRefCountRepair.plan(mismatch, targetExists = true, valueIri)
    }

  private def describeRepair(m: StandoffLinkRefCountMismatch, update: SparqlTemplateLinkUpdate): String = {
    val directLink =
      if (update.insertDirectLink) "insert" else if (update.deleteDirectLink) "delete" else "none"
    s"source=${m.source} target=${m.target} oldCount=${m.storedCount.fold("none")(_.toString)} " +
      s"newCount=${update.newReferenceCount} directLink=$directLink"
  }

  private def logMismatch(m: StandoffLinkRefCountMismatch): UIO[Unit] =
    ZIO.logWarning(
      s"standoff link reference count mismatch: source=${m.source} target=${m.target} " +
        s"stored=${m.storedCount.fold("none")(_.toString)} expected=${m.expectedCount} directLink=${m.directLink}",
    )
}

object StandoffLinkRefCountService {

  /** Mints a value IRI of the resource that no triple uses yet. */
  private def mintUnusedValueIri(iriService: IriService, resource: ResourceIri): Task[ValueIri] =
    ZIO.attempt(ValueIri.makeNew(resource)).flatMap { candidate =>
      iriService
        .checkIriExists(candidate.value)
        .flatMap(used => if (used) mintUnusedValueIri(iriService, resource) else ZIO.succeed(candidate))
    }

  val layer: URLayer[StandoffLinkRefCountRepo & IriService & StringFormatter, StandoffLinkRefCountService] =
    ZLayer.fromZIO(
      for {
        repo       <- ZIO.service[StandoffLinkRefCountRepo]
        iriService <- ZIO.service[IriService]
        sf         <- ZIO.service[StringFormatter]
        running    <- Ref.make(false)
      } yield new StandoffLinkRefCountService(
        repo,
        iri => iriService.checkIriExists(iri),
        mintUnusedValueIri(iriService, _),
        running,
      )(using sf),
    )
}
