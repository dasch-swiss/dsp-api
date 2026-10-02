/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import zio.*

import dsp.errors.InconsistentRepositoryDataException
import org.knora.sparqlbuilder.Iri
import org.knora.webapi.IRI
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.resources.repo.ValueHasXmlBackfillQuery
import org.knora.webapi.store.triplestore.api.TriplestoreService

/** The triplestore access of the `valueHasXml` backfill. */
trait ValueHasXmlBackfillRepo {

  /** Returns every text value of the project that still lacks `valueHasXml`. */
  def selectCandidates(project: KnoraProject): Task[Seq[IRI]]

  /** Returns the CONSTRUCT statements (subject to predicate/object pairs) describing the given text values. */
  def loadStandoff(project: KnoraProject, valueIris: Seq[IRI]): Task[Map[IRI, Seq[(IRI, String)]]]

  /** Writes `valueHasXml` for the given values. */
  def insertXml(project: KnoraProject, values: Seq[(IRI, String)]): Task[Unit]
}

final case class ValueHasXmlBackfillRepoLive(triplestore: TriplestoreService) extends ValueHasXmlBackfillRepo {

  override def selectCandidates(project: KnoraProject): Task[Seq[IRI]] =
    for {
      graph  <- graphOf(project)
      result <- triplestore.query(ValueHasXmlBackfillQuery.selectCandidates(graph))
    } yield result.getCol("v")

  override def loadStandoff(project: KnoraProject, valueIris: Seq[IRI]): Task[Map[IRI, Seq[(IRI, String)]]] =
    for {
      graph    <- graphOf(project)
      values   <- ZIO.foreach(valueIris)(toIri)
      response <- triplestore.query(ValueHasXmlBackfillQuery.constructStandoff(graph, values))
    } yield response.statements

  override def insertXml(project: KnoraProject, values: Seq[(IRI, String)]): Task[Unit] =
    for {
      graph <- graphOf(project)
      rows  <- ZIO.foreach(values) { case (valueIri, xml) => toIri(valueIri).map(_ -> xml) }
      _     <- triplestore.query(ValueHasXmlBackfillQuery.insertXml(graph, rows))
    } yield ()

  private def graphOf(project: KnoraProject): Task[Iri] =
    toIri(ProjectService.projectDataNamedGraphV2(project).value)

  private def toIri(value: String): Task[Iri] =
    ZIO.fromEither(Iri.from(value)).mapError(msg => InconsistentRepositoryDataException(msg))
}

object ValueHasXmlBackfillRepoLive {
  val layer = ZLayer.derive[ValueHasXmlBackfillRepoLive]
}
