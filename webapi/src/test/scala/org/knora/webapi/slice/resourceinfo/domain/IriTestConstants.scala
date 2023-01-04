/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.domain

object IriTestConstants {
  object KnoraBase {
    val Ontology = "http://www.knora.org/ontology/knora-base#"
    object Property {
      val isDeleted: InternalIri      = InternalIri(KnoraBase.Ontology + "isDeleted")
      val isEditable: InternalIri     = InternalIri(KnoraBase.Ontology + "isEditable")
      val isMainResource: InternalIri = InternalIri(KnoraBase.Ontology + "isMainResource")
    }
    object Class {
      val Resource: InternalIri = InternalIri(KnoraBase.Ontology + "Resource")
    }
  }
}
