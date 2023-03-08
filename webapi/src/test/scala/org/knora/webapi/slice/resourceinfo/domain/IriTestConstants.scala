/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.domain
import org.knora.webapi.IRI

object IriTestConstants {
  object Project {
    val TestProject: IRI = "http://rdfh.ch/projects/0000"
  }
  private def makeEntity(ontologyIri: InternalIri, entityName: String) =
    InternalIri(s"${ontologyIri.value}#$entityName")

  object Anything {
    val Ontology: InternalIri = InternalIri("http://www.knora.org/ontology/0001/anything")

    object Property {
      val hasOtherThing: InternalIri = makeEntity(Anything.Ontology, "hasOtherThing")
    }

    object Class {
      val Thing: InternalIri = makeEntity(Anything.Ontology, "Thing")
    }
  }
  object Biblio {
    val Ontology: InternalIri = InternalIri("http://www.knora.org/ontology/0801/biblio")

    object Property {
      val hasTitle: InternalIri = makeEntity(Biblio.Ontology, "hasTitle")
    }

    object Class {
      val Publication: InternalIri    = makeEntity(Biblio.Ontology, "Publication")
      val Article: InternalIri        = makeEntity(Biblio.Ontology, "Article")
      val JournalArticle: InternalIri = makeEntity(Biblio.Ontology, "JournalArticle")
    }
    object Instance {
      val SomePublicationInstance: InternalIri    = makeEntity(Biblio.Ontology, "somePublicationInstance")
      val SomeArticleInstance: InternalIri        = makeEntity(Biblio.Ontology, "someArticleInstance")
      val SomeJournalArticleInstance: InternalIri = makeEntity(Biblio.Ontology, "someJournalArticleInstance")
    }
  }

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
