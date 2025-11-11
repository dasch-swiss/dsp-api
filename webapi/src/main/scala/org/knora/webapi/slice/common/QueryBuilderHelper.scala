/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.eclipse.rdf4j.model.impl.SimpleNamespace
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfLiteral
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfValue

import java.time.Instant

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.store.triplestoremessages.BooleanLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.OntologyLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.PredicateInfoV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.KnoraIri
import org.knora.webapi.slice.common.KnoraIris.OntologyIri

trait QueryBuilderHelper {

  def toRdfValue(ontologyLiteralV2: OntologyLiteralV2): RdfValue =
    ontologyLiteralV2 match {
      case l: SmartIriLiteralV2 => toRdfIri(l.value)
      case l: StringLiteralV2   => toRdfLiteral(l)
      case l: BooleanLiteralV2  => toRdfLiteral(l)
    }

  def toRdfLiteral(literalV2: StringLiteralV2): RdfLiteral.StringLiteral = literalV2.language match {
    case Some(lang) => Rdf.literalOfLanguage(literalV2.value, lang)
    case None       => Rdf.literalOf(literalV2.value)
  }

  def toRdfLiteral(booleanV2: BooleanLiteralV2): RdfLiteral.StringLiteral = toRdfLiteral(booleanV2.value)

  def toRdfLiteral(instant: Instant): RdfLiteral.StringLiteral = Rdf.literalOfType(instant.toString, XSD.DATETIME)
  def toRdfLiteral(boolean: Boolean): RdfLiteral.StringLiteral = Rdf.literalOfType(boolean.toString, XSD.BOOLEAN)

  def toRdfLiteral(int: Int): RdfLiteral.StringLiteral = Rdf.literalOfType(int.toString, XSD.INT)
  def toRdfLiteralNonNegative(int: Int): RdfLiteral.StringLiteral =
    Rdf.literalOfType(int.toString, XSD.NON_NEGATIVE_INTEGER)

  def toRdfIri(iri: KnoraIri): Iri   = toRdfIri(iri.smartIri)
  def toRdfIri(iri: SmartIri): Iri   = Rdf.iri(iri.toInternalSchema.toIri)
  def toRdfIri(iri: ProjectIri): Iri = Rdf.iri(iri.value)

  def ontologyAndNamespace(ontologyIri: OntologyIri): (Iri, SimpleNamespace) = {
    val ontology: Iri = toRdfIri(ontologyIri)
    val ontologyNS    = ontologyIri.ontologyName.value
    (ontology, SimpleNamespace(ontologyNS, ontologyIri.toInternalSchema.toString + "#"))
  }

  def toPropertyPatterns(iri: Iri, values: Iterable[PredicateInfoV2]): List[TriplePattern] =
    values.flatMap(pred => pred.objects.map(obj => iri.has(toRdfIri(pred.predicateIri), toRdfValue(obj)))).toList
}
