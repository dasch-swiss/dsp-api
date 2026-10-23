/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import cats.syntax.traverse.*
import zio.*

import java.time.Instant
import scala.util.Try

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.resources.domain.ResourceInfo
import org.knora.webapi.slice.resources.domain.ResourceInfoRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class ResourceInfoRepoLive(triplestore: TriplestoreService) extends ResourceInfoRepo {

  override def find(
    projectIri: ProjectIri,
    resourceClass: Option[InternalIri],
    cutoff: Option[Instant],
  ): Task[List[ResourceInfo]] =
    triplestore
      .query(GetResourceInfoQuery.build(projectIri, resourceClass, cutoff))
      .logError
      .flatMap(result =>
        ZIO.foreach(result.results.bindings.toList)(row =>
          ZIO.fromEither(ResourceInfoRepoLive.toResourceInfo(resourceClass)(row)),
        ),
      )
}

object ResourceInfoRepoLive {
  val layer = ZLayer.derive[ResourceInfoRepoLive]

  /**
   * [[GetResourceInfoQuery]] selects `?resourceClass` only when no class is requested; otherwise the requested class
   * is the row's class.
   */
  private[repo] def toResourceInfo(requestedClass: Option[InternalIri])(
    row: VariableResultsRow,
  ): Either[InconsistentRepositoryDataException, ResourceInfo] = {
    def required(v: String): Either[String, String]            = row.get(v).toRight(s"'$v' is not bound")
    def instant(v: String)(s: String): Either[String, Instant] =
      Try(Instant.parse(s)).toEither.left.map(_ => s"'$v' is not a timestamp: $s")
    def optionalInstant(v: String): Either[String, Option[Instant]] = row.get(v).traverse(instant(v))

    val parsed = for {
      iri           <- required("resource").flatMap(ResourceIri.from)
      resourceClass <- requestedClass.orElse(row.get("resourceClass").map(InternalIri.apply)).toRight("no class bound")
      creationDate  <- required("creationDate").flatMap(instant("creationDate"))
      lastModified  <- optionalInstant("lastModificationDate")
      deleteDate    <- optionalInstant("deleteDate")
      isDeleted     <- required("isDeleted").flatMap(s => s.toBooleanOption.toRight(s"'isDeleted' is not a boolean: $s"))
    } yield ResourceInfo(iri, resourceClass, creationDate, lastModified, deleteDate, isDeleted)
    parsed.left.map(err =>
      InconsistentRepositoryDataException(s"Invalid resource info for ${row.get("resource").getOrElse("?")}: $err"),
    )
  }
}
