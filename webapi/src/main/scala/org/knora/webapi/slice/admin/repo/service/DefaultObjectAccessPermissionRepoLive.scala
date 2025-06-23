/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.common.net.ParsedIRI
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
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
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.DefaultObjectAccessPermissionPart
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.ForWhat
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.ForWhat.*
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermissionRepo
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.repo.rdf.RdfConversions.*
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.repo.rdf.Errors.ConversionError
import org.knora.webapi.slice.common.repo.rdf.Errors.RdfError
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.slice.common.repo.rdf.Vocabulary
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class DefaultObjectAccessPermissionRepoLive(
  triplestore: TriplestoreService,
  mapper: RdfEntityMapper[DefaultObjectAccessPermission],
) extends AbstractEntityRepo[DefaultObjectAccessPermission, PermissionIri](triplestore, mapper)
    with DefaultObjectAccessPermissionRepo {

  override protected val resourceClass: ParsedIRI = ParsedIRI.create(KnoraAdmin.DefaultObjectAccessPermission)
  override protected val namedGraphIri: Iri       = Rdf.iri(permissionsDataNamedGraph.value)

  override protected def entityProperties: EntityProperties =
    EntityProperties(
      NonEmptyChunk(Vocabulary.KnoraAdmin.forProject, Vocabulary.KnoraBase.hasPermissions),
      Chunk(Vocabulary.KnoraAdmin.forGroup, Vocabulary.KnoraAdmin.forProperty, Vocabulary.KnoraAdmin.forResourceClass),
    )

  override def findByProject(projectIri: ProjectIri): Task[Chunk[DefaultObjectAccessPermission]] =
    findAllByPattern(_.has(Vocabulary.KnoraAdmin.forProject, Rdf.iri(projectIri.value)))

  def findByProjectAndForWhat(projectIri: ProjectIri, forWhat: ForWhat): Task[Option[DefaultObjectAccessPermission]] =
    findOneByPattern(p =>
      val pattern = p.has(Vocabulary.KnoraAdmin.forProject, Rdf.iri(projectIri.value))
      forWhat match {
        case Group(g) => pattern.andHas(Vocabulary.KnoraAdmin.forGroup, Rdf.iri(g.value))
        case ResourceClass(rc) =>
          pattern
            .andHas(Vocabulary.KnoraAdmin.forResourceClass, Rdf.iri(rc.value))
            .filterNotExists(p.has(Vocabulary.KnoraAdmin.forProperty, variable("prop")))

        case Property(prop) =>
          pattern
            .andHas(Vocabulary.KnoraAdmin.forProperty, Rdf.iri(prop.value))
            .filterNotExists(p.has(Vocabulary.KnoraAdmin.forResourceClass, variable("rc")))

        case ResourceClassAndProperty(rc, prop) =>
          pattern
            .andHas(Vocabulary.KnoraAdmin.forResourceClass, Rdf.iri(rc.value))
            .andHas(Vocabulary.KnoraAdmin.forProperty, Rdf.iri(prop.value))
      },
    )
}

