/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import zio.*

import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.slice.admin.domain.model.KnoraProject

/**
 * In-memory [[ValueHasXmlBackfillRepo]]: every value shares [[ValueHasXmlBackfillRepoInMemory.sharedMapping]] and has
 * one standoff node. Writing XML removes the value from the candidates. `calls` records the order of
 * `"select"`, `"load"` and `"insert"`; `failSelectOn` and `failLoadOn` fail the n-th (1-based) select or load;
 * a `gate` blocks every select until it is completed.
 */
final class ValueHasXmlBackfillRepoInMemory private (
  candidates: Ref[Vector[IRI]],
  val calls: Ref[Vector[String]],
  selectCount: Ref[Int],
  failSelectOn: Option[Int],
  failLoadOn: Option[Int],
  gate: Option[Promise[Nothing, Unit]],
) extends ValueHasXmlBackfillRepo {

  override def selectCandidates(project: KnoraProject): Task[Seq[IRI]] =
    for {
      n <- selectCount.updateAndGet(_ + 1)
      _ <- calls.update(_ :+ "select")
      _ <- ZIO.foreachDiscard(gate)(_.await)
      _ <- ZIO.fail(new RuntimeException("select failed")).when(failSelectOn.contains(n))
      c <- candidates.get
    } yield c

  override def loadStandoff(project: KnoraProject, valueIris: Seq[IRI]): Task[Map[IRI, Seq[(IRI, String)]]] =
    for {
      loads <- calls.updateAndGet(_ :+ "load").map(_.count(_ == "load"))
      _     <- ZIO.fail(new RuntimeException("load failed")).when(failLoadOn.contains(loads))
    } yield valueIris.flatMap { iri =>
      Seq(
        iri -> Seq(
          KnoraBase.ValueHasString   -> "text",
          KnoraBase.ValueHasMapping  -> ValueHasXmlBackfillRepoInMemory.sharedMapping,
          KnoraBase.ValueHasStandoff -> s"$iri/node",
        ),
        s"$iri/node" -> Seq(KnoraBase.StandoffTagHasStartIndex -> "0"),
      )
    }.toMap

  override def insertXml(project: KnoraProject, values: Seq[(IRI, String)]): Task[Unit] =
    calls.update(_ :+ "insert") *>
      candidates.update(_.filterNot(values.map(_._1).toSet))
}

object ValueHasXmlBackfillRepoInMemory {

  val sharedMapping: IRI = "http://rdfh.ch/standoff/mappings/shared"

  def make(
    candidates: Seq[IRI],
    failSelectOn: Option[Int] = None,
    failLoadOn: Option[Int] = None,
    gate: Option[Promise[Nothing, Unit]] = None,
  ): UIO[ValueHasXmlBackfillRepoInMemory] =
    for {
      remaining <- Ref.make(candidates.toVector)
      calls     <- Ref.make(Vector.empty[String])
      selects   <- Ref.make(0)
    } yield new ValueHasXmlBackfillRepoInMemory(remaining, calls, selects, failSelectOn, failLoadOn, gate)
}
