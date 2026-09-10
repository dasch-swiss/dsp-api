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
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.PasswordHash
import org.knora.webapi.slice.admin.domain.model.SystemAdmin
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.repo.rdf.RdfConversions.*
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.common.repo.rdf.Errors.ConversionError
import org.knora.webapi.slice.common.repo.rdf.Errors.RdfError
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class KnoraUserRepoLive(
  private val triplestore: TriplestoreService,
  private val mapper: RdfEntityMapper[KnoraUser],
  private val entityCache: EntityCache[UserIri, KnoraUser],
) extends CachingEntityRepo[KnoraUser, UserIri](triplestore, mapper, entityCache)
    with KnoraUserRepo {

  override protected val resourceClass: Iri = Iri.unsafeFrom(KnoraAdmin.User)
  override protected val namedGraphIri: Iri = Iri.unsafeFrom(adminDataNamedGraph.value)

  override protected def entityProperties: EntityProperties = EntityProperties(
    NonEmptyChunk(
      Iri.unsafeFrom(KnoraAdmin.Username),
      Iri.unsafeFrom(KnoraAdmin.Email),
      Iri.unsafeFrom(KnoraAdmin.GivenName),
      Iri.unsafeFrom(KnoraAdmin.FamilyName),
      Iri.unsafeFrom(KnoraAdmin.StatusProp),
      Iri.unsafeFrom(KnoraAdmin.PreferredLanguage),
      Iri.unsafeFrom(KnoraAdmin.Password),
    ),
    Chunk(
      Iri.unsafeFrom(KnoraAdmin.IsInSystemAdminGroup),
      Iri.unsafeFrom(KnoraAdmin.IsInProject),
      Iri.unsafeFrom(KnoraAdmin.IsInGroup),
      Iri.unsafeFrom(KnoraAdmin.IsInProjectAdminGroup),
    ),
  )

  override def findAll(): Task[Chunk[KnoraUser]] = super.findAll().map(_ ++ KnoraUserRepo.builtIn.all)

  override def findById(id: UserIri): Task[Option[KnoraUser]] =
    super.findById(id).map(_.orElse(KnoraUserRepo.builtIn.findOneBy(_.id == id)))

  override def findByProjectAdminMembership(projectIri: ProjectIri): Task[Chunk[KnoraUser]] =
    findAllByPattern(sparql"$s knora-admin:isInProjectAdminGroup ${Iri.unsafeFrom(projectIri.value)} .")
      .map(_ ++ KnoraUserRepo.builtIn.findAllBy(_.isInProjectAdminGroup.contains(projectIri)))

  override def findByProjectMembership(projectIri: ProjectIri): Task[Chunk[KnoraUser]] =
    findAllByPattern(sparql"$s knora-admin:isInProject ${Iri.unsafeFrom(projectIri.value)} .")
      .map(_ ++ KnoraUserRepo.builtIn.findAllBy(_.isInProject.contains(projectIri)))

  override def findByGroupMembership(groupIri: GroupIri): Task[Chunk[KnoraUser]] =
    findAllByPattern(sparql"$s knora-admin:isInGroup ${Iri.unsafeFrom(groupIri.value)} .")
      .map(_ ++ KnoraUserRepo.builtIn.findAllBy(_.isInGroup.contains(groupIri)))

  override def findByEmail(mail: Email): Task[Option[KnoraUser]] =
    findOneByPattern(sparql"$s knora-admin:email ${Literal.string(mail.value)} .")
      .map(_.orElse(KnoraUserRepo.builtIn.findOneBy(_.email == mail)))

  override def findByUsername(name: Username): Task[Option[KnoraUser]] =
    findOneByPattern(sparql"$s knora-admin:username ${Literal.string(name.value)} .")
      .map(_.orElse(KnoraUserRepo.builtIn.findOneBy(_.username == name)))

  override def save(user: KnoraUser): Task[KnoraUser] =
    ZIO
      .die(new IllegalArgumentException("Update not supported for built-in users"))
      .when(user.id.isBuiltInUser) *>
      super.save(user)
}

object KnoraUserRepoLive {

  private val mapper = new RdfEntityMapper[KnoraUser] {
    override def toEntity(resource: RdfResource): IO[RdfError, KnoraUser] =
      for {
        userIri                   <- resource.iri.flatMap(it => ZIO.fromEither(UserIri.from(it.value).left.map(ConversionError.apply)))
        username                  <- resource.getStringLiteralOrFail[Username](KnoraAdmin.Username)
        email                     <- resource.getStringLiteralOrFail[Email](KnoraAdmin.Email)
        familyName                <- resource.getStringLiteralOrFail[FamilyName](KnoraAdmin.FamilyName)
        givenName                 <- resource.getStringLiteralOrFail[GivenName](KnoraAdmin.GivenName)
        passwordHash              <- resource.getStringLiteralOrFail[PasswordHash](KnoraAdmin.Password)
        preferredLanguage         <- resource.getStringLiteralOrFail[LanguageCode](KnoraAdmin.PreferredLanguage)
        status                    <- resource.getBooleanLiteralOrFail[UserStatus](KnoraAdmin.StatusProp)
        isInProjectIris           <- resource.getObjectIrisConvert[ProjectIri](KnoraAdmin.IsInProject)
        isInGroupIris             <- resource.getObjectIrisConvert[GroupIri](KnoraAdmin.IsInGroup)
        isInSystemAdminGroup      <- resource.getBooleanLiteralOrFail[SystemAdmin](KnoraAdmin.IsInSystemAdminGroup)
        isInProjectAdminGroupIris <- resource.getObjectIrisConvert[ProjectIri](KnoraAdmin.IsInProjectAdminGroup)
      } yield KnoraUser(
        userIri,
        username,
        email,
        familyName,
        givenName,
        passwordHash,
        preferredLanguage,
        status,
        isInProjectIris.sortBy(_.value),
        isInGroupIris,
        isInSystemAdminGroup,
        isInProjectAdminGroupIris,
      )

    override def toTriples(u: KnoraUser): Fragment = {
      val id = Iri.unsafeFrom(u.id.value)
      sparql"""|$id a knora-admin:User ;
               |  knora-admin:username ${Literal.string(u.username.value)} ;
               |  knora-admin:email ${Literal.string(u.email.value)} ;
               |  knora-admin:givenName ${Literal.string(u.givenName.value)} ;
               |  knora-admin:familyName ${Literal.string(u.familyName.value)} ;
               |  knora-admin:preferredLanguage ${Literal.string(u.preferredLanguage.value)} ;
               |  knora-admin:status ${Literal.bool(u.status.value)} ;
               |  knora-admin:password ${Literal.string(u.password.value)} ;
               |  knora-admin:isInSystemAdminGroup ${Literal.bool(u.isInSystemAdminGroup.value)} .
               |${u.isInProject.map(p => sparql"$id knora-admin:isInProject ${Iri.unsafeFrom(p.value)} .").joinLines}
               |${u.isInGroup.map(g => sparql"$id knora-admin:isInGroup ${Iri.unsafeFrom(g.value)} .").joinLines}
               |${u.isInProjectAdminGroup
          .map(p => sparql"$id knora-admin:isInProjectAdminGroup ${Iri.unsafeFrom(p.value)} .")
          .joinLines}"""
    }
  }

  val layer =
    (ZLayer.succeed(mapper) >+> EntityCache.layer[UserIri, KnoraUser]("knoraUser")) >>> ZLayer.derive[KnoraUserRepoLive]
}
