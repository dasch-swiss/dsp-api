/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.domain

import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.schema.DeriveSchema
import zio.schema.Schema

enum ActionName {
  case ApplyTopLeftCorrection extends ActionName
  case UpdateAssetMetadata    extends ActionName
  case MigrateSizes           extends ActionName
  case MigrateMimeTypes       extends ActionName
}

object ActionName {
  given codec: JsonCodec[ActionName] = DeriveJsonCodec.gen[ActionName]

  given schema: Schema[ActionName] = DeriveSchema.gen[ActionName]

  given tapirCodec: Codec[String, ActionName, TextPlain] =
    Codec.derivedEnumeration[String, ActionName].defaultStringBased
}
