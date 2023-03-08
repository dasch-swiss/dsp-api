/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api

import zio._
import zio.http.model.HttpError

import org.knora.webapi.IRI
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceLive.Order
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceLive.OrderBy
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfoRepo

trait RestResourceInfoService {

  /**
   * Queries the existing resources of a certain resource class of a single project and returns the [[ResourceInfoDto]] in a [[ListResponseDto]]
   * List can be sorted determined by the ordering.
   * @param projectIri an external IRI for the project
   * @param resourceClass an external IRI to the resource class to retrieve
   * @param ordering sort by which property ascending or descending
   * @return
   *     success: the [[ListResponseDto]] for the project and resource class
   *     failure:
   *         * with an [[HttpError.BadRequest]] if projectIri or resource class are invalid
   *         * with an [[HttpError.InternalServerError]] if the repo causes a problem
   */
  def findByProjectAndResourceClass(
    projectIri: IRI,
    resourceClass: IRI,
    ordering: (OrderBy, Order)
  ): IO[HttpError, ListResponseDto]
}

object RestResourceInfoService {

  def findByProjectAndResourceClass(projectIri: IRI, resourceClass: IRI, ordering: (OrderBy, Order)) =
    ZIO.service[RestResourceInfoService].flatMap(_.findByProjectAndResourceClass(projectIri, resourceClass, ordering))

  val layer: ZLayer[ResourceInfoRepo with IriConverter, Nothing, RestResourceInfoService] =
    ZLayer.fromFunction(RestResourceInfoServiceLive(_, _))
}
