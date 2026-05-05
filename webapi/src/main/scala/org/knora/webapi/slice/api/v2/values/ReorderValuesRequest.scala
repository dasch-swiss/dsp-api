/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.values

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri

final case class ReorderValuesRequest(
  resourceIri: ResourceIri,
  propertyIri: String,
  orderedValueIris: List[ValueIri],
)

object ReorderValuesRequest {
  implicit val codec: JsonCodec[ReorderValuesRequest] = DeriveJsonCodec.gen[ReorderValuesRequest]
}

final case class ReorderValuesResponse(
  resourceIri: ResourceIri,
  propertyIri: String,
  valuesReordered: Int,
)

object ReorderValuesResponse {
  implicit val codec: JsonCodec[ReorderValuesResponse] = DeriveJsonCodec.gen[ReorderValuesResponse]
}
