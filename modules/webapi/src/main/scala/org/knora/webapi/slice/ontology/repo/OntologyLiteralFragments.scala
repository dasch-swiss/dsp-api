/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.store.triplestoremessages.BooleanLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.LanguageTaggedStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.OntologyLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.PlainStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages.PredicateInfoV2
import org.knora.webapi.slice.ontology.domain.model.Cardinality

/** Fragments shared by the ontology entity update queries. */
object OntologyLiteralFragments {

  /** Render an ontology literal as an interpolatable SPARQL value; IRIs are converted to the internal schema. */
  def toSparqlValue(lit: OntologyLiteralV2): SparqlValue = lit match {
    case SmartIriLiteralV2(value)                       => Iri.unsafeFrom(value.toInternalSchema.toIri)
    case LanguageTaggedStringLiteralV2(value, language) => Literal.langString(value, language.value)
    case PlainStringLiteralV2(value)                    => Literal.string(value)
    case BooleanLiteralV2(value)                        => Literal.bool(value)
  }

  /** One `subject <predicate> <object> .` triple per predicate object, in the order given. */
  def predicateTriples(subject: Iri, predicates: Iterable[PredicateInfoV2]): Fragment =
    predicates.flatMap { predicate =>
      val predicateIri = Iri.unsafeFrom(predicate.predicateIri.toInternalSchema.toIri)
      predicate.objects.map(obj => sparql"$subject $predicateIri ${toSparqlValue(obj)} .")
    }.joinLines

  /**
   * The OWL restriction triples for the given cardinalities, in the order given.
   * Each cardinality gets its own blank node, labelled `node1`, `node2`, ... .
   */
  def cardinalityTriples(classIri: Iri, cardinalities: Iterable[(SmartIri, KnoraCardinalityInfo)]): Fragment =
    cardinalities.zipWithIndex.map { case ((propertyIri, cardinalityInfo), index) =>
      val node           = BlankNode(s"node${index + 1}")
      val property       = Iri.unsafeFrom(propertyIri.toInternalSchema.toIri)
      val owlCardinality = Cardinality.toOwl(cardinalityInfo.cardinality)
      val cardinalityIri = Iri.unsafeFrom(owlCardinality.owlCardinalityIri)
      val cardinality    = Literal.nonNegativeInteger(owlCardinality.owlCardinalityValue)
      val guiOrder       = cardinalityInfo.guiOrder.map(Literal.nonNegativeInteger)

      val triples = Seq(
        sparql"$classIri rdfs:subClassOf $node .",
        sparql"$node a owl:Restriction .",
        sparql"$node owl:onProperty $property .",
        sparql"$node $cardinalityIri $cardinality .",
      ) ++ guiOrder.map(order => sparql"$node salsah-gui:guiOrder $order .")

      triples.joinLines
    }.joinLines
}
