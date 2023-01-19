/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.domain

object IriTestConstants {
  private def makeEntity(ontologyIri: InternalIri, entityName: String) =
    InternalIri(s"${ontologyIri.value}#$entityName")

  object KnoraBase {
    val Ontology: InternalIri = InternalIri("http://www.knora.org/ontology/knora-base")
    object Property {
      val isDeleted: InternalIri      = makeEntity(KnoraBase.Ontology, "isDeleted")
      val isEditable: InternalIri     = makeEntity(KnoraBase.Ontology, "isEditable")
      val isMainResource: InternalIri = makeEntity(KnoraBase.Ontology, "isMainResource")
    }
    object Class {
      val Annotation: InternalIri = makeEntity(KnoraBase.Ontology, "Annotation")
      val Resource: InternalIri   = makeEntity(KnoraBase.Ontology, "Resource")
    }
  }
  object KnoraAdmin {
    val Ontology: InternalIri = InternalIri("http://www.knora.org/ontology/knora-admin")
    object Property {
      val belongsToProject: InternalIri = makeEntity(KnoraAdmin.Ontology, "belongsToProject")
    }
    object Class {
      val AdministrativePermission: InternalIri = makeEntity(KnoraAdmin.Ontology, "AdministrativePermission")
      val Institution: InternalIri              = makeEntity(KnoraAdmin.Ontology, "Institution")
      val Permission: InternalIri               = makeEntity(KnoraAdmin.Ontology, "Permission")
    }
  }
}