object DefaultObjectAccessPermissionRepoLive {
  private val permissionsDelimiter = '|'
  private val mapper = new RdfEntityMapper[DefaultObjectAccessPermission] {

    override def toEntity(resource: RdfResource): IO[RdfError, DefaultObjectAccessPermission] = for {
      id <- resource.iri.flatMap { iri =>
              ZIO.fromEither(PermissionIri.from(iri.value).left.map(ConversionError.apply))
            }
      forProject          <- resource.getObjectIrisConvert[ProjectIri](KnoraAdmin.ForProject).map(_.head)
      forGroup            <- resource.getObjectIrisConvert[GroupIri](KnoraAdmin.ForGroup).map(_.headOption)
      forResourceClass    <- resource.getObjectIrisConvert[InternalIri](KnoraAdmin.ForResourceClass).map(_.headOption)
      forResourceProperty <- resource.getObjectIrisConvert[InternalIri](KnoraAdmin.ForProperty).map(_.headOption)
      forWhat <-
        ZIO.fromEither(ForWhat.from(forGroup, forResourceClass, forResourceProperty)).mapError(ConversionError.apply)
      permissions <- parsePermissions(resource)
    } yield DefaultObjectAccessPermission(id, forProject, forWhat, permissions)

    private def parsePermissions(resource: RdfResource) = for {
      permissionStr     <- resource.getStringLiteralOrFail[String](KnoraBase.HasPermissions)(Right(_))
      parsedPermissions <- parsePermission(permissionStr)
    } yield parsedPermissions

    private def parsePermission(permission: String): IO[RdfError, Chunk[DefaultObjectAccessPermissionPart]] = {
      def collectAllValidOrAllErrors(acc: Either[String, Chunk[GroupIri]], next: Either[String, GroupIri]) =
        (acc, next) match {
          case (Right(vs), Right(v)) => Right(vs :+ v)
          case (Left(es), Left(e))   => Left(es + "; " + e)
          case (Left(es), _)         => Left(es)
          case (_, Left(e))          => Left(e)
        }
      ZIO
        .foreach(Chunk.fromIterable(permission.split(permissionsDelimiter).map(_.trim))) { token =>
          token.split(' ') match {
            case Array(token, groups) => {
              val part: Either[String, DefaultObjectAccessPermissionPart] =
                Permission.ObjectAccess
                  .fromToken(token)
                  .flatMap { permission =>
                    Chunk
                      .fromIterable(groups.split(','))
                      .map(GroupIri.from)
                      .foldLeft[Either[String, Chunk[GroupIri]]](Right(Chunk.empty))(collectAllValidOrAllErrors)
                      .flatMap(NonEmptyChunk.fromChunk(_).toRight(s"No groupIris found for $permission"))
                      .map(DefaultObjectAccessPermissionPart(permission, _))
                  }
              ZIO.fromEither(part).mapError(ConversionError.apply)
            }
            case _ => ZIO.fail(ConversionError("Invalid hasPermission pattern"))
          }
        }
    }

    override def toTriples(entity: DefaultObjectAccessPermission): TriplePattern = {
      val id = Rdf.iri(entity.id.value)
      val pat: TriplePattern = id
        .isA(Vocabulary.KnoraAdmin.DefaultObjectAccessPermission)
        .andHas(Vocabulary.KnoraAdmin.forProject, Rdf.iri(entity.forProject.value))
        .andHas(Vocabulary.KnoraBase.hasPermissions, toStringLiteral(entity.permission))

      entity.forWhat match {
        case ForWhat.Group(g)          => pat.andHas(Vocabulary.KnoraAdmin.forGroup, Rdf.iri(g.value))
        case ForWhat.ResourceClass(rc) => pat.andHas(Vocabulary.KnoraAdmin.forResourceClass, Rdf.iri(rc.value))
        case ForWhat.Property(p)       => pat.andHas(Vocabulary.KnoraAdmin.forProperty, Rdf.iri(p.value))
        case ForWhat.ResourceClassAndProperty(rc, p) =>
          pat
            .andHas(Vocabulary.KnoraAdmin.forResourceClass, Rdf.iri(rc.value))
            .andHas(Vocabulary.KnoraAdmin.forProperty, Rdf.iri(p.value))
      }
    }
  }

  def toStringLiteral(permissions: Chunk[DefaultObjectAccessPermissionPart]): String = {
    def withOutPrefixExpansion(str: String) = str.replace(KnoraAdminPrefixExpansion, KnoraAdminPrefix)
    permissions
      .map(p => s"${p.permission.token} ${p.groups.map(_.value).map(withOutPrefixExpansion).mkString(",")}")
      .mkString(permissionsDelimiter.toString)
  }

  val layer = ZLayer.succeed(mapper) >>> ZLayer.derive[DefaultObjectAccessPermissionRepoLive]
}
