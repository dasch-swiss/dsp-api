/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api

import zhttp.http.HttpError
import zio._

import org.knora.webapi.IRI
import org.knora.webapi.slice.resourceinfo.api.SpyLiveRestResourceInfoService.orderingKey
import org.knora.webapi.slice.resourceinfo.api.SpyLiveRestResourceInfoService.projectIriKey
import org.knora.webapi.slice.resourceinfo.api.SpyLiveRestResourceInfoService.resourceClassKey
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.repo.ResourceInfoRepoFake

case class SpyLiveRestResourceInfoService(
  lastInvocation: Ref[Map[String, Any]],
  realService: LiveRestResourceInfoService
) extends RestResourceInfoService {
  override def findByProjectAndResourceClass(
    projectIri: IRI,
    resourceClass: IRI,
    ordering: (LiveRestResourceInfoService.OrderBy, LiveRestResourceInfoService.Order)
  ): IO[HttpError, ListResponseDto] = for {
    _ <-
      lastInvocation.set(Map(projectIriKey -> projectIri, resourceClassKey -> resourceClass, orderingKey -> ordering))
    result <- realService.findByProjectAndResourceClass(projectIri, resourceClass, ordering)
  } yield result
}

object SpyLiveRestResourceInfoService {
  val projectIriKey    = "projectIri"
  val resourceClassKey = "resourceClass"
  val orderingKey      = "ordering"
  def lastInvocation: ZIO[SpyLiveRestResourceInfoService, Nothing, Map[String, Any]] =
    ZIO.service[SpyLiveRestResourceInfoService].flatMap(_.lastInvocation.get)

  val layer: ZLayer[IriConverter with ResourceInfoRepoFake, Nothing, SpyLiveRestResourceInfoService] = ZLayer.fromZIO {
    for {
      ref          <- Ref.make(Map.empty[String, Any])
      repo         <- ZIO.service[ResourceInfoRepoFake]
      iriConverter <- ZIO.service[IriConverter]
      realService  <- ZIO.succeed(LiveRestResourceInfoService(repo, iriConverter))
    } yield SpyLiveRestResourceInfoService(ref, realService)
  }
}
