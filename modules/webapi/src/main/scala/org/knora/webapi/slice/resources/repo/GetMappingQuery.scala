/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.IRI
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

/**
 * Builds a CONSTRUCT query to get a mapping for XML to standoff conversion from the triplestore.
 */
object GetMappingQuery {

  def build(mappingIri: IRI): Construct = {
    val mapping = Iri.unsafeFrom(mappingIri)
    Construct(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |
               |CONSTRUCT {
               |  $mapping rdfs:label ?label .
               |  $mapping rdf:type ?mappingType .
               |  $mapping knora-base:mappingHasDefaultXSLTransformation ?defaultXSLTransformation .
               |  ?mappingElement rdf:type ?mappingElementType .
               |  ?mappingElement knora-base:mappingHasXMLTagname ?tagName .
               |  ?mappingElement knora-base:mappingHasXMLNamespace ?tagNamespace .
               |  ?mappingElement knora-base:mappingHasXMLClass ?tagClass .
               |  ?mappingElement knora-base:mappingHasStandoffClass ?standoffClass .
               |  ?mappingElement knora-base:mappingElementRequiresSeparator ?separatorRequired .
               |  ?mappingElement knora-base:mappingHasXMLAttribute ?attribute .
               |  ?attribute rdf:type ?attributeType .
               |  ?attribute knora-base:mappingHasXMLAttributename ?attributeName .
               |  ?attribute knora-base:mappingHasXMLNamespace ?attributeNamespace .
               |  ?attribute knora-base:mappingHasStandoffProperty ?standoffProperty .
               |  ?mappingElement knora-base:mappingHasStandoffDataTypeClass ?datatypeClass .
               |  ?datatypeClass rdf:type ?datatypeType .
               |  ?datatypeClass knora-base:mappingHasXMLAttributename ?datatypeAttributeName .
               |  ?datatypeClass knora-base:mappingHasStandoffClass ?datatypeStandoffClass .
               |}
               |WHERE {
               |  $mapping rdfs:label ?label ;
               |    rdf:type ?mappingType .
               |  OPTIONAL { $mapping knora-base:mappingHasDefaultXSLTransformation ?defaultXSLTransformation . }
               |  $mapping knora-base:hasMappingElement ?mappingElement .
               |  ?mappingElement rdf:type ?mappingElementType ;
               |    knora-base:mappingHasXMLTagname ?tagName ;
               |    knora-base:mappingHasXMLNamespace ?tagNamespace ;
               |    knora-base:mappingHasXMLClass ?tagClass ;
               |    knora-base:mappingHasStandoffClass ?standoffClass ;
               |    knora-base:mappingElementRequiresSeparator ?separatorRequired .
               |  OPTIONAL {
               |    ?mappingElement knora-base:mappingHasXMLAttribute ?attribute .
               |    ?attribute rdf:type ?attributeType ;
               |      knora-base:mappingHasXMLAttributename ?attributeName ;
               |      knora-base:mappingHasXMLNamespace ?attributeNamespace ;
               |      knora-base:mappingHasStandoffProperty ?standoffProperty .
               |  }
               |  OPTIONAL {
               |    ?mappingElement knora-base:mappingHasStandoffDataTypeClass ?datatypeClass .
               |    ?datatypeClass rdf:type ?datatypeType ;
               |      knora-base:mappingHasXMLAttributename ?datatypeAttributeName ;
               |      knora-base:mappingHasStandoffClass ?datatypeStandoffClass .
               |  }
               |}""".render,
    )
  }
}
