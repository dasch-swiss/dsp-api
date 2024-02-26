/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.prefix
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.stream.ZStream

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUserGroup
import org.knora.webapi.slice.admin.domain.service.KnoraUserGroupRepo
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.errors.TriplestoreResponseException

final case class KnoraUserGroupRepoLive(triplestore: TriplestoreService) extends KnoraUserGroupRepo {

  override def findById(id: GroupIri): Task[Option[KnoraUserGroup]] = for {
    model    <- triplestore.queryRdfModel(UserGroupQueries.findById(id))
    resource <- model.getResource(id.value)
    user     <- ZIO.foreach(resource)(toGroup)
  } yield user

  override def findAll(): Task[List[KnoraUserGroup]] = for {
    model <- triplestore.queryRdfModel(UserGroupQueries.findAll)
    resources <-
      model.getResourcesRdfType(OntologyConstants.KnoraAdmin.UserGroup).option.map(_.getOrElse(Iterator.empty))
    groups <- ZStream.fromIterator(resources).mapZIO(toGroup).runCollect
  } yield groups.toList

  private def toGroup(resource: RdfResource): Task[KnoraUserGroup] = for {
    id <-
      resource.iri.flatMap(it => ZIO.fromEither(GroupIri.from(it.value))).mapError(TriplestoreResponseException.apply)
    belongsToProject <- resource
                          .getObjectIriOrFail(OntologyConstants.KnoraAdmin.BelongsToProject)
                          .flatMap(it => ZIO.fromEither(ProjectIri.from(it.value)))
                          .mapError(it => TriplestoreResponseException(it.toString))
  } yield KnoraUserGroup(id, belongsToProject)
}

object KnoraUserGroupRepoLive {
  val layer = ZLayer.derive[KnoraUserGroupRepoLive]
}

object UserGroupQueries {

  def findAll: Construct = {
    val (s, p, o) = (variable("s"), variable("p"), variable("o"))
    val query = Queries
      .CONSTRUCT(tp(s, p, o))
      .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS))
      .where(
        s
          .has(RDF.TYPE, Vocabulary.KnoraAdmin.UserGroup)
          .and(s.has(p, o))
          .from(Vocabulary.NamedGraphs.knoraAdminIri)
      )
    println(query.getQueryString)
    Construct(query.getQueryString)
  }

  def findById(id: GroupIri): Construct = {
    val s      = Rdf.iri(id.value)
    val (p, o) = (variable("p"), variable("o"))
    val query: ConstructQuery = Queries
      .CONSTRUCT(tp(s, p, o))
      .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS))
      .where(
        s
          .has(RDF.TYPE, Vocabulary.KnoraAdmin.UserGroup)
          .and(tp(s, p, o))
          .from(Vocabulary.NamedGraphs.knoraAdminIri)
      )
    println(query.getQueryString)
    Construct(query)
  }
}
