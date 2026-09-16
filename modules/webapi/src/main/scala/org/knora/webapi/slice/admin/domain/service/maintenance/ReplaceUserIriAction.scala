/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service.maintenance

import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.ConflictException
import dsp.errors.NotFoundException
import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

final case class ReplaceUserIriAction(triplestoreService: TriplestoreService) {

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

  private[maintenance] def existsInAdminGraph(iri: UserIri): Ask = {
    val adminGraph = Iri.unsafeFrom(AdminConstants.adminDataNamedGraph.value)
    val user       = Iri.unsafeFrom(iri.value)
    Ask(sparql"ASK { GRAPH $adminGraph { $user ?p ?o . } }".render)
  }

  private[maintenance] def replaceUpdate(oldIri: UserIri, newIri: UserIri): Update = {
    val adminGraph = Iri.unsafeFrom(AdminConstants.adminDataNamedGraph.value)
    val oldUser    = Iri.unsafeFrom(oldIri.value)
    val newUser    = Iri.unsafeFrom(newIri.value)
    Update(
      sparql"""|WITH $adminGraph
               |DELETE { $oldUser ?p ?o . }
               |INSERT { $newUser ?p ?o . }
               |WHERE { $oldUser ?p ?o . };
               |DELETE { GRAPH ?g { ?s ?p2 $oldUser . } }
               |INSERT { GRAPH ?g { ?s ?p2 $newUser . } }
               |WHERE { GRAPH ?g { ?s ?p2 $oldUser . } }""".render,
      SparqlTimeout.Maintenance,
    )
  }
}

object ReplaceUserIriAction {
  val layer: ZLayer[TriplestoreService, Nothing, ReplaceUserIriAction] = ZLayer.derive[ReplaceUserIriAction]
}
