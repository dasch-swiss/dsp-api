/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import zio.Chunk
import zio.IO
import zio.NonEmptyChunk
import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin
import org.knora.webapi.messages.store.triplestoremessages.LanguageTaggedStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.PlainStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraGroup.Conversions.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.repo.rdf.RdfConversions.projectIriConverter
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
  override protected def resourceClass: Iri                 = Iri.unsafeFrom(KnoraAdmin.UserGroup)
  override protected def namedGraphIri: Iri                 = Iri.unsafeFrom(adminDataNamedGraph.value)
  override protected def entityProperties: EntityProperties = EntityProperties(
    NonEmptyChunk(
      Iri.unsafeFrom(KnoraAdmin.GroupName),
      Iri.unsafeFrom(KnoraAdmin.GroupDescriptions),
      Iri.unsafeFrom(KnoraAdmin.StatusProp),
      Iri.unsafeFrom(KnoraAdmin.HasSelfJoinEnabled),
    ),
    Chunk(Iri.unsafeFrom(KnoraAdmin.BelongsToProject)),
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
    findOneByPattern(sparql"$s knora-admin:groupName ${Literal.string(name.value)} .")
      .map(_.orElse(KnoraGroupRepo.builtIn.findOneBy(_.groupName == name)))

  override def findByProjectIri(projectIri: ProjectIri): Task[Chunk[KnoraGroup]] =
    findAllByPattern(sparql"$s knora-admin:belongsToProject ${Iri.unsafeFrom(projectIri.value)} .")
}

object KnoraGroupRepoLive {

  private def toLiteral(literal: StringLiteralV2): Literal = literal match {
    case LanguageTaggedStringLiteralV2(value, lang) => Literal.langString(value, lang.value)
    case PlainStringLiteralV2(value)                => Literal.string(value)
  }

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

    override def toTriples(group: KnoraGroup): Fragment = {
      val id = Iri.unsafeFrom(group.id.value)
      sparql"""|$id a knora-admin:UserGroup ;
               |  knora-admin:groupName ${Literal.string(group.groupName.value)} .
               |${group.groupDescriptions.value
          .map(d => sparql"$id knora-admin:groupDescriptions ${toLiteral(d)} .")
          .joinLines}
               |$id knora-admin:status ${Literal.bool(group.status.value)} .
               |${group.belongsToProject.whenSome(p =>
          sparql"$id knora-admin:belongsToProject ${Iri.unsafeFrom(p.value)} .",
        )}
               |$id knora-admin:hasSelfJoinEnabled ${Literal.bool(group.hasSelfJoinEnabled.value)} ."""
    }
  }

  val layer = (ZLayer.succeed(mapper) >+> EntityCache.layer[GroupIri, KnoraGroup]("knoraGroup")) >>> ZLayer
    .derive[KnoraGroupRepoLive]
}
