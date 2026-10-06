/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import zio.*

import dsp.errors.ConflictException
import org.knora.webapi.IRI
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.resources.repo.service.ValueHasXmlBackfillRepo

/**
 * Backfills `knora-base:valueHasXml` on the existing formatted text values of a list of projects, one project at a
 * time, in paced batches.
 *
 * Log lines are documented for operators in `docs/03-endpoints/api-admin/maintenance.md`; keep it in sync.
 */
final class ValueHasXmlBackfillService private[service] (
  repo: ValueHasXmlBackfillRepo,
  renderer: ValueHasXmlRenderer,
  private[service] val running: Ref[Boolean],
  batchSize: Int,
  batchInterval: Duration,
) {

  /** Starts a run over the projects in the background; fails if a run is already active on this instance. */
  def start(projects: Seq[KnoraProject]): IO[ConflictException, Unit] =
    running.getAndSet(true).flatMap { busy =>
      if (busy) ZIO.fail(ConflictException("A valueHasXml backfill is already running on this instance"))
      else runAll(projects).ensuring(running.set(false)).forkDaemon.unit
    }

  /** Runs the backfill of each project in sequence; a project whose run fails does not stop the next one. */
  def runAll(projects: Seq[KnoraProject]): UIO[ValueHasXmlBackfillSummary] =
    for {
      summary <- ZIO.foldLeft(projects)(ValueHasXmlBackfillSummary.zero) { (total, project) =>
                   // `run` logs its own failure, so the error is dropped here. An interruption is not caught.
                   run(project)
                     .fold(_ => ValueHasXmlBackfillSummary.failed, ValueHasXmlBackfillSummary.succeeded)
                     .map(total + _)
                 }
      line = s"valueHasXml backfill of all projects finished: ${summary.describe}"
      _   <- if (summary.hasFailures) ZIO.logError(line) else ZIO.logInfo(line)
    } yield summary

  /** Runs the backfill of the project to completion and returns its counts. */
  def run(project: KnoraProject): Task[ValueHasXmlBackfillCounts] =
    ZIO.logAnnotate("shortcode", project.shortcode.value) {
      (for {
        candidates <- repo.selectCandidates(project)
        _          <- ZIO.logInfo(s"valueHasXml backfill started (candidates=${candidates.size})")
        batches     = candidates.grouped(batchSize).toSeq.zipWithIndex
        counts     <- ZIO.foldLeft(batches)(ValueHasXmlBackfillCounts.zero) { case (total, (batch, index)) =>
                    for {
                      _    <- ZIO.sleep(batchInterval).when(index > 0)
                      done <- processBatch(project, batch)
                      sum   = total + done
                      _    <- ZIO.logInfo(s"valueHasXml backfill progress: ${sum.describe}")
                    } yield sum
                  }
        line = s"valueHasXml backfill finished: ${counts.describe}"
        _   <- if (counts.failed > 0) ZIO.logError(line) else ZIO.logInfo(line)
      } yield counts).tapErrorCause { cause =>
        // The error class only: standoff and mapping errors can repeat user text.
        ZIO.logError(s"valueHasXml backfill failed with ${cause.squash.getClass.getName}")
      }
    }

  private def processBatch(project: KnoraProject, batch: Seq[IRI]): Task[ValueHasXmlBackfillCounts] =
    for {
      statements <- repo.loadStandoff(project, batch)
      grouped     = ValueHasXmlBackfill.groupByValue(statements, batch)
      rendered   <- ZIO.foreach(batch)(iri => renderOne(iri, grouped(iri))).map(_.flatten)
      _          <- ZIO.unless(rendered.isEmpty)(repo.insertXml(project, rendered))
    } yield ValueHasXmlBackfillCounts(batch.size, rendered.size, batch.size - rendered.size)

  /** A value that fails is logged and skipped; it stays a candidate for the next run. */
  private def renderOne(iri: IRI, value: Either[String, StoredTextValue]): Task[Option[(IRI, String)]] =
    value match {
      case Left(reason) => logFailure(iri, reason)
      case Right(text)  =>
        renderer
          .render(text)
          // A value whose standoff renders no XML would remain a candidate forever, so it counts as failed.
          .flatMap(ZIO.fromOption(_).orElseFail(new IllegalStateException("no XML rendered")))
          .map(xml => Some(iri -> xml))
          .catchAllCause { cause =>
            if (cause.isInterrupted) ZIO.refailCause(cause)
            else logFailure(iri, cause.squash.getClass.getName)
          }
    }

  private def logFailure(iri: IRI, reason: String): UIO[None.type] =
    ZIO.logWarning(s"valueHasXml backfill: $iri failed: $reason").as(None)
}

object ValueHasXmlBackfillService {

  private val BatchSize: Int          = 50
  private val BatchInterval: Duration = 1.second

  val layer: URLayer[ValueHasXmlBackfillRepo & ValueHasXmlRenderer, ValueHasXmlBackfillService] =
    ZLayer.fromZIO(
      for {
        repo     <- ZIO.service[ValueHasXmlBackfillRepo]
        renderer <- ZIO.service[ValueHasXmlRenderer]
        running  <- Ref.make(false)
      } yield new ValueHasXmlBackfillService(repo, renderer, running, BatchSize, BatchInterval),
    )
}
