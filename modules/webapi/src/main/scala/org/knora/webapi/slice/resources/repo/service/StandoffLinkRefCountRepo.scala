/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import zio.*

import dsp.errors.InconsistentRepositoryDataException
import org.knora.sparqlbuilder.Iri
import org.knora.webapi.IRI
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.resources.repo.StandoffLinkRefCountQuery
import org.knora.webapi.store.triplestore.api.TriplestoreService

/**
 * A (source, target) pair whose stored standoff link state contradicts the standoff link tags of the source.
 *
 * @param linkValue     the current link value, if any
 * @param storedCount   the `valueHasRefCount` of the current link value, if any
 * @param expectedCount the number of current text values of the source with a link tag to the target
 * @param directLink    whether `hasStandoffLinkTo` exists between source and target
 */
final case class StandoffLinkRefCountMismatch(
  source: IRI,
  target: IRI,
  linkValue: Option[IRI],
  storedCount: Option[Int],
  expectedCount: Int,
  directLink: Boolean,
)

/** The triplestore access of the standoff link reference count invariant. */
trait StandoffLinkRefCountRepo {

  /** Returns every standoff link of the project whose stored state contradicts the standoff link tags. */
  def selectMismatches(project: KnoraProject): Task[Seq[StandoffLinkRefCountMismatch]]
}

final case class StandoffLinkRefCountRepoLive(triplestore: TriplestoreService) extends StandoffLinkRefCountRepo {

  override def selectMismatches(project: KnoraProject): Task[Seq[StandoffLinkRefCountMismatch]] =
    for {
      graph <- ZIO
                 .fromEither(Iri.from(ProjectService.projectDataNamedGraphV2(project).value))
                 .mapError(msg => InconsistentRepositoryDataException(msg))
      result <- triplestore.query(StandoffLinkRefCountQuery.selectMismatches(graph))
      rows   <- ZIO.attempt(result.map(StandoffLinkRefCountRepoLive.toMismatch))
    } yield rows
}

object StandoffLinkRefCountRepoLive {
  val layer = ZLayer.derive[StandoffLinkRefCountRepoLive]

  private def toInt(value: String): Either[String, Int] =
    value.toIntOption.toRight(s"'$value' is not an integer")

  private def toBoolean(value: String): Either[String, Boolean] =
    value.toBooleanOption.toRight(s"'$value' is not a boolean")

  private def toIri(value: String): Either[String, IRI] = Iri.from(value).map(_ => value)

  private[service] def toMismatch(row: VariableResultsRow): StandoffLinkRefCountMismatch =
    StandoffLinkRefCountMismatch(
      source = row.getRequired("source", toIri),
      target = row.getRequired("target", toIri),
      linkValue = row.get("linkValue", toIri),
      storedCount = row.get("storedCount", toInt),
      expectedCount = row.getRequired("expectedCount", toInt),
      directLink = row.getRequired("directLink", toBoolean),
    )
}
