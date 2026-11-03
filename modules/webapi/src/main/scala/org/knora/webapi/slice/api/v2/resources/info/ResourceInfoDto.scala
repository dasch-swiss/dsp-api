/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.resources.info

import zio.json.*

import java.time.Instant

import org.knora.webapi.IRI
import org.knora.webapi.slice.resources.domain.ResourceInfo

/**
 * @param asOf the server time read before the listing was queried; a client polling for changes passes it, minus a
 *             safety window, as the next `cutoff`
 */
final case class ListResponseDto private (resources: List[ResourceInfoDto], count: Int, asOf: Instant)
object ListResponseDto {
  def apply(list: List[ResourceInfoDto], asOf: Instant): ListResponseDto = ListResponseDto(list, list.size, asOf)

  implicit val codec: JsonCodec[ListResponseDto] = DeriveJsonCodec.gen[ListResponseDto]
}

/**
 * @param resourceClass the external (complex schema) IRI of the resource's class
 */
final case class ResourceInfoDto private (
  resourceIri: IRI,
  resourceClass: IRI,
  creationDate: Instant,
  lastModificationDate: Instant,
  deleteDate: Option[Instant],
  isDeleted: Boolean,
)

object ResourceInfoDto {

  def from(info: ResourceInfo, externalResourceClass: IRI): ResourceInfoDto =
    ResourceInfoDto(
      info.iri.value,
      externalResourceClass,
      info.creationDate,
      info.lastModificationDate.getOrElse(info.creationDate),
      info.deleteDate,
      info.isDeleted,
    )

  implicit val codec: JsonCodec[ResourceInfoDto] = DeriveJsonCodec.gen[ResourceInfoDto]
}
