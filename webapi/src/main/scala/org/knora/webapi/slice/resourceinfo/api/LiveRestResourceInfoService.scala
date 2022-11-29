/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api

import zhttp.http.HttpError
import zio.IO
import zio.ZLayer

import java.time.Instant

import org.knora.webapi.IRI
import org.knora.webapi.slice.resourceinfo.api.LiveRestResourceInfoService._
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfoRepo

final case class LiveRestResourceInfoService(repo: ResourceInfoRepo, iriConverter: IriConverter)
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

  private def sort(resources: List[ResourceInfoDto], ordering: (OrderBy, Order)) = ordering match {
    case (`lastModificationDate`, order) => resources.sortWith(lastModificationDateSort(order))
    case (`creationDate`, order)         => resources.sortWith(creationDateSort(order))
  }

  override def findByProjectAndResourceClass(
    projectIri: IRI,
    resourceClass: IRI,
    ordering: (OrderBy, Order)
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
      sorted = sort(resources, ordering)
    } yield ListResponseDto(sorted)
}

object LiveRestResourceInfoService {

  sealed trait OrderBy

  case object creationDate extends OrderBy

  case object lastModificationDate extends OrderBy

  object OrderBy {
    def make(str: String): Option[OrderBy] = str match {
      case "creationDate"         => Some(creationDate)
      case "lastModificationDate" => Some(lastModificationDate)
      case _                      => None
    }
  }

  sealed trait Order

  case object ASC extends Order

  case object DESC extends Order

  object Order {
    def make(str: String): Option[Order] = str match {
      case "ASC"  => Some(ASC)
      case "DESC" => Some(DESC)
      case _      => None
    }
  }
  val layer = ZLayer.fromFunction(new LiveRestResourceInfoService(_, _))
}
