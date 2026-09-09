/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.IRI
import org.knora.webapi.slice.resources.repo.model.MappingElement
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object CreateNewMappingQuery {

  def build(
    dataNamedGraph: IRI,
    mappingIri: IRI,
    label: String,
    defaultXSLTransformation: Option[IRI],
    mappingElements: Seq[MappingElement],
  ): Update = {
    val graphName  = Iri.unsafeFrom(dataNamedGraph)
    val mapping    = Iri.unsafeFrom(mappingIri)
    val mappingLbl = Literal.string(label)

    val xslTriple = defaultXSLTransformation.whenSome { xsl =>
      sparql"$mapping knora-base:mappingHasDefaultXSLTransformation ${Iri.unsafeFrom(xsl)} ."
    }

    val elementTriples = mappingElements.map { ele =>
      val eleIri = Iri.unsafeFrom(ele.mappingElementIri.value)

      val attributeTriples = Option.when(ele.attributes.nonEmpty) {
        ele.attributes.map { attr =>
          val attrIri = Iri.unsafeFrom(attr.mappingXMLAttributeElementIri.value)
          sparql"""|$eleIri knora-base:mappingHasXMLAttribute $attrIri .
                   |$attrIri a knora-base:MappingXMLAttribute ;
                   |  knora-base:mappingHasXMLAttributename ${Literal.string(attr.attributeName)} ;
                   |  knora-base:mappingHasXMLNamespace ${Literal.string(attr.namespace)} ;
                   |  knora-base:mappingHasStandoffProperty ${Iri.unsafeFrom(attr.standoffProperty)} ."""
        }.joinLines
      }

      val datatypeClassTriples = ele.standoffDataTypeClass.map { dtc =>
        val dtcIri = Iri.unsafeFrom(dtc.mappingStandoffDataTypeClassElementIri.value)
        sparql"""|$eleIri knora-base:mappingHasStandoffDataTypeClass $dtcIri .
                 |$dtcIri a knora-base:MappingStandoffDataTypeClass ;
                 |  knora-base:mappingHasXMLAttributename ${Literal.string(dtc.attributeName)} ;
                 |  knora-base:mappingHasStandoffClass ${Iri.unsafeFrom(dtc.datatype)} ."""
      }

      val elementCore =
        sparql"""|$mapping knora-base:hasMappingElement $eleIri .
                 |$eleIri a knora-base:MappingElement ;
                 |  knora-base:mappingHasXMLTagname ${Literal.string(ele.tagName)} ;
                 |  knora-base:mappingHasXMLNamespace ${Literal.string(ele.namespace)} ;
                 |  knora-base:mappingHasXMLClass ${Literal.string(ele.className)} ;
                 |  knora-base:mappingHasStandoffClass ${Iri.unsafeFrom(ele.standoffClass)} ;
                 |  knora-base:mappingElementRequiresSeparator ${Literal.bool(ele.separatorRequired)} ."""

      (Seq(elementCore) ++ attributeTriples ++ datatypeClassTriples).joinLines
    }.joinLines

    Update(
      sparql"""|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |INSERT {
               |  GRAPH $graphName {
               |    $mapping a knora-base:XMLToStandoffMapping ;
               |      rdfs:label $mappingLbl .
               |    $xslTriple
               |    $elementTriples
               |  }
               |}
               |WHERE {
               |  FILTER NOT EXISTS { $mapping ?p ?o . }
               |}""".render,
    )
  }
}
