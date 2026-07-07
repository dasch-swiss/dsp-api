/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*

object GetMappingQuerySpec extends ZIOSpecDefault {

  private val testMappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping"

  override def spec: Spec[TestEnvironment, Any] = suite("GetMappingQuerySpec")(
    test("should produce correct CONSTRUCT query for a mapping IRI") {
      val actual = GetMappingQuery.build(testMappingIri).getQueryString
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |CONSTRUCT { <http://rdfh.ch/standoff/mappings/StandardMapping> rdfs:label ?label .
            |<http://rdfh.ch/standoff/mappings/StandardMapping> rdf:type ?mappingType .
            |<http://rdfh.ch/standoff/mappings/StandardMapping> knora-base:mappingHasDefaultXSLTransformation ?defaultXSLTransformation .
            |?mappingElement rdf:type ?mappingElementType .
            |?mappingElement knora-base:mappingHasXMLTagname ?tagName .
            |?mappingElement knora-base:mappingHasXMLNamespace ?tagNamespace .
            |?mappingElement knora-base:mappingHasXMLClass ?tagClass .
            |?mappingElement knora-base:mappingHasStandoffClass ?standoffClass .
            |?mappingElement knora-base:mappingElementRequiresSeparator ?separatorRequired .
            |?mappingElement knora-base:mappingHasXMLAttribute ?attribute .
            |?attribute rdf:type ?attributeType .
            |?attribute knora-base:mappingHasXMLAttributename ?attributeName .
            |?attribute knora-base:mappingHasXMLNamespace ?attributeNamespace .
            |?attribute knora-base:mappingHasStandoffProperty ?standoffProperty .
            |?mappingElement knora-base:mappingHasStandoffDataTypeClass ?datatypeClass .
            |?datatypeClass rdf:type ?datatypeType .
            |?datatypeClass knora-base:mappingHasXMLAttributename ?datatypeAttributeName .
            |?datatypeClass knora-base:mappingHasStandoffClass ?datatypeStandoffClass . }
            |WHERE { <http://rdfh.ch/standoff/mappings/StandardMapping> rdfs:label ?label ;
            |    rdf:type ?mappingType .
            |OPTIONAL { <http://rdfh.ch/standoff/mappings/StandardMapping> knora-base:mappingHasDefaultXSLTransformation ?defaultXSLTransformation . }
            |<http://rdfh.ch/standoff/mappings/StandardMapping> knora-base:hasMappingElement ?mappingElement .
            |?mappingElement rdf:type ?mappingElementType ;
            |    knora-base:mappingHasXMLTagname ?tagName ;
            |    knora-base:mappingHasXMLNamespace ?tagNamespace ;
            |    knora-base:mappingHasXMLClass ?tagClass ;
            |    knora-base:mappingHasStandoffClass ?standoffClass ;
            |    knora-base:mappingElementRequiresSeparator ?separatorRequired .
            |OPTIONAL { ?mappingElement knora-base:mappingHasXMLAttribute ?attribute .
            |?attribute rdf:type ?attributeType ;
            |    knora-base:mappingHasXMLAttributename ?attributeName ;
            |    knora-base:mappingHasXMLNamespace ?attributeNamespace ;
            |    knora-base:mappingHasStandoffProperty ?standoffProperty . }
            |OPTIONAL { ?mappingElement knora-base:mappingHasStandoffDataTypeClass ?datatypeClass .
            |?datatypeClass rdf:type ?datatypeType ;
            |    knora-base:mappingHasXMLAttributename ?datatypeAttributeName ;
            |    knora-base:mappingHasStandoffClass ?datatypeStandoffClass . } }
            |""".stripMargin,
      )
    },
    test("should produce correct CONSTRUCT query for a different mapping IRI") {
      val differentMappingIri = "http://rdfh.ch/projects/0001/mappings/CustomMapping"
      val actual              = GetMappingQuery.build(differentMappingIri).getQueryString
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |CONSTRUCT { <http://rdfh.ch/projects/0001/mappings/CustomMapping> rdfs:label ?label .
            |<http://rdfh.ch/projects/0001/mappings/CustomMapping> rdf:type ?mappingType .
            |<http://rdfh.ch/projects/0001/mappings/CustomMapping> knora-base:mappingHasDefaultXSLTransformation ?defaultXSLTransformation .
            |?mappingElement rdf:type ?mappingElementType .
            |?mappingElement knora-base:mappingHasXMLTagname ?tagName .
            |?mappingElement knora-base:mappingHasXMLNamespace ?tagNamespace .
            |?mappingElement knora-base:mappingHasXMLClass ?tagClass .
            |?mappingElement knora-base:mappingHasStandoffClass ?standoffClass .
            |?mappingElement knora-base:mappingElementRequiresSeparator ?separatorRequired .
            |?mappingElement knora-base:mappingHasXMLAttribute ?attribute .
            |?attribute rdf:type ?attributeType .
            |?attribute knora-base:mappingHasXMLAttributename ?attributeName .
            |?attribute knora-base:mappingHasXMLNamespace ?attributeNamespace .
            |?attribute knora-base:mappingHasStandoffProperty ?standoffProperty .
            |?mappingElement knora-base:mappingHasStandoffDataTypeClass ?datatypeClass .
            |?datatypeClass rdf:type ?datatypeType .
            |?datatypeClass knora-base:mappingHasXMLAttributename ?datatypeAttributeName .
            |?datatypeClass knora-base:mappingHasStandoffClass ?datatypeStandoffClass . }
            |WHERE { <http://rdfh.ch/projects/0001/mappings/CustomMapping> rdfs:label ?label ;
            |    rdf:type ?mappingType .
            |OPTIONAL { <http://rdfh.ch/projects/0001/mappings/CustomMapping> knora-base:mappingHasDefaultXSLTransformation ?defaultXSLTransformation . }
            |<http://rdfh.ch/projects/0001/mappings/CustomMapping> knora-base:hasMappingElement ?mappingElement .
            |?mappingElement rdf:type ?mappingElementType ;
            |    knora-base:mappingHasXMLTagname ?tagName ;
            |    knora-base:mappingHasXMLNamespace ?tagNamespace ;
            |    knora-base:mappingHasXMLClass ?tagClass ;
            |    knora-base:mappingHasStandoffClass ?standoffClass ;
            |    knora-base:mappingElementRequiresSeparator ?separatorRequired .
            |OPTIONAL { ?mappingElement knora-base:mappingHasXMLAttribute ?attribute .
            |?attribute rdf:type ?attributeType ;
            |    knora-base:mappingHasXMLAttributename ?attributeName ;
            |    knora-base:mappingHasXMLNamespace ?attributeNamespace ;
            |    knora-base:mappingHasStandoffProperty ?standoffProperty . }
            |OPTIONAL { ?mappingElement knora-base:mappingHasStandoffDataTypeClass ?datatypeClass .
            |?datatypeClass rdf:type ?datatypeType ;
            |    knora-base:mappingHasXMLAttributename ?datatypeAttributeName ;
            |    knora-base:mappingHasStandoffClass ?datatypeStandoffClass . } }
            |""".stripMargin,
      )
    },
  )
}
