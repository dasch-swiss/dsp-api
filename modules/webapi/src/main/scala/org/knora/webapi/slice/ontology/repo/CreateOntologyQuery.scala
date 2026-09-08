/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import eu.timepit.refined.types.string.NonEmptyString
import zio.*

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object CreateOntologyQuery {

  def build(
    ontologyIri: OntologyIri,
    projectIri: ProjectIri,
    isShared: Boolean,
    ontologyLabel: String,
    ontologyComment: Option[NonEmptyString],
  ): UIO[(LastModificationDate, Update)] = LastModificationDate.instant.map { lmd =>
    val ontology = Iri.unsafeFrom(ontologyIri.toInternalSchema.toIri)
    val project  = Iri.unsafeFrom(projectIri.value)
    val shared   = Literal.bool(isShared)
    val label    = Literal.string(ontologyLabel)
    val comment  = ontologyComment.map(c => Literal.string(c.value))
    val created  = Literal.dateTime(lmd.value)

    val update = Update(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
               |PREFIX owl: <http://www.w3.org/2002/07/owl#>
               |
               |INSERT {
               |  GRAPH $ontology {
               |    $ontology a owl:Ontology ;
               |      knora-base:attachedToProject $project ;
               |      knora-base:isShared $shared ;
               |      rdfs:label $label ;
               |      ${comment.whenSome(c => sparql"rdfs:comment $c ;")}
               |      knora-base:lastModificationDate $created .
               |  }
               |}
               |WHERE {
               |  FILTER NOT EXISTS { $ontology a ?existingOntologyType . }
               |}""".render,
    )
    (lmd, update)
  }
}
