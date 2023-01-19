/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api

import zio._

import org.knora.webapi.IRI
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceSpy.orderingKey
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceSpy.projectIriKey
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceSpy.resourceClassKey
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.repo.ResourceInfoRepoFake
import zio.http.model.HttpError

case class RestResourceInfoServiceSpy(
  lastInvocation: Ref[Map[String, Any]],
  realService: RestResourceInfoServiceLive
) extends RestResourceInfoService {
  override def findByProjectAndResourceClass(
    projectIri: IRI,
    resourceClass: IRI,
    ordering: (RestResourceInfoServiceLive.OrderBy, RestResourceInfoServiceLive.Order)
  ): IO[HttpError, ListResponseDto] = for {
    _ <-
      lastInvocation.set(Map(projectIriKey -> projectIri, resourceClassKey -> resourceClass, orderingKey -> ordering))
    result <- realService.findByProjectAndResourceClass(projectIri, resourceClass, ordering)
  } yield result
}

object RestResourceInfoServiceSpy {
  val projectIriKey    = "projectIri"
  val resourceClassKey = "resourceClass"
  val orderingKey      = "ordering"
  def lastInvocation: ZIO[RestResourceInfoServiceSpy, Nothing, Map[String, Any]] =
    ZIO.service[RestResourceInfoServiceSpy].flatMap(_.lastInvocation.get)

  val layer: ZLayer[IriConverter with ResourceInfoRepoFake, Nothing, RestResourceInfoServiceSpy] = ZLayer.fromZIO {
    for {
      ref          <- Ref.make(Map.empty[String, Any])
      repo         <- ZIO.service[ResourceInfoRepoFake]
      iriConverter <- ZIO.service[IriConverter]
      realService  <- ZIO.succeed(RestResourceInfoServiceLive(repo, iriConverter))
    } yield RestResourceInfoServiceSpy(ref, realService)
  }
}
