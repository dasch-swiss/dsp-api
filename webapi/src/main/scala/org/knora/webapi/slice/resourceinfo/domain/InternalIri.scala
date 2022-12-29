/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.domain

import org.knora.webapi.IRI

final case class InternalIri(value: IRI)

object InternalIri {
  object Property {
    object KnoraBase {
      private val knoraBase           = "http://www.knora.org/ontology/knora-base#"
      val isDeleted: InternalIri      = InternalIri(knoraBase + "isDeleted")
      val isEditable: InternalIri     = InternalIri(knoraBase + "isEditable")
      val isMainResource: InternalIri = InternalIri(knoraBase + "isMainResource")
    }
  }
}
