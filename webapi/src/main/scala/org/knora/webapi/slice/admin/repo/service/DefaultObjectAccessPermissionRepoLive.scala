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
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.slice.admin.AdminConstants.permissionsDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.AdministrativePermission
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionPart
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionRepo
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermissionRepo
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

final case class DefaultObjectAccessPermissionRepoLive(
  triplestore: TriplestoreService,
  mapper: RdfEntityMapper[DefaultObjectAccessPermission],
) extends AbstractEntityRepo[DefaultObjectAccessPermission, PermissionIri](triplestore, mapper)
    with DefaultObjectAccessPermissionRepo {

  override protected val resourceClass: ParsedIRI = ParsedIRI.create(KnoraAdmin.DefaultObjectAccessPermission)
  override protected val namedGraphIri: Iri       = Rdf.iri(permissionsDataNamedGraph.value)

  override protected def entityProperties: EntityProperties =
    EntityProperties(
      NonEmptyChunk(Vocabulary.KnoraAdmin.forProject),
    )

  override def findByProject(projectIri: ProjectIri): Task[Chunk[DefaultObjectAccessPermission]] =
    findAllByTriplePattern(_.has(Vocabulary.KnoraAdmin.forProject, Rdf.iri(projectIri.value)))

  override def save(entity: DefaultObjectAccessPermission): Task[DefaultObjectAccessPermission] =
    ZIO.die(UnsupportedOperationException("Mapper not yet fully implemented"))
}

object DefaultObjectAccessPermissionRepoLive {
  private val mapper = new RdfEntityMapper[DefaultObjectAccessPermission] {

    override def toEntity(resource: RdfResource): IO[RdfError, DefaultObjectAccessPermission] =
      for {
        id <- resource.iri.flatMap { iri =>
                ZIO.fromEither(PermissionIri.from(iri.value).left.map(ConversionError.apply))
              }
        forProject <- resource.getObjectIrisConvert[ProjectIri](KnoraAdmin.ForProject).map(_.head)
      } yield DefaultObjectAccessPermission(id, forProject)

    override def toTriples(entity: DefaultObjectAccessPermission): TriplePattern = {
      val id = Rdf.iri(entity.id.value)
      id.isA(Vocabulary.KnoraAdmin.DefaultObjectAccessPermission)
        .andHas(Vocabulary.KnoraAdmin.forProject, Rdf.iri(entity.forProject.value))
    }
  }

  val layer = ZLayer.succeed(mapper) >>> ZLayer.derive[DefaultObjectAccessPermissionRepoLive]
}
