/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.domain

import scala.util.Try

import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.KnoraIri

final case class InternalIri(value: IRI)
object InternalIri {
  def from(knoraIri: KnoraIri): InternalIri = from(knoraIri.smartIri)
  def from(smartIri: SmartIri): InternalIri = InternalIri(smartIri.toInternalSchema.toIri)
  def from(iri: IRI)(implicit sf: StringFormatter): Either[Throwable, InternalIri] =
    Try(sf.toSmartIri(iri))
      .flatMap(smartIri => Try(smartIri.toInternalSchema))
      .map(smartIri => InternalIri(smartIri.toIri))
      .toEither
}
