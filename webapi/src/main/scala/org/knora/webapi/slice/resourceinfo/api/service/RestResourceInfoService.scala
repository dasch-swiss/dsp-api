/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api.service

import zio._
import zio.http.HttpError
import zio.macros.accessible

import java.time.Instant

import org.knora.webapi.IRI
import org.knora.webapi.slice.resourceinfo.api.model.ListResponseDto
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams._
import org.knora.webapi.slice.resourceinfo.api.model.ResourceInfoDto
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfoRepo

@accessible
trait RestResourceInfoService {

  /**
   * Queries the existing resources of a certain resource class of a single project and returns the [[ResourceInfoDto]] in a [[ListResponseDto]]
   * List can be sorted determined by the ordering.
   * @param projectIri an external IRI for the project
   * @param resourceClass an external IRI to the resource class to retrieve
   * @param order    sort by property
   * @param orderBy  sort by ascending or descending
   * @return
   *     success: the [[ListResponseDto]] for the project and resource class
   *     failure:
   *         * with an [[HttpError.BadRequest]] if projectIri or resource class are invalid
   *         * with an [[HttpError.InternalServerError]] if the repo causes a problem
   */
  def findByProjectAndResourceClass(
    projectIri: IRI,
    resourceClass: IRI,
    order: Order,
    orderBy: OrderBy
  ): Task[ListResponseDto]
}

final case class RestResourceInfoServiceLive(repo: ResourceInfoRepo, iriConverter: IriConverter)
    extends RestResourceInfoService {

  private def lastModificationDateSort(order: Order)(one: ResourceInfoDto, two: ResourceInfoDto) =
    instant(order)(one.lastModificationDate, two.lastModificationDate)

  private def creationDateSort(order: Order)(one: ResourceInfoDto, two: ResourceInfoDto) =
    instant(order)(one.creationDate, two.creationDate)

  private def instant(order: Order)(one: Instant, two: Instant) =
    order match {
      case ASC  => two.compareTo(one) > 0
      case DESC => one.compareTo(two) > 0
    }

  private def sort(resources: List[ResourceInfoDto], order: Order, orderBy: OrderBy) = (orderBy, order) match {
    case (`lastModificationDate`, order) => resources.sortWith(lastModificationDateSort(order))
    case (`creationDate`, order)         => resources.sortWith(creationDateSort(order))
  }

  override def findByProjectAndResourceClass(
    projectIri: IRI,
    resourceClass: IRI,
    order: Order,
    orderBy: OrderBy
  ): IO[HttpError, ListResponseDto] =
    for {
      p <- iriConverter
             .asInternalIri(projectIri)
             .mapError(err => HttpError.BadRequest(s"Invalid projectIri: ${err.getMessage}"))
      rc <- iriConverter
              .asInternalIri(resourceClass)
              .mapError(err => HttpError.BadRequest(s"Invalid resourceClass: ${err.getMessage}"))
      resources <- repo
                     .findByProjectAndResourceClass(p, rc)
                     .mapBoth(err => HttpError.InternalServerError(err.getMessage), _.map(ResourceInfoDto(_)))
      sorted = sort(resources, order, orderBy)
    } yield ListResponseDto(sorted)
}

object RestResourceInfoServiceLive {
  val layer = ZLayer.derive[RestResourceInfoServiceLive]
}
