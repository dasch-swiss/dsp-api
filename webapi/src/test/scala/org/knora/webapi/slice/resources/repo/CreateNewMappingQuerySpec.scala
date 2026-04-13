/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*

import org.knora.webapi.slice.resources.repo.model.MappingElement
import org.knora.webapi.slice.resources.repo.model.MappingStandoffDatatypeClass
import org.knora.webapi.slice.resources.repo.model.MappingXMLAttribute

object CreateNewMappingQuerySpec extends ZIOSpecDefault {

  private val dataNamedGraph = "http://www.knora.org/data/0001/anything"
  private val mappingIri     = "http://rdfh.ch/projects/0001/mappings/testMapping"
  private val label          = "Test Mapping"

  private val simpleElement = MappingElement(
    tagName = "p",
    namespace = "noNamespace",
    className = "noClass",
    standoffClass = "http://www.knora.org/ontology/standoff#StandoffParagraphTag",
    attributes = Seq.empty,
    standoffDataTypeClass = None,
    mappingElementIri = "http://rdfh.ch/projects/0001/mappings/testMapping/elements/1",
    separatorRequired = true,
  )

  private val elementWithAttributes = MappingElement(
    tagName = "a",
    namespace = "noNamespace",
    className = "noClass",
    standoffClass = "http://www.knora.org/ontology/standoff#StandoffHyperlinkTag",
    attributes = Seq(
      MappingXMLAttribute(
        attributeName = "href",
        namespace = "noNamespace",
        standoffProperty = "http://www.knora.org/ontology/knora-base#valueHasUri",
        mappingXMLAttributeElementIri = "http://rdfh.ch/projects/0001/mappings/testMapping/elements/2/attributes/1",
      ),
    ),
    standoffDataTypeClass = Some(
      MappingStandoffDatatypeClass(
        datatype = "http://www.knora.org/ontology/knora-base#StandoffUriTag",
        attributeName = "href",
        mappingStandoffDataTypeClassElementIri =
          "http://rdfh.ch/projects/0001/mappings/testMapping/elements/2/datatypeclass/1",
      ),
    ),
    mappingElementIri = "http://rdfh.ch/projects/0001/mappings/testMapping/elements/2",
    separatorRequired = false,
  )

  override def spec: Spec[Any, Nothing] = suite("CreateNewMappingQuery")(
    test("simple element without XSL transformation") {
      val actual = CreateNewMappingQuery.build(
        dataNamedGraph = dataNamedGraph,
        mappingIri = mappingIri,
        label = label,
        defaultXSLTransformation = None,
        mappingElements = Seq(simpleElement),
      )
      assertTrue(
        actual ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/projects/0001/mappings/testMapping> a knora-base:XMLToStandoffMapping ;
            |    rdfs:label "Test Mapping" .
            |<http://rdfh.ch/projects/0001/mappings/testMapping> knora-base:hasMappingElement <http://rdfh.ch/projects/0001/mappings/testMapping/elements/1> .
            |<http://rdfh.ch/projects/0001/mappings/testMapping/elements/1> a knora-base:MappingElement ;
            |    knora-base:mappingHasXMLTagname "p" ;
            |    knora-base:mappingHasXMLNamespace "noNamespace" ;
            |    knora-base:mappingHasXMLClass "noClass" ;
            |    knora-base:mappingHasStandoffClass <http://www.knora.org/ontology/standoff#StandoffParagraphTag> ;
            |    knora-base:mappingElementRequiresSeparator true . } }
            |WHERE { FILTER NOT EXISTS { <http://rdfh.ch/projects/0001/mappings/testMapping> ?p ?o . } }""".stripMargin,
      )
    },
    test("element with attributes, data type class, and XSL transformation") {
      val actual = CreateNewMappingQuery.build(
        dataNamedGraph = dataNamedGraph,
        mappingIri = mappingIri,
        label = label,
        defaultXSLTransformation = Some("http://rdfh.ch/projects/0001/xsl/testTransformation"),
        mappingElements = Seq(simpleElement, elementWithAttributes),
      )
      assertTrue(
        actual ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/projects/0001/mappings/testMapping> a knora-base:XMLToStandoffMapping ;
            |    rdfs:label "Test Mapping" .
            |<http://rdfh.ch/projects/0001/mappings/testMapping> knora-base:mappingHasDefaultXSLTransformation <http://rdfh.ch/projects/0001/xsl/testTransformation> .
            |<http://rdfh.ch/projects/0001/mappings/testMapping> knora-base:hasMappingElement <http://rdfh.ch/projects/0001/mappings/testMapping/elements/1> .
            |<http://rdfh.ch/projects/0001/mappings/testMapping/elements/1> a knora-base:MappingElement ;
            |    knora-base:mappingHasXMLTagname "p" ;
            |    knora-base:mappingHasXMLNamespace "noNamespace" ;
            |    knora-base:mappingHasXMLClass "noClass" ;
            |    knora-base:mappingHasStandoffClass <http://www.knora.org/ontology/standoff#StandoffParagraphTag> ;
            |    knora-base:mappingElementRequiresSeparator true .
            |<http://rdfh.ch/projects/0001/mappings/testMapping> knora-base:hasMappingElement <http://rdfh.ch/projects/0001/mappings/testMapping/elements/2> .
            |<http://rdfh.ch/projects/0001/mappings/testMapping/elements/2> a knora-base:MappingElement ;
            |    knora-base:mappingHasXMLTagname "a" ;
            |    knora-base:mappingHasXMLNamespace "noNamespace" ;
            |    knora-base:mappingHasXMLClass "noClass" ;
            |    knora-base:mappingHasStandoffClass <http://www.knora.org/ontology/standoff#StandoffHyperlinkTag> ;
            |    knora-base:mappingElementRequiresSeparator false .
            |<http://rdfh.ch/projects/0001/mappings/testMapping/elements/2> knora-base:mappingHasXMLAttribute <http://rdfh.ch/projects/0001/mappings/testMapping/elements/2/attributes/1> .
            |<http://rdfh.ch/projects/0001/mappings/testMapping/elements/2/attributes/1> a knora-base:MappingXMLAttribute ;
            |    knora-base:mappingHasXMLAttributename "href" ;
            |    knora-base:mappingHasXMLNamespace "noNamespace" ;
            |    knora-base:mappingHasStandoffProperty knora-base:valueHasUri .
            |<http://rdfh.ch/projects/0001/mappings/testMapping/elements/2> knora-base:mappingHasStandoffDataTypeClass <http://rdfh.ch/projects/0001/mappings/testMapping/elements/2/datatypeclass/1> .
            |<http://rdfh.ch/projects/0001/mappings/testMapping/elements/2/datatypeclass/1> a knora-base:MappingStandoffDataTypeClass ;
            |    knora-base:mappingHasXMLAttributename "href" ;
            |    knora-base:mappingHasStandoffClass knora-base:StandoffUriTag . } }
            |WHERE { FILTER NOT EXISTS { <http://rdfh.ch/projects/0001/mappings/testMapping> ?p ?o . } }""".stripMargin,
      )
    },
  )
}
