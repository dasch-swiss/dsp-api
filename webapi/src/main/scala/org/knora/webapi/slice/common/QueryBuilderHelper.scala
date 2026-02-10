/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.eclipse.rdf4j
import org.eclipse.rdf4j.model.impl.SimpleNamespace
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.PropertyPath
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.Variable
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfLiteral
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfValue

import java.time.Instant

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.store.triplestoremessages.BooleanLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.LanguageTaggedStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.OntologyLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.PlainStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LabelOrComment
import org.knora.webapi.messages.v2.responder.ontologymessages.PredicateInfoV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.KnoraIri
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

trait QueryBuilderHelper {

  def toRdfValue(ontologyLiteralV2: OntologyLiteralV2): RdfValue =
    ontologyLiteralV2 match {
      case l: SmartIriLiteralV2 => toRdfIri(l.value)
      case l: StringLiteralV2   => toRdfLiteral(l)
      case l: BooleanLiteralV2  => toRdfLiteral(l)
    }

  def toRdfLiteral(literalV2: StringLiteralV2): RdfLiteral.StringLiteral = literalV2 match {
    case LanguageTaggedStringLiteralV2(value, lang) => Rdf.literalOfLanguage(value, lang.value)
    case PlainStringLiteralV2(value)                => Rdf.literalOf(value)
  }

  def toRdfLiteral(str: StringValue): RdfLiteral.StringLiteral = Rdf.literalOf(str.value)

  def toRdfLiteral(booleanV2: BooleanLiteralV2): RdfLiteral.StringLiteral =
    Rdf.literalOfType(booleanV2.value.toString, XSD.BOOLEAN)

  def toRdfLiteral(lmd: LastModificationDate): RdfLiteral.StringLiteral = toRdfLiteral(lmd.value)
  def toRdfLiteral(instant: Instant): RdfLiteral.StringLiteral          = Rdf.literalOfType(instant.toString, XSD.DATETIME)

  def toRdfLiteral(int: Int): RdfLiteral.StringLiteral            = Rdf.literalOfType(int.toString, XSD.INT)
  def toRdfLiteralNonNegative(int: Int): RdfLiteral.StringLiteral =
    Rdf.literalOfType(int.toString, XSD.NON_NEGATIVE_INTEGER)

  def toRdfIri(iri: KnoraIri): Iri    = toRdfIri(iri.smartIri)
  def toRdfIri(iri: SmartIri): Iri    = Rdf.iri(iri.toInternalSchema.toIri)
  def toRdfIri(iri: InternalIri): Iri = Rdf.iri(iri.value)
  def toRdfIri(iri: StringValue): Iri = Rdf.iri(iri.value)

  def toRdfIri(labelOrComment: LabelOrComment): Iri = Rdf.iri(labelOrComment.toString)

  def ontologyAndNamespace(propertyIri: PropertyIri): (Iri, SimpleNamespace) =
    ontologyAndNamespace(propertyIri.ontologyIri)
  def ontologyAndNamespace(resourceClassIri: ResourceClassIri): (Iri, SimpleNamespace) =
    ontologyAndNamespace(resourceClassIri.ontologyIri)
  def ontologyAndNamespace(ontologyIri: OntologyIri): (Iri, SimpleNamespace) = {
    val ontology: Iri = toRdfIri(ontologyIri)
    val ontologyNS    = ontologyIri.ontologyName.value
    (ontology, SimpleNamespace(ontologyNS, ontologyIri.toInternalSchema.toString + "#"))
  }

  def toPropertyPatterns(iri: Iri, values: Iterable[PredicateInfoV2]): List[TriplePattern] =
    values.flatMap(pred => pred.objects.map(obj => iri.has(toRdfIri(pred.predicateIri), toRdfValue(obj)))).toList

  def graphIri(knoraProject: KnoraProject): Iri = Rdf.iri(ProjectService.projectDataNamedGraphV2(knoraProject).value)

  def variable(name: String): Variable = SparqlBuilder.`var`(name)

  def zeroOrMore(pred: Iri): PropertyPath             = PropertyPathBuilder.of(pred).zeroOrMore().build()
  def zeroOrMore(pred: rdf4j.model.IRI): PropertyPath = PropertyPathBuilder.of(pred).zeroOrMore().build()

  def askWhere(triplePattern: TriplePattern): Ask =
    Ask(s"""
           |ASK
           |WHERE {
           | ${triplePattern.getQueryString}
           |}
           |""".stripMargin)

  def spo: (Variable, Variable, Variable) = (variable("s"), variable("p"), variable("o"))

  def NS(ontologyIri: OntologyIri): SimpleNamespace =
    SimpleNamespace(ontologyIri.ontologyName.value, ontologyIri.toInternalSchema.toString + "#")
}
