/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class SearchResultResourcesQuerySpec extends ZIOSpecDefault {

  private def canonical(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  private val resourceIri1 = "http://rdfh.ch/0001/resource1"
  private val resourceIri2 = "http://rdfh.ch/0001/resource2"

  // @formatter:off
  private val expectedWithoutStandoff =
    """|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
       |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
       |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
       |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
       |
       |CONSTRUCT {
       |  ?resource a <http://www.knora.org/ontology/knora-base#Resource> ;
       |    <http://www.knora.org/ontology/knora-base#isMainResource> true ;
       |    <http://www.knora.org/ontology/knora-base#attachedToProject> ?resourceProject ;
       |    <http://www.w3.org/2000/01/rdf-schema#label> ?label ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ?resourceType ;
       |    <http://www.knora.org/ontology/knora-base#attachedToUser> ?resourceCreator ;
       |    <http://www.knora.org/ontology/knora-base#hasPermissions> ?resourcePermissions ;
       |    <http://www.knora.org/ontology/knora-base#creationDate> ?creationDate ;
       |    <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate ;
       |    <http://www.knora.org/ontology/knora-base#hasResourceAuthorship> ?resourceAuthorship .
       |  ?resource <http://www.knora.org/ontology/knora-base#isDeleted> false .
       |  ?resource <http://www.knora.org/ontology/knora-base#hasValue> ?valueObject ;
       |    ?resourceValueProperty ?valueObject .
       |  ?valueObject ?valueObjectProperty ?valueObjectValue ;
       |    <http://www.knora.org/ontology/knora-base#valueHasUUID> ?currentValueUUID ;
       |    <http://www.knora.org/ontology/knora-base#hasPermissions> ?currentValuePermissions .
       |  ?resource <http://www.knora.org/ontology/knora-base#hasLinkTo> ?referredResource ;
       |    ?resourceLinkProperty ?referredResource .
       |  ?referredResource a <http://www.knora.org/ontology/knora-base#Resource> ;
       |    ?referredResourcePred ?referredResourceObj .
       |} WHERE {
       |  VALUES ?resource { <http://rdfh.ch/0001/resource1> }
       |  { ?resource <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ?resourceType .
       |?resourceType <http://www.w3.org/2000/01/rdf-schema#subClassOf>* <http://www.knora.org/ontology/knora-base#Resource> . }
       |  ?resource <http://www.knora.org/ontology/knora-base#attachedToProject> ?resourceProject ;
       |    <http://www.knora.org/ontology/knora-base#attachedToUser> ?resourceCreator ;
       |    <http://www.knora.org/ontology/knora-base#hasPermissions> ?resourcePermissions ;
       |    <http://www.knora.org/ontology/knora-base#creationDate> ?creationDate ;
       |    <http://www.w3.org/2000/01/rdf-schema#label> ?label .
       |  ?resource <http://www.knora.org/ontology/knora-base#isDeleted> false .
       |  OPTIONAL { ?resource <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate . }
       |  OPTIONAL { ?resource <http://www.knora.org/ontology/knora-base#hasResourceAuthorship> ?resourceAuthorship . }
       |  OPTIONAL { ?resource ?resourceValueProperty ?valueObject .
       |?resourceValueProperty <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>* <http://www.knora.org/ontology/knora-base#hasValue> .
       |?valueObject <http://www.knora.org/ontology/knora-base#hasPermissions> ?currentValuePermissions .
       |{ ?valueObject a ?valueObjectType ;
       |    ?valueObjectProperty ?valueObjectValue .
       |FILTER ( ( ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#valueHasStandoff> && ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#hasPermissions> ) ) } UNION { ?valueObject a <http://www.knora.org/ontology/knora-base#LinkValue> ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> ?resourceLinkProperty ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> ?referredResource .
       |?referredResource ?referredResourcePred ?referredResourceObj ;
       |    <http://www.knora.org/ontology/knora-base#isDeleted> false . } }
       |}""".stripMargin

  private val expectedWithStandoff =
    """|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
       |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
       |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
       |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
       |
       |CONSTRUCT {
       |  ?resource a <http://www.knora.org/ontology/knora-base#Resource> ;
       |    <http://www.knora.org/ontology/knora-base#isMainResource> true ;
       |    <http://www.knora.org/ontology/knora-base#attachedToProject> ?resourceProject ;
       |    <http://www.w3.org/2000/01/rdf-schema#label> ?label ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ?resourceType ;
       |    <http://www.knora.org/ontology/knora-base#attachedToUser> ?resourceCreator ;
       |    <http://www.knora.org/ontology/knora-base#hasPermissions> ?resourcePermissions ;
       |    <http://www.knora.org/ontology/knora-base#creationDate> ?creationDate ;
       |    <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate ;
       |    <http://www.knora.org/ontology/knora-base#hasResourceAuthorship> ?resourceAuthorship .
       |  ?resource <http://www.knora.org/ontology/knora-base#isDeleted> false .
       |  ?resource <http://www.knora.org/ontology/knora-base#hasValue> ?valueObject ;
       |    ?resourceValueProperty ?valueObject .
       |  ?valueObject ?valueObjectProperty ?valueObjectValue ;
       |    <http://www.knora.org/ontology/knora-base#valueHasUUID> ?currentValueUUID ;
       |    <http://www.knora.org/ontology/knora-base#hasPermissions> ?currentValuePermissions .
       |  ?valueObject <http://www.knora.org/ontology/knora-base#valueHasStandoff> ?standoffNode .
       |  ?standoffNode ?standoffProperty ?standoffValue ;
       |    <http://www.knora.org/ontology/knora-base#targetHasOriginalXMLID> ?targetOriginalXMLID .
       |  ?resource <http://www.knora.org/ontology/knora-base#hasLinkTo> ?referredResource ;
       |    ?resourceLinkProperty ?referredResource .
       |  ?referredResource a <http://www.knora.org/ontology/knora-base#Resource> ;
       |    ?referredResourcePred ?referredResourceObj .
       |} WHERE {
       |  VALUES ?resource { <http://rdfh.ch/0001/resource1> }
       |  { ?resource <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ?resourceType .
       |?resourceType <http://www.w3.org/2000/01/rdf-schema#subClassOf>* <http://www.knora.org/ontology/knora-base#Resource> . }
       |  ?resource <http://www.knora.org/ontology/knora-base#attachedToProject> ?resourceProject ;
       |    <http://www.knora.org/ontology/knora-base#attachedToUser> ?resourceCreator ;
       |    <http://www.knora.org/ontology/knora-base#hasPermissions> ?resourcePermissions ;
       |    <http://www.knora.org/ontology/knora-base#creationDate> ?creationDate ;
       |    <http://www.w3.org/2000/01/rdf-schema#label> ?label .
       |  ?resource <http://www.knora.org/ontology/knora-base#isDeleted> false .
       |  OPTIONAL { ?resource <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate . }
       |  OPTIONAL { ?resource <http://www.knora.org/ontology/knora-base#hasResourceAuthorship> ?resourceAuthorship . }
       |  OPTIONAL { ?resource ?resourceValueProperty ?valueObject .
       |?resourceValueProperty <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>* <http://www.knora.org/ontology/knora-base#hasValue> .
       |?valueObject <http://www.knora.org/ontology/knora-base#hasPermissions> ?currentValuePermissions .
       |{ { ?valueObject a ?valueObjectType ;
       |    ?valueObjectProperty ?valueObjectValue .
       |FILTER ( ( ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#valueHasStandoff> && ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#hasPermissions> ) ) } UNION { ?valueObject <http://www.knora.org/ontology/knora-base#valueHasStandoff> ?standoffNode .
       |?standoffNode ?standoffProperty ?standoffValue ;
       |    <http://www.knora.org/ontology/knora-base#standoffTagHasStartIndex> ?startIndex .
       |OPTIONAL { ?standoffTag <http://www.knora.org/ontology/knora-base#standoffTagHasInternalReference> ?targetStandoffTag .
       |?targetStandoffTag <http://www.knora.org/ontology/knora-base#standoffTagHasOriginalXMLID> ?targetOriginalXMLID . }
       |FILTER ( ?startIndex >= 0 ) } } UNION { ?valueObject a <http://www.knora.org/ontology/knora-base#LinkValue> ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> ?resourceLinkProperty ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> ?referredResource .
       |?referredResource ?referredResourcePred ?referredResourceObj ;
       |    <http://www.knora.org/ontology/knora-base#isDeleted> false . } }
       |}""".stripMargin
  // @formatter:on

  override val spec: Spec[Any, Nothing] = suite("SearchResultResourcesQuery")(
    test("without standoff - matches the legacy rendering") {
      val actual = SearchResultResourcesQuery.build(Seq(resourceIri1), queryStandoff = false).sparql
      assertTrue(canonical(actual) == canonical(expectedWithoutStandoff))
    },
    test("with standoff - matches the legacy rendering") {
      val actual = SearchResultResourcesQuery.build(Seq(resourceIri1), queryStandoff = true).sparql
      assertTrue(canonical(actual) == canonical(expectedWithStandoff))
    },
    test("multiple resources - all IRIs end up in the VALUES clause") {
      val actual = SearchResultResourcesQuery.build(Seq(resourceIri1, resourceIri2), queryStandoff = false).sparql
      assertTrue(actual.contains(s"VALUES ?resource { <$resourceIri1> <$resourceIri2> }"))
    },
  )
}
