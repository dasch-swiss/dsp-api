/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3
import zio.json.JsonCodec

enum V3ErrorCode:
  case resource_not_found

object V3ErrorCode:
  given JsonCodec[V3ErrorCode] = JsonCodec.string
    .transformOrFail(
      str => V3ErrorCode.values.find(_.toString == str).toRight(s"Unknown V3ErrorCode: $str"),
      _.toString.toLowerCase,
    )
