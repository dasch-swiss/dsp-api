/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api.model

import zio.json.*

import java.time.Instant

import org.knora.webapi.IRI
import org.knora.webapi.slice.resources.domain.ResourceInfo

final case class ListResponseDto private (resources: List[ResourceInfoDto], count: Int)
object ListResponseDto {
  val empty: ListResponseDto                              = ListResponseDto(List.empty, 0)
  def apply(list: List[ResourceInfoDto]): ListResponseDto = list match {
    case Nil  => ListResponseDto.empty
    case list => ListResponseDto(list, list.size)
  }

  implicit val codec: JsonCodec[ListResponseDto] = DeriveJsonCodec.gen[ListResponseDto]
}

final case class ResourceInfoDto private (
  resourceIri: IRI,
  creationDate: Instant,
  lastModificationDate: Instant,
  deleteDate: Option[Instant],
  isDeleted: Boolean,
)

object ResourceInfoDto {

  def from(info: ResourceInfo): ResourceInfoDto =
    ResourceInfoDto(
      info.iri,
      info.creationDate,
      info.lastModificationDate.getOrElse(info.creationDate),
      info.deleteDate,
      info.isDeleted,
    )

  implicit val codec: JsonCodec[ResourceInfoDto] = DeriveJsonCodec.gen[ResourceInfoDto]
}
