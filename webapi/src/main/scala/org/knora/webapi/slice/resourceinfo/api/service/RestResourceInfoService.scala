/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api.service

import zio._
import zio.macros.accessible

import org.knora.webapi.IRI
import org.knora.webapi.slice.resourceinfo.api.model.ListResponseDto
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.Order
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.OrderBy

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

object RestResourceInfoService {
  val layer = ZLayer.derive[RestResourceInfoServiceLive]
}
