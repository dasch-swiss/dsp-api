/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import io.opentelemetry.api.trace.Span
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing

import dsp.errors.ConflictException
import org.knora.webapi.IRI
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.ValueHasXmlBackfillConfig
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.infrastructure.SanitizedSpan
import org.knora.webapi.slice.resources.repo.ValueHasXmlBackfillRepo
import org.knora.webapi.slice.resources.service.ValueHasXmlBackfill.RunState
import org.knora.webapi.slice.resources.service.ValueHasXmlBackfill.StopReason

/** Backfills `knora-base:valueHasXml` on the existing formatted text values of one project, in paced batches. */
final case class ValueHasXmlBackfillService(
  repo: ValueHasXmlBackfillRepo,
  renderer: ValueHasXmlRenderer,
  appConfig: AppConfig,
  tracing: Tracing,
  running: Ref[Option[ProjectIri]],
) {
  import ValueHasXmlBackfillService.*

  private val config: ValueHasXmlBackfillConfig = appConfig.valueHasXmlBackfill

  /** Starts a run in the background; fails if a run is already active on this instance. */
  def start(project: KnoraProject): IO[ConflictException, Unit] = startFiber(project).unit

  private[service] def startFiber(project: KnoraProject): IO[ConflictException, Fiber[Nothing, Unit]] =
    ZIO.uninterruptibleMask { restore =>
      running
        .modify(held => if (held.isEmpty) (None, Some(project.id)) else (held, held))
        .flatMap {
          case Some(held) =>
            ZIO.fail(ConflictException(s"A valueHasXml backfill is already running on this instance (${held.value})"))
          case None =>
            // `run` has logged its own outcome by the time it ends, so the cause is dropped here.
            restore(run(project).unit).catchAllCause(_ => ZIO.unit).ensuring(running.set(None)).forkDaemon
        }
    }

  /** Runs the backfill of the project to completion and returns what it did. */
  def run(project: KnoraProject): Task[ValueHasXmlBackfillReport] =
    ZIO.logAnnotate("shortcode", project.shortcode.value) {
      for {
        // Written by `step` for the failure and interrupt log lines only; the loop never reads it.
        partial    <- Ref.make(ValueHasXmlBackfillReport.zero)
        _          <- ZIO.logInfo(s"valueHasXml backfill started (batchSize=${config.batchSize})")
        finalState <- SanitizedSpan
                        .withSpan(tracing, "value_has_xml_backfill", ExitReasonKey) { span =>
                          ZIO
                            .iterate(RunState.zero)(!_.done)(step(project, partial))
                            .tap(state => ZIO.succeed(setReportAttributes(span, project, state.report)))
                        }
                        .onExit {
                          case Exit.Success(_)     => ZIO.unit
                          case Exit.Failure(cause) => logAbnormalEnd(partial, cause)
                        }
        _ <- logOutcome(finalState)
      } yield finalState.report
    }

  private def logAbnormalEnd(partial: Ref[ValueHasXmlBackfillReport], cause: Cause[Throwable]): UIO[Unit] =
    partial.get.flatMap { report =>
      if (cause.isInterrupted) ZIO.logWarning(s"valueHasXml backfill interrupted: ${describe(report)}")
      else
        ZIO.logError(
          s"valueHasXml backfill failed with ${cause.squash.getClass.getName}: ${describe(report)}",
        )
    }

  private def logOutcome(state: RunState): UIO[Unit] = {
    val line = s"valueHasXml backfill finished: ${describe(state.report)} stop=${state.stop.fold("none")(_.toString)}"
    if (state.report.failed > 0) ZIO.logError(line) else ZIO.logInfo(line)
  }

  private def step(project: KnoraProject, partial: Ref[ValueHasXmlBackfillReport])(state: RunState): Task[RunState] =
    for {
      started    <- Clock.nanoTime
      candidates <- stage("select")(repo.selectCandidates(project, config.batchSize + state.failed.size))
      batch       = ValueHasXmlBackfill.nextBatch(candidates, state.failed, config.batchSize)
      next       <- ValueHasXmlBackfill.stopReason(state, batch, config.maxFailures) match {
                case Some(reason) => ZIO.succeed(stopped(state, reason))
                case None         => processBatch(project, state, batch)
              }
      _ <- partial.set(next.report)
      _ <- ZIO.when(!next.done)(pace(started))
    } yield next

  private def stopped(state: RunState, reason: StopReason): RunState =
    state.copy(stop = Some(reason), report = state.report.copy(stoppedEarly = reason.stoppedEarly))

  private def processBatch(project: KnoraProject, state: RunState, batch: Seq[IRI]): Task[RunState] =
    for {
      statements <- stage("load")(repo.loadStandoff(project, batch))
      grouped     = ValueHasXmlBackfill.groupByValue(statements, batch)
      outcome    <- stage("render")(renderAll(batch, grouped, state.mappings))
      _          <- stage("write")(ZIO.unless(outcome.rendered.isEmpty)(repo.insertXml(project, outcome.rendered)))
      batchReport = ValueHasXmlBackfillReport(batch.size, outcome.rendered.size, outcome.failed.size, false)
      report      = state.report + batchReport
      _          <- ZIO.logInfo(s"valueHasXml backfill progress: ${describe(report)}")
    } yield state.copy(
      failed = state.failed ++ outcome.failed,
      mappings = outcome.mappings,
      report = report,
      previousBatch = batch.toSet,
    )

  private def renderAll(
    batch: Seq[IRI],
    grouped: Map[IRI, Either[String, StoredTextValue]],
    mappings: Map[IRI, MappingXMLtoStandoff],
  ): Task[BatchOutcome] =
    ZIO.foldLeft(batch)(BatchOutcome(mappings, Vector.empty, Vector.empty)) { (acc, iri) =>
      grouped(iri) match {
        case Left(reason) => ZIO.logWarning(s"valueHasXml backfill: $iri failed: $reason").as(acc.withFailure(iri))
        case Right(value) => renderValue(value, acc.mappings).foldCauseZIO(failValue(iri, acc), succeed(iri, acc))
      }
    }

  private def renderValue(
    value: StoredTextValue,
    mappings: Map[IRI, MappingXMLtoStandoff],
  ): Task[(Map[IRI, MappingXMLtoStandoff], String)] =
    for {
      mapping <- mappings.get(value.mappingIri) match {
                   case Some(cached) => ZIO.succeed(cached)
                   case None         => renderer.loadMapping(value.mappingIri)
                 }
      xml  <- renderer.render(value, mapping)
      text <- ZIO.fromOption(xml).orElseFail(NoXmlRendered())
    } yield (mappings.updated(value.mappingIri, mapping), text)

  private def succeed(
    iri: IRI,
    acc: BatchOutcome,
  )(result: (Map[IRI, MappingXMLtoStandoff], String)): UIO[BatchOutcome] =
    ZIO.succeed(acc.copy(mappings = result._1).withRendered(iri, result._2))

  private def failValue(iri: IRI, acc: BatchOutcome)(cause: Cause[Throwable]): Task[BatchOutcome] =
    if (cause.isInterrupted) ZIO.refailCause(cause)
    else
      ZIO.logWarning(s"valueHasXml backfill: $iri failed: ${cause.squash.getClass.getName}").as(acc.withFailure(iri))

  private def pace(started: Long): UIO[Unit] =
    Clock.nanoTime.flatMap { now =>
      val remaining = config.batchInterval.minus(java.time.Duration.ofNanos(now - started))
      ZIO.sleep(Duration.fromJava(remaining)).when(!remaining.isNegative && !remaining.isZero).unit
    }

  private def stage[A](name: String)(effect: Task[A]): Task[A] =
    SanitizedSpan.withSpan(tracing, s"value_has_xml_backfill.$name", ExitReasonKey)(_ => effect)

  private def setReportAttributes(span: Span, project: KnoraProject, report: ValueHasXmlBackfillReport): Unit = {
    val _ = span.setAttribute("value_has_xml_backfill.shortcode", project.shortcode.value)
    val _ = span.setAttribute("value_has_xml_backfill.found", report.found.toLong)
    val _ = span.setAttribute("value_has_xml_backfill.rendered", report.rendered.toLong)
    val _ = span.setAttribute("value_has_xml_backfill.failed", report.failed.toLong)
    val _ = span.setAttribute("value_has_xml_backfill.stopped_early", report.stoppedEarly)
  }
}

