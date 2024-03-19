/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api.service

import zio.*

import java.time.Instant

import dsp.errors.BadRequestException
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.slice.resourceinfo.api.model.ListResponseDto
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.*
import org.knora.webapi.slice.resourceinfo.api.model.ResourceInfoDto
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfoRepo

final case class RestResourceInfoService(repo: ResourceInfoRepo, iriConverter: IriConverter) {

  private def lastModificationDateSort(order: Order)(one: ResourceInfoDto, two: ResourceInfoDto) =
    instant(order)(one.lastModificationDate, two.lastModificationDate)

  private def creationDateSort(order: Order)(one: ResourceInfoDto, two: ResourceInfoDto) =
    instant(order)(one.creationDate, two.creationDate)

  private def instant(order: Order)(one: Instant, two: Instant) =
    order match {
      case Asc  => two.compareTo(one) > 0
      case Desc => one.compareTo(two) > 0
    }

  private def sort(resources: List[ResourceInfoDto], order: Order, orderBy: OrderBy) = (orderBy, order) match {
    case (LastModificationDate, order) => resources.sortWith(lastModificationDateSort(order))
    case (CreationDate, order)         => resources.sortWith(creationDateSort(order))
  }

  /**
   * Queries the existing resources of a certain resource class of a single project and returns the [[ResourceInfoDto]] in a [[ListResponseDto]]
   * List can be sorted determined by the ordering.
   * @param projectIri an external IRI for the project
   * @param resourceClass an external IRI to the resource class to retrieve
   * @param order    sort by property
   * @param orderBy  sort by ascending or descending
   * @return the [[ListResponseDto]] for the project and resource class
   */
  def findByProjectAndResourceClass(
    projectIri: IriIdentifier,
    resourceClass: IRI,
    order: Order,
    orderBy: OrderBy,
  ): Task[ListResponseDto] =
    for {
      rc <- iriConverter
              .asInternalIri(resourceClass)
              .mapError(err => BadRequestException(s"Invalid resourceClass: ${err.getMessage}"))
      resources <- repo.findByProjectAndResourceClass(projectIri, rc).map(_.map(ResourceInfoDto.from))
    } yield ListResponseDto(sort(resources, order, orderBy))
}

object RestResourceInfoService {
  val layer = ZLayer.derive[RestResourceInfoService]
}
