/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.values

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

final case class ReorderValuesRequest(
  resourceIri: String,
  propertyIri: String,
  orderedValueIris: List[String],
)

object ReorderValuesRequest {
  implicit val codec: JsonCodec[ReorderValuesRequest] = DeriveJsonCodec.gen[ReorderValuesRequest]
}

final case class ReorderValuesResponse(
  resourceIri: String,
  propertyIri: String,
  valuesReordered: Int,
)

object ReorderValuesResponse {
  implicit val codec: JsonCodec[ReorderValuesResponse] = DeriveJsonCodec.gen[ReorderValuesResponse]
}
