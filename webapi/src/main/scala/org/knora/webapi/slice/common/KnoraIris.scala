/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common
import eu.timepit.refined.types.string.NonEmptyString

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.ApiV2Simple
import org.knora.webapi.OntologySchema
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

object KnoraIris {

  opaque type ResourceId = NonEmptyString
  opaque type ValueId    = NonEmptyString

  final case class ValueIri private (
    smartIri: SmartIri,
    shortcode: Shortcode,
    resourceId: ResourceId,
    valueId: ValueId,
  ) { self =>
    def toInternal: InternalIri                       = self.smartIri.toInternalIri
    def toApiV2Complex: SmartIri                      = self.smartIri.toOntologySchema(ApiV2Complex)
    def toApiV2Simple: SmartIri                       = self.smartIri.toOntologySchema(ApiV2Simple)
    def toOntologySchema(s: OntologySchema): SmartIri = self.smartIri.toOntologySchema(s)
  }

  object ValueIri {
    def from(iri: SmartIri): Either[String, ValueIri] =
      if (!iri.isKnoraValueIri) {
        Left(s"<$iri> is not a Knora value IRI")
      } else {
        // the following two calls are safe because we checked that the
        // shortcode, resourceId and valueId are present in isKnoraValueIri
        val shortcode  = iri.getProjectShortcode.getOrElse(throw Exception())
        val resourceId = NonEmptyString.unsafeFrom(iri.getResourceID.getOrElse(throw Exception()))
        val valueId    = NonEmptyString.unsafeFrom(iri.getValueID.getOrElse(throw Exception()))
        Right(ValueIri(iri, shortcode, resourceId, valueId))
      }
  }
}
