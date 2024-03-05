/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.*
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.prefix
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.stream.ZStream

import dsp.valueobjects.V2
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUserGroup.Conversions.*
import org.knora.webapi.slice.admin.domain.model._
import org.knora.webapi.slice.admin.domain.service.KnoraUserGroupRepo
import org.knora.webapi.slice.admin.repo.rdf.RdfConversions.projectIriConverter
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.errors.TriplestoreResponseException

final case class KnoraUserGroupRepoLive(triplestore: TriplestoreService) extends KnoraUserGroupRepo {
  override def findById(id: GroupIri): Task[Option[KnoraUserGroup]] = for {
    model     <- triplestore.queryRdfModel(KnoraUserGroupQueries.findById(id))
    resource  <- model.getResource(id.value)
    userGroup <- ZIO.foreach(resource)(toGroup)
  } yield userGroup

  override def findAll(): Task[List[KnoraUserGroup]] = for {
    model <- triplestore.queryRdfModel(KnoraUserGroupQueries.findAll)
    resources <-
      model.getResourcesRdfType(KnoraAdmin.UserGroup).option.map(_.getOrElse(Iterator.empty))
    groups <- ZStream.fromIterator(resources).mapZIO(toGroup).runCollect
  } yield groups.toList

  def save(userGroup: KnoraUserGroup): Task[KnoraUserGroup] =
    triplestore.query(KnoraUserGroupQueries.save(userGroup)).as(userGroup)

  private def toGroup(resource: RdfResource): Task[KnoraUserGroup] = {
    for {
      id                 <- resource.iri.flatMap(it => ZIO.fromEither(GroupIri.from(it.value)))
      groupName          <- resource.getStringLiteralOrFail[GroupName](KnoraAdmin.GroupName)
      groupDescriptions  <- resource.getLangStringLiteralsOrFail[V2.StringLiteralV2](KnoraAdmin.GroupDescriptions)
      groupDescriptions  <- ZIO.fromEither(GroupDescriptions.from(groupDescriptions))
      groupStatus        <- resource.getBooleanLiteralOrFail[GroupStatus](KnoraAdmin.StatusProp)
      belongsToProject   <- resource.getObjectIrisConvert[ProjectIri](KnoraAdmin.BelongsToProject).map(_.headOption)
      hasSelfJoinEnabled <- resource.getBooleanLiteralOrFail[GroupSelfJoin](KnoraAdmin.HasSelfJoinEnabled)
    } yield KnoraUserGroup(
      id,
      groupName,
      groupDescriptions,
      groupStatus,
      belongsToProject,
      hasSelfJoinEnabled,
    )
  }.mapError(it => TriplestoreResponseException(it.toString))
}

object KnoraUserGroupRepoLive {
  val layer = ZLayer.derive[KnoraUserGroupRepoLive]
}

private object KnoraUserGroupQueries {
  def findAll: Construct = {
    val (s, p, o) = (variable("s"), variable("p"), variable("o"))
    val query = Queries
      .CONSTRUCT(tp(s, p, o))
      .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS))
      .where(
        s
          .has(RDF.TYPE, Vocabulary.KnoraAdmin.UserGroup)
          .and(s.has(p, o))
          .from(Vocabulary.NamedGraphs.knoraAdminIri),
      )
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
          .from(Vocabulary.NamedGraphs.knoraAdminIri),
      )
    Construct(query)
  }

  private def deleteWhere(
    id: Iri,
    rdfType: Iri,
    query: ModifyQuery,
    iris: List[Iri],
  ): ModifyQuery =
    query
      .delete(iris.zipWithIndex.foldLeft(id.has(RDF.TYPE, rdfType)) { case (p, (iri, index)) =>
        p.andHas(iri, variable(s"n${index}"))
      })
      .where(iris.zipWithIndex.foldLeft(id.has(RDF.TYPE, rdfType).optional()) { case (p, (iri, index)) =>
        p.and(id.has(iri, variable(s"n${index}")).optional())
      })

  def save(group: KnoraUserGroup): Update = {
    val query: ModifyQuery =
      Queries
        .MODIFY()
        .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS), prefix(XSD.NS))
        .`with`(Rdf.iri(adminDataNamedGraph.value))
        .insert(toTriples(group))

    Update(deleteWhere(Rdf.iri(group.id.value), Vocabulary.KnoraAdmin.UserGroup, query, deletionFields))
  }

  private val deletionFields: List[Iri] = List(
    Vocabulary.KnoraAdmin.groupName,
    Vocabulary.KnoraAdmin.groupDescriptions,
    Vocabulary.KnoraAdmin.status,
    Vocabulary.KnoraAdmin.belongsToProject,
    Vocabulary.KnoraAdmin.hasSelfJoinEnabled,
  )

  private def toTriples(group: KnoraUserGroup) = {
    import Vocabulary.KnoraAdmin.*
    Rdf
      .iri(group.id.value)
      .has(RDF.TYPE, UserGroup)
      .andHas(groupName, Rdf.literalOf(group.groupName.value))
      .andHas(groupDescriptions, group.groupDescriptions.toRdfLiterals: _*)
      .andHas(status, Rdf.literalOf(group.status.value))
      .andHas(belongsToProject, group.belongsToProject.map(p => Rdf.iri(p.value)).toList: _*)
      .andHas(hasSelfJoinEnabled, Rdf.literalOf(group.hasSelfJoinEnabled.value))
  }
}
