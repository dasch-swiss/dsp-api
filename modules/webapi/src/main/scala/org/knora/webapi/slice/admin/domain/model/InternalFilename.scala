/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.json.JsonCodec

import org.knora.webapi.slice.api.admin.Codecs.TapirCodec
import org.knora.webapi.slice.api.admin.Codecs.TapirCodec.StringCodec
import org.knora.webapi.slice.api.admin.Codecs.ZioJsonCodec
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue

final case class InternalFilename private (value: String) extends StringValue

object InternalFilename extends StringValueCompanion[InternalFilename] {

  given JsonCodec[InternalFilename]   = ZioJsonCodec.stringCodec(InternalFilename.from)
  given StringCodec[InternalFilename] = TapirCodec.stringCodec(InternalFilename.from)

  def from(value: String): Either[String, InternalFilename] =
    if (value.isEmpty) Left("InternalFilename cannot be empty.")
    else Right(InternalFilename(value))
}
