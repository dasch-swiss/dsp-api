/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.common.net.ParsedIRI
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.Chunk
import zio.IO
import zio.NonEmptyChunk
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.valueobjects.LanguageCode
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
import org.knora.webapi.slice.admin.repo.rdf.RdfConversions._
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary.KnoraAdmin._
import org.knora.webapi.slice.common.repo.rdf.Errors.ConversionError
import org.knora.webapi.slice.common.repo.rdf.Errors.RdfError
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.store.cache.CacheService
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class KnoraUserRepoLive(
  private val triplestore: TriplestoreService,
  private val cacheService: CacheService,
  private val mapper: RdfEntityMapper[KnoraUser],
) extends AbstractEntityRepo[KnoraUser, UserIri](triplestore, mapper)
    with KnoraUserRepo {

  override protected val resourceClass: ParsedIRI = ParsedIRI.create(KnoraAdmin.User)
  override protected val namedGraphIri: Iri       = Rdf.iri(adminDataNamedGraph.value)

  override protected def entityProperties: EntityProperties = EntityProperties(
    NonEmptyChunk(username, email, givenName, familyName, status, preferredLanguage, password),
    Chunk(isInSystemAdminGroup, isInProject, isInGroup, isInProjectAdminGroup),
  )

  override def findAll(): Task[Chunk[KnoraUser]] = super.findAll().map(_ ++ KnoraUserRepo.builtIn.all)

  override def findById(id: UserIri): Task[Option[KnoraUser]] =
    super.findById(id).map(_.orElse(KnoraUserRepo.builtIn.findOneBy(_.id == id)))

  override def findByProjectAdminMembership(projectIri: ProjectIri): Task[Chunk[KnoraUser]] =
    findAllByTriplePattern(_.has(isInProjectAdminGroup, Rdf.iri(projectIri.value)))
      .map(_ ++ KnoraUserRepo.builtIn.findAllBy(_.isInProjectAdminGroup.contains(projectIri)))

  override def findByProjectMembership(projectIri: ProjectIri): Task[Chunk[KnoraUser]] =
    findAllByTriplePattern(_.has(isInProject, Rdf.iri(projectIri.value)))
      .map(_ ++ KnoraUserRepo.builtIn.findAllBy(_.isInProject.contains(projectIri)))

  override def findByEmail(mail: Email): Task[Option[KnoraUser]] =
    findOneByTriplePattern(_.has(email, Rdf.literalOf(mail.value)))
      .map(_.orElse(KnoraUserRepo.builtIn.findOneBy(_.email == mail)))

  override def findByUsername(name: Username): Task[Option[KnoraUser]] =
    findOneByTriplePattern(_.has(username, Rdf.literalOf(name.value)))
      .map(_.orElse(KnoraUserRepo.builtIn.findOneBy(_.username == name)))

  override def save(user: KnoraUser): Task[KnoraUser] =
    ZIO
      .die(new IllegalArgumentException("Update not supported for built-in users"))
      .when(KnoraUserRepo.builtIn.findOneBy(_.id == user.id).isDefined) *>
      cacheService.invalidateUser(user.id) *> super.save(user)
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
        isInProjectIris,
        isInGroupIris,
        isInSystemAdminGroup,
        isInProjectAdminGroupIris,
      )

    override def toTriples(u: KnoraUser): TriplePattern =
      Rdf
        .iri(u.id.value)
        .isA(User)
        .andHas(username, Rdf.literalOf(u.username.value))
        .andHas(email, Rdf.literalOf(u.email.value))
        .andHas(givenName, Rdf.literalOf(u.givenName.value))
        .andHas(familyName, Rdf.literalOf(u.familyName.value))
        .andHas(preferredLanguage, Rdf.literalOf(u.preferredLanguage.value))
        .andHas(status, Rdf.literalOf(u.status.value))
        .andHas(password, Rdf.literalOf(u.password.value))
        .andHas(isInSystemAdminGroup, Rdf.literalOf(u.isInSystemAdminGroup.value))
        .andHas(isInProject, u.isInProject.map(p => Rdf.iri(p.value)).toList: _*)
        .andHas(isInGroup, u.isInGroup.map(p => Rdf.iri(p.value)).toList: _*)
        .andHas(isInProjectAdminGroup, u.isInProjectAdminGroup.map(p => Rdf.iri(p.value)).toList: _*)
  }

  val layer = ZLayer.succeed(mapper) >>> ZLayer.derive[KnoraUserRepoLive]
}
