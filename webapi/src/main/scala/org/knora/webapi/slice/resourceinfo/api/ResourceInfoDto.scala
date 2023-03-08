/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api

import zio.json._

import java.time.Instant

import org.knora.webapi.IRI
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfo

final case class ListResponseDto private (resources: List[ResourceInfoDto], count: Int)
object ListResponseDto {
  val empty: ListResponseDto = ListResponseDto(List.empty, 0)
  def apply(list: List[ResourceInfoDto]): ListResponseDto = list match {
    case Nil  => ListResponseDto.empty
    case list => ListResponseDto(list, list.size)
  }

  implicit val encoder: JsonEncoder[ListResponseDto] =
    DeriveJsonEncoder.gen[ListResponseDto]
}

final case class ResourceInfoDto private (
  resourceIri: IRI,
  creationDate: Instant,
  lastModificationDate: Instant,
  deleteDate: Option[Instant],
  isDeleted: Boolean
)
object ResourceInfoDto {
  def apply(info: ResourceInfo): ResourceInfoDto =
    ResourceInfoDto(
      info.iri,
      info.creationDate,
      info.lastModificationDate.getOrElse(info.creationDate),
      info.deleteDate,
      info.isDeleted
    )

  implicit val encoder: JsonEncoder[ResourceInfoDto] =
    DeriveJsonEncoder.gen[ResourceInfoDto]
}
