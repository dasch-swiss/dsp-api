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
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ChangePropertyLabelsOrCommentsQuery {

  def build(
    propertyIri: PropertyIri,
    labelOrComment: LabelOrComment,
    newValues: Seq[LanguageTaggedStringLiteralV2],
    maybeLinkValuePropertyIri: Option[PropertyIri],
    lastModificationDate: LastModificationDate,
  ): UIO[Update] =
    Clock.instant.map { now =>
      val ontology = Iri.unsafeFrom(propertyIri.ontologyIri.toInternalSchema.toIri)
      val property = Iri.unsafeFrom(propertyIri.toInternalSchema.toIri)
      // A link property's link value property carries the same labels/comments,
      // so its old values are replaced alongside the property's own.
      val linkValueProp     = maybeLinkValuePropertyIri.map(iri => Iri.unsafeFrom(iri.toInternalSchema.toIri))
      val labelOrCommentIri = Iri.unsafeFrom(labelOrComment.toString) // rdfs:label or rdfs:comment
      val previousDate      = Literal.dateTime(lastModificationDate.value)
      val currentDate       = Literal.dateTime(now)
      val newLiterals       = newValues.map(v => Literal.langString(v.value, v.language.value))

      Update(
        sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                 |
                 |DELETE {
                 |  GRAPH $ontology {
                 |    $ontology knora-base:lastModificationDate $previousDate .
                 |    $property $labelOrCommentIri ?oldValues .
                 |    ${linkValueProp.whenSome(p => sparql"$p $labelOrCommentIri ?oldLinkValueValues .")}
                 |  }
                 |}
                 |INSERT {
                 |  GRAPH $ontology {
                 |    $ontology knora-base:lastModificationDate $currentDate .
                 |    ${newLiterals.map(l => sparql"$property $labelOrCommentIri $l .").joinLines}
                 |    ${linkValueProp.whenSome(p => newLiterals.map(l => sparql"$p $labelOrCommentIri $l .").joinLines)}
                 |  }
                 |}
                 |WHERE {
                 |  $ontology a owl:Ontology ;
                 |    knora-base:lastModificationDate $previousDate .
                 |  OPTIONAL { $property $labelOrCommentIri ?oldValues . }
                 |  ${linkValueProp.whenSome(p => sparql"OPTIONAL { $p $labelOrCommentIri ?oldLinkValueValues . }")}
                 |}""".render,
      )
    }
}
