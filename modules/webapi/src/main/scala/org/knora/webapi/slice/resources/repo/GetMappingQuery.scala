/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.IRI
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

/**
 * Builds a CONSTRUCT query to get a mapping for XML to standoff conversion from the triplestore.
 */
object GetMappingQuery extends QueryBuilderHelper {

  def build(mappingIri: IRI): ConstructQuery = {
    val mapping                  = Rdf.iri(mappingIri)
    val label                    = variable("label")
    val mappingType              = variable("mappingType")
    val defaultXSLTransformation = variable("defaultXSLTransformation")
    val mappingElement           = variable("mappingElement")
    val mappingElementType       = variable("mappingElementType")
    val tagName                  = variable("tagName")
    val tagNamespace             = variable("tagNamespace")
    val tagClass                 = variable("tagClass")
    val standoffClass            = variable("standoffClass")
    val separatorRequired        = variable("separatorRequired")
    val attribute                = variable("attribute")
    val attributeType            = variable("attributeType")
    val attributeName            = variable("attributeName")
    val attributeNamespace       = variable("attributeNamespace")
    val standoffProperty         = variable("standoffProperty")
    val datatypeClass            = variable("datatypeClass")
    val datatypeType             = variable("datatypeType")
    val datatypeAttributeName    = variable("datatypeAttributeName")
    val datatypeStandoffClass    = variable("datatypeStandoffClass")

    val rdfsLabel = Rdf.iri(RDFS.LABEL.stringValue())
    val rdfType   = Rdf.iri(RDF.TYPE.stringValue())

    // CONSTRUCT triple patterns
    val constructPatterns = Seq(
      mapping.has(rdfsLabel, label),
      mapping.has(rdfType, mappingType),
      mapping.has(KnoraBase.mappingHasDefaultXSLTransformation, defaultXSLTransformation),
      mappingElement.has(rdfType, mappingElementType),
      mappingElement.has(KnoraBase.mappingHasXMLTagname, tagName),
      mappingElement.has(KnoraBase.mappingHasXMLNamespace, tagNamespace),
      mappingElement.has(KnoraBase.mappingHasXMLClass, tagClass),
      mappingElement.has(KnoraBase.mappingHasStandoffClass, standoffClass),
      mappingElement.has(KnoraBase.mappingElementRequiresSeparator, separatorRequired),
      mappingElement.has(KnoraBase.mappingHasXMLAttribute, attribute),
      attribute.has(rdfType, attributeType),
      attribute.has(KnoraBase.mappingHasXMLAttributename, attributeName),
      attribute.has(KnoraBase.mappingHasXMLNamespace, attributeNamespace),
      attribute.has(KnoraBase.mappingHasStandoffProperty, standoffProperty),
      mappingElement.has(KnoraBase.mappingHasStandoffDataTypeClass, datatypeClass),
      datatypeClass.has(rdfType, datatypeType),
      datatypeClass.has(KnoraBase.mappingHasXMLAttributename, datatypeAttributeName),
      datatypeClass.has(KnoraBase.mappingHasStandoffClass, datatypeStandoffClass),
    )

    // WHERE patterns
    val wherePatterns = mapping
      .has(rdfsLabel, label)
      .andHas(rdfType, mappingType)
      .and(
        mapping.has(KnoraBase.mappingHasDefaultXSLTransformation, defaultXSLTransformation).optional(),
      )
      .and(
        mapping.has(KnoraBase.hasMappingElement, mappingElement),
      )
      .and(
        mappingElement
          .has(rdfType, mappingElementType)
          .andHas(KnoraBase.mappingHasXMLTagname, tagName)
          .andHas(KnoraBase.mappingHasXMLNamespace, tagNamespace)
          .andHas(KnoraBase.mappingHasXMLClass, tagClass)
          .andHas(KnoraBase.mappingHasStandoffClass, standoffClass)
          .andHas(KnoraBase.mappingElementRequiresSeparator, separatorRequired),
      )
      .and(
        mappingElement
          .has(KnoraBase.mappingHasXMLAttribute, attribute)
          .and(
            attribute
              .has(rdfType, attributeType)
              .andHas(KnoraBase.mappingHasXMLAttributename, attributeName)
              .andHas(KnoraBase.mappingHasXMLNamespace, attributeNamespace)
              .andHas(KnoraBase.mappingHasStandoffProperty, standoffProperty),
          )
          .optional(),
      )
      .and(
        mappingElement
          .has(KnoraBase.mappingHasStandoffDataTypeClass, datatypeClass)
          .and(
            datatypeClass
              .has(rdfType, datatypeType)
              .andHas(KnoraBase.mappingHasXMLAttributename, datatypeAttributeName)
              .andHas(KnoraBase.mappingHasStandoffClass, datatypeStandoffClass),
          )
          .optional(),
      )

    Queries
      .CONSTRUCT(constructPatterns*)
      .prefix(KnoraBase.NS, RDF.NS, RDFS.NS)
      .where(wherePatterns)
  }
}
