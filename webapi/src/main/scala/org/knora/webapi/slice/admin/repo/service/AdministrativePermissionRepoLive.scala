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
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.KnoraAdminPrefix
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.slice.admin.AdminConstants.permissionsDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.AdministrativePermission
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionPart
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionRepo
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.Permission.Administrative
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.repo.rdf.RdfConversions.*
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.slice.common.repo.rdf.Errors.ConversionError
import org.knora.webapi.slice.common.repo.rdf.Errors.RdfError
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.errors.TriplestoreResponseException

final case class AdministrativePermissionRepoLive(
  triplestore: TriplestoreService,
  mapper: RdfEntityMapper[AdministrativePermission],
) extends AbstractEntityRepo[AdministrativePermission, PermissionIri](triplestore, mapper)
    with AdministrativePermissionRepo {

  override protected val resourceClass: ParsedIRI = ParsedIRI.create(KnoraAdmin.AdministrativePermission)
  override protected val namedGraphIri: Iri       = Rdf.iri(permissionsDataNamedGraph.value)

  override protected def entityProperties: EntityProperties =
    EntityProperties(
      NonEmptyChunk(
        Vocabulary.KnoraBase.hasPermissions,
        Vocabulary.KnoraAdmin.forGroup,
        Vocabulary.KnoraAdmin.forProject,
      ),
    )

  override def findByGroupAndProject(
    groupIri: GroupIri,
    projectIri: ProjectIri,
  ): Task[Option[AdministrativePermission]] =
    findOneByTriplePattern(
      _.has(Vocabulary.KnoraAdmin.forGroup, Rdf.iri(groupIri.value))
        .andHas(Vocabulary.KnoraAdmin.forProject, Rdf.iri(projectIri.value)),
    )

  override def findByProject(projectIri: ProjectIri): Task[Chunk[AdministrativePermission]] =
    findAllByTriplePattern(_.has(Vocabulary.KnoraAdmin.forProject, Rdf.iri(projectIri.value)))
}

object AdministrativePermissionRepoLive {
  private val mapper = new RdfEntityMapper[AdministrativePermission] {

    private val permissionsDelimiter = '|'

    override def toEntity(resource: RdfResource): IO[RdfError, AdministrativePermission] =
      for {
        id <- resource.iri.flatMap { iri =>
                ZIO.fromEither(PermissionIri.from(iri.value).left.map(ConversionError.apply))
              }
        forGroup    <- resource.getObjectIrisConvert[GroupIri](KnoraAdmin.ForGroup).map(_.head)
        forProject  <- resource.getObjectIrisConvert[ProjectIri](KnoraAdmin.ForProject).map(_.head)
        permissions <- parsePermissions(resource)
      } yield AdministrativePermission(id, forGroup, forProject, permissions)

    private def parsePermissions(resource: RdfResource) = for {
      permissionStr     <- resource.getStringLiteralOrFail[String](KnoraBase.HasPermissions)(Right(_))
      parsedPermissions <- parsePermission(permissionStr)
    } yield parsedPermissions

    private def parsePermission(permission: String) =
      ZIO
        .foreach(Chunk.fromIterable(permission.split(permissionsDelimiter).map(_.trim))) { token =>
          token.split(' ') match {

            case Array(simplePermission) =>
              ZIO.succeed(
                Permission.Administrative
                  .fromToken(simplePermission)
                  .flatMap(AdministrativePermissionPart.Simple.from(_).toOption),
              )

            case Array(restricted, irisStr) =>
              val iris = Chunk.fromIterable(irisStr.split(','))
              Permission.Administrative.fromToken(restricted) match {
                case Some(_: Administrative.ProjectResourceCreateRestricted.type) =>
                  ZIO.some(AdministrativePermissionPart.ResourceCreateRestricted(iris.map(InternalIri.apply)))
                case Some(_: Administrative.ProjectAdminGroupRestricted.type) =>
                  val groups = iris.map(withPrefixExpansion(GroupIri.from)).map(_.left.map(str => ConversionError(str)))
                  ZIO
                    .foreach(groups)(ZIO.fromEither(_))
                    .map(AdministrativePermissionPart.ProjectAdminGroupRestricted.apply)
                    .map(Some(_))
                case _ => ZIO.none
              }

            case _ => ZIO.die(TriplestoreResponseException(s"Invalid permission token '$token' in: $permission"))
          }
        }
        .map(_.flatten)

    override def toTriples(entity: AdministrativePermission): TriplePattern = {
      val id = Rdf.iri(entity.id.value)
      id.isA(Vocabulary.KnoraAdmin.AdministrativePermission)
        .andHas(Vocabulary.KnoraAdmin.forGroup, Rdf.iri(entity.forGroup.value))
        .andHas(Vocabulary.KnoraAdmin.forProject, Rdf.iri(entity.forProject.value))
        .andHas(Vocabulary.KnoraBase.hasPermissions, toStringLiteral(entity.permissions))
    }
    private def toStringLiteral(permissions: Chunk[AdministrativePermissionPart]): String = {
      def useKnoraAdminPrefix(str: String) = str.replace(KnoraAdminPrefixExpansion, KnoraAdminPrefix)
      permissions.map {
        case AdministrativePermissionPart.Simple(permission) => permission.token
        case AdministrativePermissionPart.ResourceCreateRestricted(iris) =>
          s"${Permission.Administrative.ProjectResourceCreateRestricted.token} ${iris.map(_.value).mkString(",")}"
        case AdministrativePermissionPart.ProjectAdminGroupRestricted(groups) =>
          s"${Permission.Administrative.ProjectAdminGroupRestricted.token} ${groups.map(_.value).map(useKnoraAdminPrefix).mkString(",")}"
      }.mkString(permissionsDelimiter.toString)
    }
  }

  val layer = ZLayer.succeed(mapper) >>> ZLayer.derive[AdministrativePermissionRepoLive]
}
