/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.*

import java.time.Instant

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
      .flatMap { result =>
        ZIO
          .attempt(result.results.bindings.toList.map(toResourceInfo(resourceClass)))
          .flatMap(rows =>
            ZIO.fromEither(rows.collectFirst { case Left(e) => e }.toLeft(rows.collect { case Right(r) => r })),
          )
      }

  /**
   * [[GetResourceInfoQuery]] selects `?resourceClass` only when no class is requested; otherwise the requested class
   * is the row's class.
   */
  private def toResourceInfo(requestedClass: Option[InternalIri])(
    row: VariableResultsRow,
  ): Either[InconsistentRepositoryDataException, ResourceInfo] = {
    val rowMap = row.rowMap
    for {
      iri           <- ResourceIri.from(rowMap("resource")).left.map(InconsistentRepositoryDataException(_))
      resourceClass <- requestedClass
                         .orElse(rowMap.get("resourceClass").map(InternalIri.apply))
                         .toRight(InconsistentRepositoryDataException(s"No resource class bound for ${iri.value}"))
    } yield ResourceInfo(
      iri,
      resourceClass,
      Instant.parse(rowMap("creationDate")),
      rowMap.get("lastModificationDate").map(Instant.parse),
      rowMap.get("deleteDate").map(Instant.parse),
      rowMap("isDeleted").toBoolean,
    )
  }
}

object ResourceInfoRepoLive {
  val layer = ZLayer.derive[ResourceInfoRepoLive]
}
