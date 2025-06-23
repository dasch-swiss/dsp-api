/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.domain

import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri

final case class InternalIri(value: IRI)
object InternalIri {
  def from(smartIri: SmartIri): InternalIri = InternalIri(smartIri.toInternalSchema.toIri)
}
