/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue

final case class InternalFilename private (value: String) extends StringValue

object InternalFilename extends StringValueCompanion[InternalFilename] {
  def from(value: String): Either[String, InternalFilename] =
    if (value.isEmpty) Left("InternalFilename cannot be empty.")
    else Right(InternalFilename(value))
}
