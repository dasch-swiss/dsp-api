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
