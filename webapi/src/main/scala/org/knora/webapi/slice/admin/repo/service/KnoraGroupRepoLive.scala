/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.common.net.ParsedIRI
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.Chunk
import zio.IO
import zio.NonEmptyChunk
import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.messages.OntologyConstants.KnoraAdmin
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.BelongsToProject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.model.KnoraGroup.Conversions.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.repo.rdf.RdfConversions.projectIriConverter
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary.KnoraAdmin.*
import org.knora.webapi.slice.common.repo.rdf.Errors.ConversionError
import org.knora.webapi.slice.common.repo.rdf.Errors.RdfError
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class KnoraGroupRepoLive(
  private val triplestore: TriplestoreService,
  private val mapper: RdfEntityMapper[KnoraGroup],
  private val cache: EntityCache[GroupIri, KnoraGroup],
) extends CachingEntityRepo[KnoraGroup, GroupIri](triplestore, mapper, cache)
    with KnoraGroupRepo {
  override protected def resourceClass: ParsedIRI = ParsedIRI.create(KnoraAdmin.UserGroup)
  override protected def namedGraphIri: Iri       = Vocabulary.NamedGraphs.dataAdmin
  override protected def entityProperties: EntityProperties = EntityProperties(
    NonEmptyChunk(groupName, groupDescriptions, status, hasSelfJoinEnabled),
    Chunk(belongsToProject),
  )

  override def findById(id: GroupIri): Task[Option[KnoraGroup]] =
    super.findById(id).map(_.orElse(KnoraGroupRepo.builtIn.findOneBy(_.id == id)))

  override def findAll(): Task[Chunk[KnoraGroup]] = super.findAll().map(_ ++ KnoraGroupRepo.builtIn.all)

  override def save(group: KnoraGroup): Task[KnoraGroup] =
    ZIO
      .die(new IllegalArgumentException("Update not supported for built-in groups"))
      .when(KnoraGroupRepo.builtIn.findOneBy(_.id == group.id).isDefined) *>
      super.save(group)

  override def findByName(name: GroupName): Task[Option[KnoraGroup]] =
    findOneByTriplePattern(_.has(groupName, Rdf.literalOf(name.value)))
      .map(_.orElse(KnoraGroupRepo.builtIn.findOneBy(_.groupName == name)))

  override def findByProjectIri(projectIri: ProjectIri): Task[Chunk[KnoraGroup]] =
    findAllByTriplePattern(_.has(belongsToProject, Rdf.iri(projectIri.value)))
}

object KnoraGroupRepoLive {

  private val mapper = new RdfEntityMapper[KnoraGroup] {
    override def toEntity(resource: RdfResource): IO[RdfError, KnoraGroup] =
      for {
        id                 <- resource.iri.flatMap(it => ZIO.fromEither(GroupIri.from(it.value).left.map(ConversionError.apply)))
        groupName          <- resource.getStringLiteralOrFail[GroupName](KnoraAdmin.GroupName)
        groupDescriptions  <- resource.getLangStringLiteralsOrFail[StringLiteralV2](KnoraAdmin.GroupDescriptions)
        groupDescriptions  <- ZIO.fromEither(GroupDescriptions.from(groupDescriptions).left.map(ConversionError.apply))
        groupStatus        <- resource.getBooleanLiteralOrFail[GroupStatus](KnoraAdmin.StatusProp)
        belongsToProject   <- resource.getObjectIrisConvert[ProjectIri](KnoraAdmin.BelongsToProject).map(_.headOption)
        hasSelfJoinEnabled <- resource.getBooleanLiteralOrFail[GroupSelfJoin](KnoraAdmin.HasSelfJoinEnabled)
      } yield KnoraGroup(
        id,
        groupName,
        groupDescriptions,
        groupStatus,
        belongsToProject,
        hasSelfJoinEnabled,
      )

    override def toTriples(group: KnoraGroup): TriplePattern =
      Rdf
        .iri(group.id.value)
        .has(RDF.TYPE, Rdf.iri(KnoraAdmin.UserGroup))
        .andHas(groupName, Rdf.literalOf(group.groupName.value))
        .andHas(groupDescriptions, group.groupDescriptions.toRdfLiterals: _*)
        .andHas(status, Rdf.literalOf(group.status.value))
        .andHas(belongsToProject, group.belongsToProject.map(p => Rdf.iri(p.value)).toList: _*)
        .andHas(hasSelfJoinEnabled, Rdf.literalOf(group.hasSelfJoinEnabled.value))
  }

  val layer = (ZLayer.succeed(mapper) >+> EntityCache.layer[GroupIri, KnoraGroup]("knoraGroup")) >>> ZLayer
    .derive[KnoraGroupRepoLive]
}
