/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import zio.*

import org.knora.webapi.IRI
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate

/**
 * In-memory [[StandoffLinkRefCountRepo]]: returns the mismatches configured per project shortcode, fails for the
 * projects in `failing`, dies (a defect) for the projects in `dying` and, when a `gate` is given, waits for it before answering.
 * It records every repair it is asked to apply in `applied` and answers `applyResult` for each, except that the
 * repair of a source in `applyFailing` fails and the repair of a source in `applyDying` dies.
 */
final case class StandoffLinkRefCountRepoInMemory private (
  mismatches: Map[String, Seq[StandoffLinkRefCountMismatch]],
  failing: Set[String],
  dying: Set[String],
  gate: Option[Promise[Nothing, Unit]],
  applyResult: Boolean,
  applyFailing: Set[IRI],
  applyDying: Set[IRI],
  applied: Ref[Chunk[(IRI, SparqlTemplateLinkUpdate)]],
) extends StandoffLinkRefCountRepo {

  override def selectMismatches(project: KnoraProject): Task[Seq[StandoffLinkRefCountMismatch]] = {
    val shortcode = project.shortcode.value
    ZIO.foreachDiscard(gate)(_.await) *>
      (if (dying(shortcode)) ZIO.die(new IllegalStateException("secret triplestore detail"))
       else if (failing(shortcode)) ZIO.fail(new RuntimeException("secret triplestore detail"))
       else ZIO.succeed(mismatches.getOrElse(shortcode, Seq.empty)))
  }

  override def applyRepair(project: KnoraProject, source: IRI, update: SparqlTemplateLinkUpdate): Task[Boolean] =
    applied.update(_ :+ (source -> update)) *>
      (if (applyDying(source)) ZIO.die(new IllegalStateException("secret triplestore detail"))
       else if (applyFailing(source)) ZIO.fail(new RuntimeException("secret triplestore detail"))
       else ZIO.succeed(applyResult))
}

object StandoffLinkRefCountRepoInMemory {
  def make(
    mismatches: Map[String, Seq[StandoffLinkRefCountMismatch]] = Map.empty,
    failing: Set[String] = Set.empty,
    dying: Set[String] = Set.empty,
    gate: Option[Promise[Nothing, Unit]] = None,
    applyResult: Boolean = true,
    applyFailing: Set[IRI] = Set.empty,
    applyDying: Set[IRI] = Set.empty,
  ): UIO[StandoffLinkRefCountRepoInMemory] =
    Ref
      .make(Chunk.empty[(IRI, SparqlTemplateLinkUpdate)])
      .map(StandoffLinkRefCountRepoInMemory(mismatches, failing, dying, gate, applyResult, applyFailing, applyDying, _))
}
