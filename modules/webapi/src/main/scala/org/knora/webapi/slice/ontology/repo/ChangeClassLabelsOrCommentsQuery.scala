/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.*

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.store.triplestoremessages.LanguageTaggedStringLiteralV2
import org.knora.webapi.slice.api.v2.ontologies.LabelOrComment
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ChangeClassLabelsOrCommentsQuery {

  def build(
    resourceClassIri: ResourceClassIri,
    labelOrComment: LabelOrComment,
    newValues: Seq[LanguageTaggedStringLiteralV2],
    lastModificationDate: LastModificationDate,
  ): UIO[Update] =
    Clock.instant.map { now =>
      val ontology     = Iri.unsafeFrom(resourceClassIri.ontologyIri.toInternalSchema.toIri)
      val classIri     = Iri.unsafeFrom(resourceClassIri.toInternalSchema.toIri)
      val predicate    = Iri.unsafeFrom(labelOrComment.toString)
      val previousDate = Literal.dateTime(lastModificationDate.value)
      val currentDate  = Literal.dateTime(now)

      val newValueTriples = newValues
        .map(v => sparql"$classIri $predicate ${Literal.langString(v.value, v.language.value)} .")
        .joinLines

      Update(
        sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                 |
                 |DELETE {
                 |  GRAPH $ontology {
                 |    $ontology knora-base:lastModificationDate $previousDate .
                 |    $classIri $predicate ?oldValues .
                 |  }
                 |}
                 |INSERT {
                 |  GRAPH $ontology {
                 |    $ontology knora-base:lastModificationDate $currentDate .
                 |    $newValueTriples
                 |  }
                 |}
                 |WHERE {
                 |  $ontology a owl:Ontology ;
                 |    knora-base:lastModificationDate $previousDate .
                 |  $classIri ?p ?o .
                 |  OPTIONAL { $classIri $predicate ?oldValues . }
                 |}""".render,
      )
    }
}
