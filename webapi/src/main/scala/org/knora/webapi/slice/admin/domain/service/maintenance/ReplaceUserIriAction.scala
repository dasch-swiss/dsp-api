/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service.maintenance

import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.ConflictException
import dsp.errors.NotFoundException
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

final case class ReplaceUserIriAction(triplestoreService: TriplestoreService) extends QueryBuilderHelper {

  def execute(oldIri: UserIri, newIri: UserIri, requester: User): Task[Unit] =
    for {
      oldExists <- triplestoreService.query(existsInAdminGraph(oldIri))
      _         <-
        ZIO.when(!oldExists)(ZIO.fail(NotFoundException(s"User IRI ${oldIri.value} not found in admin named graph.")))
      newExists <- triplestoreService.query(existsInAdminGraph(newIri))
      _         <- ZIO.when(newExists)(
             ZIO.fail(ConflictException(s"User IRI ${newIri.value} already exists in admin named graph.")),
           )
      _ <- triplestoreService.query(replaceUpdate(oldIri, newIri))
      _ <- ZIO.logInfo(s"Replaced user IRI ${oldIri.value} with ${newIri.value} (requested by ${requester.id})")
    } yield ()

  private def existsInAdminGraph(iri: UserIri): Ask = {
    val adminGraphIri = Rdf.iri(AdminConstants.adminDataNamedGraph.value)
    val iriRdf        = Rdf.iri(iri.value)
    val p             = variable("p")
    val o             = variable("o")
    Ask(s"""ASK { ${GraphPatterns.tp(iriRdf, p, o).from(adminGraphIri).getQueryString} }""")
  }

  private def replaceUpdate(oldIri: UserIri, newIri: UserIri): Update = {
    val adminGraphIri = Rdf.iri(AdminConstants.adminDataNamedGraph.value)
    val oldIriRdf     = Rdf.iri(oldIri.value)
    val newIriRdf     = Rdf.iri(newIri.value)
    val p             = variable("p")
    val o             = variable("o")
    val s             = variable("s")
    val p2            = variable("p2")
    val g             = variable("g")

    val partA = Queries
      .MODIFY()
      .`with`(adminGraphIri)
      .delete(oldIriRdf.has(p, o))
      .insert(newIriRdf.has(p, o))
      .where(oldIriRdf.has(p, o))

    val partB = Queries
      .MODIFY()
      .delete(GraphPatterns.tp(s, p2, oldIriRdf))
      .from(g)
      .insert(GraphPatterns.tp(s, p2, newIriRdf))
      .into(g)
      .where(GraphPatterns.tp(s, p2, oldIriRdf).from(g))

    Update(partA.getQueryString + ";\n" + partB.getQueryString)
  }
}

object ReplaceUserIriAction {
  val layer: ZLayer[TriplestoreService, Nothing, ReplaceUserIriAction] = ZLayer.derive[ReplaceUserIriAction]
}
