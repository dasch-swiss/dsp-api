/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.IRI
import org.knora.webapi.messages.twirl.MappingElement
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object CreateNewMappingQuery extends QueryBuilderHelper {

  def build(
    dataNamedGraph: IRI,
    mappingIri: IRI,
    label: String,
    defaultXSLTransformation: Option[IRI],
    mappingElements: Seq[MappingElement],
  ): String = {
    val graphName  = Rdf.iri(dataNamedGraph)
    val mappingIRI = Rdf.iri(mappingIri)

    val mappingBase: TriplePattern =
      mappingIRI.isA(KnoraBase.XMLToStandoffMapping).andHas(RDFS.LABEL, Rdf.literalOf(label))

    val xslPatterns: Seq[TriplePattern] = defaultXSLTransformation.toSeq.map { xsl =>
      mappingIRI.has(KnoraBase.mappingHasDefaultXSLTransformation, Rdf.iri(xsl))
    }

    val elementPatterns: Seq[TriplePattern] = mappingElements.flatMap { ele =>
      val eleIri = Rdf.iri(ele.mappingElementIri)

      val hasMappingEle = mappingIRI.has(KnoraBase.hasMappingElement, eleIri)

      val elementProps = eleIri
        .isA(KnoraBase.MappingElement)
        .andHas(KnoraBase.mappingHasXMLTagname, Rdf.literalOf(ele.tagName))
        .andHas(KnoraBase.mappingHasXMLNamespace, Rdf.literalOf(ele.namespace))
        .andHas(KnoraBase.mappingHasXMLClass, Rdf.literalOf(ele.className))
        .andHas(KnoraBase.mappingHasStandoffClass, Rdf.iri(ele.standoffClass))
        .andHas(KnoraBase.mappingElementRequiresSeparator, ele.separatorRequired)

      val attrPatterns = ele.attributes.flatMap { attr =>
        val attrIri = Rdf.iri(attr.mappingXMLAttributeElementIri)
        Seq(
          eleIri.has(KnoraBase.mappingHasXMLAttribute, attrIri),
          attrIri
            .isA(KnoraBase.MappingXMLAttribute)
            .andHas(KnoraBase.mappingHasXMLAttributename, Rdf.literalOf(attr.attributeName))
            .andHas(KnoraBase.mappingHasXMLNamespace, Rdf.literalOf(attr.namespace))
            .andHas(KnoraBase.mappingHasStandoffProperty, Rdf.iri(attr.standoffProperty)),
        )
      }

      val dtcPatterns = ele.standoffDataTypeClass.toSeq.flatMap { dtc =>
        val dtcIri = Rdf.iri(dtc.mappingStandoffDataTypeClassElementIri)
        Seq(
          eleIri.has(KnoraBase.mappingHasStandoffDataTypeClass, dtcIri),
          dtcIri
            .isA(KnoraBase.MappingStandoffDataTypeClass)
            .andHas(KnoraBase.mappingHasXMLAttributename, Rdf.literalOf(dtc.attributeName))
            .andHas(KnoraBase.mappingHasStandoffClass, Rdf.iri(dtc.datatype)),
        )
      }

      Seq(hasMappingEle, elementProps) ++ attrPatterns ++ dtcPatterns
    }

    val allInsertPatterns = Seq(mappingBase) ++ xslPatterns ++ elementPatterns

    val p              = variable("p")
    val o              = variable("o")
    val filterNotExist = GraphPatterns.filterNotExists(mappingIRI.has(p, o))

    // Workaround: rdf4j drops FILTER NOT EXISTS when it's the only WHERE pattern.
    // See https://github.com/eclipse-rdf4j/rdf4j/issues/5561 — fixed in rdf4j 5.3.0.
    val insertQuery = Queries
      .MODIFY()
      .prefix(RDF.NS, RDFS.NS, KnoraBase.NS)
      .insert(allInsertPatterns*)
      .into(graphName)
      .getQueryString
      .replaceFirst("WHERE \\{\\s*}", "")
      .strip()

    s"$insertQuery\nWHERE { ${filterNotExist.getQueryString} }"
  }
}
