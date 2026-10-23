/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.resources.info

import zio.*

import java.time.Instant

import dsp.errors.BadRequestException
import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.IRI
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.admin.model.Order
import org.knora.webapi.slice.api.admin.model.OrderBy
import org.knora.webapi.slice.api.v2.IriDto
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.resources.domain.ResourceInfoRepo

final case class ResourceInfoRestService(repo: ResourceInfoRepo, iriConverter: IriConverter) {

  private def sort(resources: List[ResourceInfoDto], order: Order, orderBy: OrderBy) = {
    val date: ResourceInfoDto => Instant = orderBy match {
      case OrderBy.LastModificationDate => _.lastModificationDate
      case OrderBy.CreationDate         => _.creationDate
    }
    val byDate = order match {
      case Order.Asc  => Ordering.by(date)
      case Order.Desc => Ordering.by(date).reverse
    }
    resources.sorted(using byDate.orElseBy(_.resourceIri))
  }

  private def toExternal(resourceClass: InternalIri): Task[IRI] =
    iriConverter
      .asExternalIri(resourceClass)
      .mapError(err =>
        InconsistentRepositoryDataException(s"Invalid stored resource class ${resourceClass.value}: ${err.getMessage}"),
      )

  /**
   * Lists the resources of a project, deleted ones included, each with its class.
   *
   * The response's `asOf` is the server time read before the repository is queried.
   *
   * A stored class that cannot be converted to an external IRI fails the whole listing with an
   * [[InconsistentRepositoryDataException]] rather than dropping its resources, which a client syncing by cutoff
   * would never see again.
   *
   * @param projectIri    the project; an unknown project gives an empty list
   * @param resourceClass when given, only resources of exactly this class (no subclasses)
   * @param cutoff        when given, only resources whose effective date is at or after it (inclusive); the effective
   *                      date is `lastModificationDate`, else `creationDate`
   * @param order         ascending or descending
   * @param orderBy       the date to sort by; equal dates are ordered by ascending resource IRI, also when descending
   */
  def find(
    projectIri: ProjectIri,
    resourceClass: Option[IriDto],
    cutoff: Option[Instant],
    order: Order,
    orderBy: OrderBy,
  ): Task[ListResponseDto] =
    for {
      asOf <- Clock.instant
      rc   <- ZIO.foreach(resourceClass)(c =>
              iriConverter
                .asInternalIri(c.value)
                .mapError(err => BadRequestException(s"Invalid resourceClass: ${err.getMessage}")),
            )
      infos             <- repo.find(projectIri, rc, cutoff)
      externalClassIris <- ZIO.foreach(infos.map(_.resourceClass).distinct)(c => toExternal(c).map(c -> _)).map(_.toMap)
      resources          = infos.map(info => ResourceInfoDto.from(info, externalClassIris(info.resourceClass)))
    } yield ListResponseDto(sort(resources, order, orderBy), asOf)
}

object ResourceInfoRestService {
  private[info] val layer = ZLayer.derive[ResourceInfoRestService]
}
