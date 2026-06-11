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

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraAdmin as KA
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

final case class ReplaceUserIriInProjectAction(
  triplestoreService: TriplestoreService,
  knoraProjectService: KnoraProjectService,
) extends QueryBuilderHelper {

  def execute(shortcode: Shortcode, oldIri: UserIri, newIri: UserIri, requester: User): Task[Unit] =
    for {
      project <- knoraProjectService
                   .findByShortcode(shortcode)
                   .someOrFail(NotFoundException(s"project ${shortcode.value} not found."))
      oldExists <- triplestoreService.query(existsInAdminGraph(oldIri))
      _         <- ZIO.when(!oldExists)(
             ZIO.fail(NotFoundException(s"user IRI ${oldIri.value} not found in admin named graph.")),
           )
      newExists <- triplestoreService.query(existsInAdminGraph(newIri))
      _         <- ZIO.when(!newExists)(
             ZIO.fail(NotFoundException(s"user IRI ${newIri.value} not found in admin named graph.")),
           )
      isMember <- triplestoreService.query(isMemberOfProject(newIri, project))
      _        <- ZIO.when(!isMember)(
             ZIO.fail(BadRequestException(s"user ${newIri.value} is not a member of project ${shortcode.value}.")),
           )
      hasRefs <- triplestoreService.query(hasRefsInProjectGraph(oldIri, project))
      _       <- ZIO.when(!hasRefs)(
             ZIO.fail(
               NotFoundException(
                 s"user ${oldIri.value} has no references in the data graph of project ${shortcode.value}.",
               ),
             ),
           )
      _ <- triplestoreService.query(replaceInProjectGraph(oldIri, newIri, project))
      _ <-
        ZIO.logInfo(
          s"Re-attributed references from ${oldIri.value} to ${newIri.value} in project ${shortcode.value} (requested by ${requester.id})",
        )
    } yield ()

  private def existsInAdminGraph(iri: UserIri): Ask = {
    val adminGraphIri = Rdf.iri(AdminConstants.adminDataNamedGraph.value)
    val iriRdf        = Rdf.iri(iri.value)
    val p             = variable("p")
    val o             = variable("o")
    Ask(s"""ASK { ${GraphPatterns.tp(iriRdf, p, o).from(adminGraphIri).getQueryString} }""")
  }

  private def isMemberOfProject(iri: UserIri, project: KnoraProject): Ask = {
    val adminGraphIri = Rdf.iri(AdminConstants.adminDataNamedGraph.value)
    val iriRdf        = Rdf.iri(iri.value)
    val projectIriRdf = Rdf.iri(project.id.value)
    Ask(s"""ASK { ${GraphPatterns.tp(iriRdf, KA.isInProject, projectIriRdf).from(adminGraphIri).getQueryString} }""")
  }

  private def hasRefsInProjectGraph(iri: UserIri, project: KnoraProject): Ask = {
    val projectGraphIri = graphIri(project)
    val iriRdf          = Rdf.iri(iri.value)
    val s               = variable("s")
    val p               = variable("p")
    Ask(s"""ASK { ${GraphPatterns.tp(s, p, iriRdf).from(projectGraphIri).getQueryString} }""")
  }

  private def replaceInProjectGraph(oldIri: UserIri, newIri: UserIri, project: KnoraProject): Update = {
    val projectGraphIri = graphIri(project)
    val oldIriRdf       = Rdf.iri(oldIri.value)
    val newIriRdf       = Rdf.iri(newIri.value)
    val s               = variable("s")
    val p               = variable("p")
    Update(
      Queries
        .MODIFY()
        .`with`(projectGraphIri)
        .delete(GraphPatterns.tp(s, p, oldIriRdf))
        .insert(GraphPatterns.tp(s, p, newIriRdf))
        .where(GraphPatterns.tp(s, p, oldIriRdf)),
    )
  }
}

object ReplaceUserIriInProjectAction {
  val layer: ZLayer[TriplestoreService & KnoraProjectService, Nothing, ReplaceUserIriInProjectAction] =
    ZLayer.derive[ReplaceUserIriInProjectAction]
}