object ValueHasXmlBackfillService {

  private val ExitReasonKey = "value_has_xml_backfill.exit_reason"

  /** A value whose standoff renders no XML would remain a candidate forever, so it counts as failed. */
  private final case class NoXmlRendered() extends RuntimeException

  private final case class BatchOutcome(
    mappings: Map[IRI, MappingXMLtoStandoff],
    rendered: Vector[(IRI, String)],
    failed: Vector[IRI],
  ) {
    def withRendered(iri: IRI, xml: String): BatchOutcome = copy(rendered = rendered :+ (iri -> xml))
    def withFailure(iri: IRI): BatchOutcome               = copy(failed = failed :+ iri)
  }

  private def describe(report: ValueHasXmlBackfillReport): String =
    s"found=${report.found} rendered=${report.rendered} failed=${report.failed} stoppedEarly=${report.stoppedEarly}"

  val layer: URLayer[ValueHasXmlBackfillRepo & ValueHasXmlRenderer & AppConfig & Tracing, ValueHasXmlBackfillService] =
    ZLayer.fromZIO(
      for {
        repo     <- ZIO.service[ValueHasXmlBackfillRepo]
        renderer <- ZIO.service[ValueHasXmlRenderer]
        config   <- ZIO.service[AppConfig]
        tracing  <- ZIO.service[Tracing]
        running  <- Ref.make(Option.empty[ProjectIri])
      } yield ValueHasXmlBackfillService(repo, renderer, config, tracing, running),
    )
}
