/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import java.time.Instant
import java.util.UUID

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.StandoffSelection
import org.knora.webapi.messages.StringFormatter

@RunWith(classOf[DspZTestJUnitRunner])
class GetResourcePropertiesAndValuesQuerySpec extends ZIOSpecDefault {

  private val sf = StringFormatter.getInitializedTestInstance

  private def canonical(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  private val resourceIri1 = "http://rdfh.ch/0001/resource1"
  private val resourceIri2 = "http://rdfh.ch/0001/resource2"
  private val propertyIri  = sf.toSmartIri("http://www.knora.org/ontology/0001/anything#hasText")
  private val valueUuid    = UUID.fromString("12345678-1234-1234-1234-123456789012")
  private val versionDate  = Instant.parse("2019-08-30T10:36:54.024Z")

  private def render(
    resourceIris: Seq[String] = Seq(resourceIri1),
    preview: Boolean = false,
    withDeleted: Boolean = false,
    standoff: StandoffSelection = StandoffSelection.NoStandoff,
    maybePropertyIri: Option[org.knora.webapi.messages.SmartIri] = None,
    maybeValueUuid: Option[UUID] = None,
    maybeVersionDate: Option[Instant] = None,
  ): String =
    GetResourcePropertiesAndValuesQuery
      .build(
        resourceIris = resourceIris,
        preview = preview,
        withDeleted = withDeleted,
        standoff = standoff,
        maybePropertyIri = maybePropertyIri,
        maybeValueUuid = maybeValueUuid,
        maybeVersionDate = maybeVersionDate,
      )
      .sparql

  // @formatter:off
  private val expectedBasic =
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

  private val expectedPreview =
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
       |}""".stripMargin

  private val expectedWithDeleted =
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
       |  ?resource <http://www.knora.org/ontology/knora-base#isDeleted> ?isDeleted ;
       |    <http://www.knora.org/ontology/knora-base#deleteDate> ?deletionDate ;
       |    <http://www.knora.org/ontology/knora-base#deleteComment> ?deleteComment .
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
       |  OPTIONAL { ?resource <http://www.knora.org/ontology/knora-base#isDeleted> ?isDeleted ;
       |    <http://www.knora.org/ontology/knora-base#deleteDate> ?deletionDate .
       |OPTIONAL { ?resource <http://www.knora.org/ontology/knora-base#deleteComment> ?deleteComment . } }
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

  private val expectedVersionDate =
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
       |  { ?resource <http://www.knora.org/ontology/knora-base#creationDate> ?creationDate .
       |FILTER ( ?creationDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> ) }
       |  OPTIONAL { ?resource <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate . }
       |  OPTIONAL { ?resource <http://www.knora.org/ontology/knora-base#hasResourceAuthorship> ?resourceAuthorship . }
       |  OPTIONAL { ?resource ?resourceValueProperty ?currentValue .
       |?resourceValueProperty <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>* <http://www.knora.org/ontology/knora-base#hasValue> .
       |FILTER NOT EXISTS { ?currentValue <http://www.knora.org/ontology/knora-base#deleteDate> ?currentValueDeleteDate .
       |FILTER ( ?currentValueDeleteDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> ) }
       |?currentValue <http://www.knora.org/ontology/knora-base#valueHasUUID> ?currentValueUUID .
       |{ ?currentValue <http://www.knora.org/ontology/knora-base#previousValue>* ?valueObject .
       |?valueObject <http://www.knora.org/ontology/knora-base#valueCreationDate> ?valueObjectCreationDate .
       |FILTER ( ?valueObjectCreationDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> ) }
       |FILTER NOT EXISTS { ?currentValue <http://www.knora.org/ontology/knora-base#previousValue>* ?otherValueObject .
       |?otherValueObject <http://www.knora.org/ontology/knora-base#valueCreationDate> ?otherValueObjectCreationDate .
       |FILTER ( ( ?otherValueObjectCreationDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> && ?otherValueObjectCreationDate > ?valueObjectCreationDate ) ) }
       |?currentValue <http://www.knora.org/ontology/knora-base#hasPermissions> ?currentValuePermissions .
       |{ ?valueObject a ?valueObjectType ;
       |    ?valueObjectProperty ?valueObjectValue .
       |FILTER ( ( ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#valueHasStandoff> && ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#hasPermissions> ) ) } UNION { ?valueObject a <http://www.knora.org/ontology/knora-base#LinkValue> ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> ?resourceLinkProperty ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> ?referredResource .
       |?referredResource ?referredResourcePred ?referredResourceObj ;
       |    <http://www.knora.org/ontology/knora-base#isDeleted> false . } }
       |}""".stripMargin

  private val expectedStandoff =
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
       |OPTIONAL { ?standoffNode <http://www.knora.org/ontology/knora-base#standoffTagHasInternalReference> ?targetStandoffTag .
       |?targetStandoffTag <http://www.knora.org/ontology/knora-base#standoffTagHasOriginalXMLID> ?targetOriginalXMLID . }
       |FILTER ( ?startIndex >= 0 ) } } UNION { ?valueObject a <http://www.knora.org/ontology/knora-base#LinkValue> ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> ?resourceLinkProperty ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> ?referredResource .
       |?referredResource ?referredResourcePred ?referredResourceObj ;
       |    <http://www.knora.org/ontology/knora-base#isDeleted> false . } }
       |}""".stripMargin

  private val expectedPropertyIri =
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
       |{ ?resource ?resourceValueProperty ?valueObject .
       |FILTER ( ?resourceValueProperty = <http://www.knora.org/ontology/0001/anything#hasText> ) }
       |?valueObject <http://www.knora.org/ontology/knora-base#hasPermissions> ?currentValuePermissions .
       |{ ?valueObject a ?valueObjectType ;
       |    ?valueObjectProperty ?valueObjectValue .
       |FILTER ( ( ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#valueHasStandoff> && ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#hasPermissions> ) ) } UNION { ?valueObject a <http://www.knora.org/ontology/knora-base#LinkValue> ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> ?resourceLinkProperty ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> ?referredResource .
       |?referredResource ?referredResourcePred ?referredResourceObj ;
       |    <http://www.knora.org/ontology/knora-base#isDeleted> false . } }
       |}""".stripMargin

  private val expectedValueUuid =
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
       |?valueObject <http://www.knora.org/ontology/knora-base#valueHasUUID> "EjRWeBI0EjQSNBI0VniQEg" .
       |?valueObject <http://www.knora.org/ontology/knora-base#hasPermissions> ?currentValuePermissions .
       |{ ?valueObject a ?valueObjectType ;
       |    ?valueObjectProperty ?valueObjectValue .
       |FILTER ( ( ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#valueHasStandoff> && ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#hasPermissions> ) ) } UNION { ?valueObject a <http://www.knora.org/ontology/knora-base#LinkValue> ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> ?resourceLinkProperty ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> ?referredResource .
       |?referredResource ?referredResourcePred ?referredResourceObj ;
       |    <http://www.knora.org/ontology/knora-base#isDeleted> false . } }
       |}""".stripMargin

  private val expectedMultipleResources =
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
       |  VALUES ?resource { <http://rdfh.ch/0001/resource1> <http://rdfh.ch/0001/resource2> }
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

  private val expectedVersionDateWithDeleted =
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
       |  ?resource <http://www.knora.org/ontology/knora-base#isDeleted> ?isDeleted ;
       |    <http://www.knora.org/ontology/knora-base#deleteDate> ?deletionDate ;
       |    <http://www.knora.org/ontology/knora-base#deleteComment> ?deleteComment .
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
       |  OPTIONAL { ?resource <http://www.knora.org/ontology/knora-base#isDeleted> ?isDeleted ;
       |    <http://www.knora.org/ontology/knora-base#deleteDate> ?deletionDate .
       |OPTIONAL { ?resource <http://www.knora.org/ontology/knora-base#deleteComment> ?deleteComment . } }
       |  { ?resource <http://www.knora.org/ontology/knora-base#creationDate> ?creationDate .
       |FILTER ( ?creationDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> ) }
       |  OPTIONAL { ?resource <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate . }
       |  OPTIONAL { ?resource <http://www.knora.org/ontology/knora-base#hasResourceAuthorship> ?resourceAuthorship . }
       |  OPTIONAL { ?resource ?resourceValueProperty ?currentValue .
       |?resourceValueProperty <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>* <http://www.knora.org/ontology/knora-base#hasValue> .
       |?currentValue <http://www.knora.org/ontology/knora-base#valueHasUUID> ?currentValueUUID .
       |{ ?currentValue <http://www.knora.org/ontology/knora-base#previousValue>* ?valueObject .
       |?valueObject <http://www.knora.org/ontology/knora-base#valueCreationDate> ?valueObjectCreationDate .
       |FILTER ( ?valueObjectCreationDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> ) }
       |FILTER NOT EXISTS { ?currentValue <http://www.knora.org/ontology/knora-base#previousValue>* ?otherValueObject .
       |?otherValueObject <http://www.knora.org/ontology/knora-base#valueCreationDate> ?otherValueObjectCreationDate .
       |FILTER ( ( ?otherValueObjectCreationDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> && ?otherValueObjectCreationDate > ?valueObjectCreationDate ) ) }
       |?currentValue <http://www.knora.org/ontology/knora-base#hasPermissions> ?currentValuePermissions .
       |{ ?valueObject a ?valueObjectType ;
       |    ?valueObjectProperty ?valueObjectValue .
       |FILTER ( ( ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#valueHasStandoff> && ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#hasPermissions> ) ) } UNION { ?valueObject a <http://www.knora.org/ontology/knora-base#LinkValue> ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> ?resourceLinkProperty ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> ?referredResource .
       |?referredResource ?referredResourcePred ?referredResourceObj ;
       |    <http://www.knora.org/ontology/knora-base#isDeleted> false . } }
       |}""".stripMargin

  private val expectedVersionDateWithUuid =
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
       |  { ?resource <http://www.knora.org/ontology/knora-base#creationDate> ?creationDate .
       |FILTER ( ?creationDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> ) }
       |  OPTIONAL { ?resource <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate . }
       |  OPTIONAL { ?resource <http://www.knora.org/ontology/knora-base#hasResourceAuthorship> ?resourceAuthorship . }
       |  OPTIONAL { ?resource ?resourceValueProperty ?currentValue .
       |?resourceValueProperty <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>* <http://www.knora.org/ontology/knora-base#hasValue> .
       |FILTER NOT EXISTS { ?currentValue <http://www.knora.org/ontology/knora-base#deleteDate> ?currentValueDeleteDate .
       |FILTER ( ?currentValueDeleteDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> ) }
       |?currentValue <http://www.knora.org/ontology/knora-base#valueHasUUID> ?currentValueUUID .
       |{ ?currentValue <http://www.knora.org/ontology/knora-base#valueHasUUID> ?currentValueUUID .
       |FILTER ( ?currentValueUUID = "EjRWeBI0EjQSNBI0VniQEg" ) }
       |{ ?currentValue <http://www.knora.org/ontology/knora-base#previousValue>* ?valueObject .
       |?valueObject <http://www.knora.org/ontology/knora-base#valueCreationDate> ?valueObjectCreationDate .
       |FILTER ( ?valueObjectCreationDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> ) }
       |FILTER NOT EXISTS { ?currentValue <http://www.knora.org/ontology/knora-base#previousValue>* ?otherValueObject .
       |?otherValueObject <http://www.knora.org/ontology/knora-base#valueCreationDate> ?otherValueObjectCreationDate .
       |FILTER ( ( ?otherValueObjectCreationDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> && ?otherValueObjectCreationDate > ?valueObjectCreationDate ) ) }
       |?currentValue <http://www.knora.org/ontology/knora-base#hasPermissions> ?currentValuePermissions .
       |{ ?valueObject a ?valueObjectType ;
       |    ?valueObjectProperty ?valueObjectValue .
       |FILTER ( ( ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#valueHasStandoff> && ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#hasPermissions> ) ) } UNION { ?valueObject a <http://www.knora.org/ontology/knora-base#LinkValue> ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> ?resourceLinkProperty ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> ?referredResource .
       |?referredResource ?referredResourcePred ?referredResourceObj ;
       |    <http://www.knora.org/ontology/knora-base#isDeleted> false . } }
       |}""".stripMargin

  private val expectedStandoffTagFilter =
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
       |  ?standoffNode a <http://www.knora.org/ontology/standoff#StandoffFootnoteTag> ;
       |    ?standoffProperty ?standoffValue .
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
       |?standoffNode a <http://www.knora.org/ontology/standoff#StandoffFootnoteTag> .
       |?standoffNode ?standoffProperty ?standoffValue ;
       |    <http://www.knora.org/ontology/knora-base#standoffTagHasStartIndex> ?startIndex .
       |FILTER ( ?startIndex >= 0 ) } } UNION { ?valueObject a <http://www.knora.org/ontology/knora-base#LinkValue> ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> ?resourceLinkProperty ;
       |    <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> ?referredResource .
       |?referredResource ?referredResourcePred ?referredResourceObj ;
       |    <http://www.knora.org/ontology/knora-base#isDeleted> false . } }
       |}""".stripMargin

  // @formatter:on

  // @formatter:off
  private val expectedStandoffOnlyWithoutStoredXml =
    """|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
       |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
       |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
       |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
       |
       |CONSTRUCT {
       |  ?resource a knora-base:Resource ;
       |    knora-base:isMainResource true ;
       |    knora-base:attachedToProject ?resourceProject ;
       |    rdfs:label ?label ;
       |    rdf:type ?resourceType ;
       |    knora-base:attachedToUser ?resourceCreator ;
       |    knora-base:hasPermissions ?resourcePermissions ;
       |    knora-base:creationDate ?creationDate ;
       |    knora-base:lastModificationDate ?lastModificationDate ;
       |    knora-base:hasResourceAuthorship ?resourceAuthorship .
       |  ?resource knora-base:isDeleted false .
       |  ?resource knora-base:hasValue ?valueObject ;
       |    ?resourceValueProperty ?valueObject .
       |  ?valueObject ?valueObjectProperty ?valueObjectValue ;
       |    knora-base:valueHasUUID ?currentValueUUID ;
       |    knora-base:hasPermissions ?currentValuePermissions .
       |  ?valueObject knora-base:valueHasStandoff ?standoffNode .
       |  ?standoffNode ?standoffProperty ?standoffValue ;
       |    knora-base:targetHasOriginalXMLID ?targetOriginalXMLID .
       |  ?resource knora-base:hasLinkTo ?referredResource ;
       |    ?resourceLinkProperty ?referredResource .
       |  ?referredResource a knora-base:Resource ;
       |    ?referredResourcePred ?referredResourceObj .
       |} WHERE {
       |  VALUES ?resource { <http://rdfh.ch/0001/resource1> }
       |  {
       |    ?resource rdf:type ?resourceType .
       |    ?resourceType rdfs:subClassOf* knora-base:Resource .
       |  }
       |  ?resource knora-base:attachedToProject ?resourceProject ;
       |    knora-base:attachedToUser ?resourceCreator ;
       |    knora-base:hasPermissions ?resourcePermissions ;
       |    knora-base:creationDate ?creationDate ;
       |    rdfs:label ?label .
       |  ?resource knora-base:isDeleted false .
       |  OPTIONAL { ?resource knora-base:lastModificationDate ?lastModificationDate . }
       |  OPTIONAL { ?resource knora-base:hasResourceAuthorship ?resourceAuthorship . }
       |  OPTIONAL {
       |    ?resource ?resourceValueProperty ?valueObject .
       |    ?resourceValueProperty rdfs:subPropertyOf* knora-base:hasValue .
       |    ?valueObject knora-base:hasPermissions ?currentValuePermissions .
       |    {
       |      {
       |        ?valueObject a ?valueObjectType ;
       |          ?valueObjectProperty ?valueObjectValue .
       |        FILTER(?valueObjectProperty != knora-base:valueHasStandoff && ?valueObjectProperty != knora-base:hasPermissions)
       |      } UNION {
       |        {
       |          ?valueObject knora-base:valueHasStandoff ?standoffNode .
       |          FILTER NOT EXISTS { ?valueObject knora-base:valueHasXml [] }
       |        }
       |        ?standoffNode ?standoffProperty ?standoffValue ;
       |          knora-base:standoffTagHasStartIndex ?startIndex .
       |        OPTIONAL {
       |          ?standoffNode knora-base:standoffTagHasInternalReference ?targetStandoffTag .
       |          ?targetStandoffTag knora-base:standoffTagHasOriginalXMLID ?targetOriginalXMLID .
       |        }
       |        FILTER(?startIndex >= 0)
       |      }
       |    } UNION {
       |      ?valueObject a knora-base:LinkValue ;
       |        rdf:predicate ?resourceLinkProperty ;
       |        rdf:object ?referredResource .
       |      ?referredResource ?referredResourcePred ?referredResourceObj ;
       |        knora-base:isDeleted false .
       |    }
       |  }
       |}""".stripMargin

  private val expectedVersionedStandoff =
    """|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
       |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
       |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
       |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
       |
       |CONSTRUCT {
       |  ?resource a knora-base:Resource ;
       |    knora-base:isMainResource true ;
       |    knora-base:attachedToProject ?resourceProject ;
       |    rdfs:label ?label ;
       |    rdf:type ?resourceType ;
       |    knora-base:attachedToUser ?resourceCreator ;
       |    knora-base:hasPermissions ?resourcePermissions ;
       |    knora-base:creationDate ?creationDate ;
       |    knora-base:lastModificationDate ?lastModificationDate ;
       |    knora-base:hasResourceAuthorship ?resourceAuthorship .
       |  ?resource knora-base:isDeleted false .
       |  ?resource knora-base:hasValue ?valueObject ;
       |    ?resourceValueProperty ?valueObject .
       |  ?valueObject ?valueObjectProperty ?valueObjectValue ;
       |    knora-base:valueHasUUID ?currentValueUUID ;
       |    knora-base:hasPermissions ?currentValuePermissions .
       |  ?valueObject knora-base:valueHasStandoff ?standoffNode .
       |  ?standoffNode ?standoffProperty ?standoffValue ;
       |    knora-base:targetHasOriginalXMLID ?targetOriginalXMLID .
       |  ?resource knora-base:hasLinkTo ?referredResource ;
       |    ?resourceLinkProperty ?referredResource .
       |  ?referredResource a knora-base:Resource ;
       |    ?referredResourcePred ?referredResourceObj .
       |} WHERE {
       |  VALUES ?resource { <http://rdfh.ch/0001/resource1> }
       |  {
       |    ?resource rdf:type ?resourceType .
       |    ?resourceType rdfs:subClassOf* knora-base:Resource .
       |  }
       |  ?resource knora-base:attachedToProject ?resourceProject ;
       |    knora-base:attachedToUser ?resourceCreator ;
       |    knora-base:hasPermissions ?resourcePermissions ;
       |    knora-base:creationDate ?creationDate ;
       |    rdfs:label ?label .
       |  ?resource knora-base:isDeleted false .
       |  {
       |    ?resource knora-base:creationDate ?creationDate .
       |    FILTER(?creationDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime>)
       |  }
       |  OPTIONAL { ?resource knora-base:lastModificationDate ?lastModificationDate . }
       |  OPTIONAL { ?resource knora-base:hasResourceAuthorship ?resourceAuthorship . }
       |  OPTIONAL {
       |    ?resource ?resourceValueProperty ?currentValue .
       |    ?resourceValueProperty rdfs:subPropertyOf* knora-base:hasValue .
       |    FILTER NOT EXISTS {
       |      ?currentValue knora-base:deleteDate ?currentValueDeleteDate .
       |      FILTER(?currentValueDeleteDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime>)
       |    }
       |    ?currentValue knora-base:valueHasUUID ?currentValueUUID .
       |    {
       |      ?currentValue knora-base:previousValue* ?valueObject .
       |      ?valueObject knora-base:valueCreationDate ?valueObjectCreationDate .
       |      FILTER(?valueObjectCreationDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime>)
       |    }
       |    FILTER NOT EXISTS {
       |      ?currentValue knora-base:previousValue* ?otherValueObject .
       |      ?otherValueObject knora-base:valueCreationDate ?otherValueObjectCreationDate .
       |      FILTER(?otherValueObjectCreationDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> && ?otherValueObjectCreationDate > ?valueObjectCreationDate)
       |    }
       |    ?currentValue knora-base:hasPermissions ?currentValuePermissions .
       |    {
       |      {
       |        ?valueObject a ?valueObjectType ;
       |          ?valueObjectProperty ?valueObjectValue .
       |        FILTER(?valueObjectProperty != knora-base:valueHasStandoff && ?valueObjectProperty != knora-base:hasPermissions)
       |      } UNION {
       |        ?valueObject knora-base:valueHasStandoff ?standoffNode .
       |        ?standoffNode ?standoffProperty ?standoffValue ;
       |          knora-base:standoffTagHasStartIndex ?startIndex .
       |        OPTIONAL {
       |          ?standoffNode knora-base:standoffTagHasInternalReference ?targetStandoffTag .
       |          ?targetStandoffTag knora-base:standoffTagHasOriginalXMLID ?targetOriginalXMLID .
       |        }
       |        FILTER(?startIndex >= 0)
       |      }
       |    } UNION {
       |      ?valueObject a knora-base:LinkValue ;
       |        rdf:predicate ?resourceLinkProperty ;
       |        rdf:object ?referredResource .
       |      ?referredResource ?referredResourcePred ?referredResourceObj ;
       |        knora-base:isDeleted false .
       |    }
       |  }
       |}""".stripMargin

  private val expectedVersionedStandoffOnlyWithoutStoredXml =
    """|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
       |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
       |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
       |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
       |
       |CONSTRUCT {
       |  ?resource a knora-base:Resource ;
       |    knora-base:isMainResource true ;
       |    knora-base:attachedToProject ?resourceProject ;
       |    rdfs:label ?label ;
       |    rdf:type ?resourceType ;
       |    knora-base:attachedToUser ?resourceCreator ;
       |    knora-base:hasPermissions ?resourcePermissions ;
       |    knora-base:creationDate ?creationDate ;
       |    knora-base:lastModificationDate ?lastModificationDate ;
       |    knora-base:hasResourceAuthorship ?resourceAuthorship .
       |  ?resource knora-base:isDeleted false .
       |  ?resource knora-base:hasValue ?valueObject ;
       |    ?resourceValueProperty ?valueObject .
       |  ?valueObject ?valueObjectProperty ?valueObjectValue ;
       |    knora-base:valueHasUUID ?currentValueUUID ;
       |    knora-base:hasPermissions ?currentValuePermissions .
       |  ?valueObject knora-base:valueHasStandoff ?standoffNode .
       |  ?standoffNode ?standoffProperty ?standoffValue ;
       |    knora-base:targetHasOriginalXMLID ?targetOriginalXMLID .
       |  ?resource knora-base:hasLinkTo ?referredResource ;
       |    ?resourceLinkProperty ?referredResource .
       |  ?referredResource a knora-base:Resource ;
       |    ?referredResourcePred ?referredResourceObj .
       |} WHERE {
       |  VALUES ?resource { <http://rdfh.ch/0001/resource1> }
       |  {
       |    ?resource rdf:type ?resourceType .
       |    ?resourceType rdfs:subClassOf* knora-base:Resource .
       |  }
       |  ?resource knora-base:attachedToProject ?resourceProject ;
       |    knora-base:attachedToUser ?resourceCreator ;
       |    knora-base:hasPermissions ?resourcePermissions ;
       |    knora-base:creationDate ?creationDate ;
       |    rdfs:label ?label .
       |  ?resource knora-base:isDeleted false .
       |  {
       |    ?resource knora-base:creationDate ?creationDate .
       |    FILTER(?creationDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime>)
       |  }
       |  OPTIONAL { ?resource knora-base:lastModificationDate ?lastModificationDate . }
       |  OPTIONAL { ?resource knora-base:hasResourceAuthorship ?resourceAuthorship . }
       |  OPTIONAL {
       |    ?resource ?resourceValueProperty ?currentValue .
       |    ?resourceValueProperty rdfs:subPropertyOf* knora-base:hasValue .
       |    FILTER NOT EXISTS {
       |      ?currentValue knora-base:deleteDate ?currentValueDeleteDate .
       |      FILTER(?currentValueDeleteDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime>)
       |    }
       |    ?currentValue knora-base:valueHasUUID ?currentValueUUID .
       |    {
       |      ?currentValue knora-base:previousValue* ?valueObject .
       |      ?valueObject knora-base:valueCreationDate ?valueObjectCreationDate .
       |      FILTER(?valueObjectCreationDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime>)
       |    }
       |    FILTER NOT EXISTS {
       |      ?currentValue knora-base:previousValue* ?otherValueObject .
       |      ?otherValueObject knora-base:valueCreationDate ?otherValueObjectCreationDate .
       |      FILTER(?otherValueObjectCreationDate <= "2019-08-30T10:36:54.024Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> && ?otherValueObjectCreationDate > ?valueObjectCreationDate)
       |    }
       |    ?currentValue knora-base:hasPermissions ?currentValuePermissions .
       |    {
       |      {
       |        ?valueObject a ?valueObjectType ;
       |          ?valueObjectProperty ?valueObjectValue .
       |        FILTER(?valueObjectProperty != knora-base:valueHasStandoff && ?valueObjectProperty != knora-base:hasPermissions)
       |      } UNION {
       |        {
       |          ?valueObject knora-base:valueHasStandoff ?standoffNode .
       |          FILTER NOT EXISTS { ?valueObject knora-base:valueHasXml [] }
       |        }
       |        ?standoffNode ?standoffProperty ?standoffValue ;
       |          knora-base:standoffTagHasStartIndex ?startIndex .
       |        OPTIONAL {
       |          ?standoffNode knora-base:standoffTagHasInternalReference ?targetStandoffTag .
       |          ?targetStandoffTag knora-base:standoffTagHasOriginalXMLID ?targetOriginalXMLID .
       |        }
       |        FILTER(?startIndex >= 0)
       |      }
       |    } UNION {
       |      ?valueObject a knora-base:LinkValue ;
       |        rdf:predicate ?resourceLinkProperty ;
       |        rdf:object ?referredResource .
       |      ?referredResource ?referredResourcePred ?referredResourceObj ;
       |        knora-base:isDeleted false .
       |    }
       |  }
       |}""".stripMargin
  // @formatter:on

  override val spec: Spec[Any, Nothing] = suite("GetResourcePropertiesAndValuesQuery")(
    test("basic - default params") {
      val actual = render()
      assertTrue(canonical(actual) == canonical(expectedBasic))
    },
    test("preview - preview=true") {
      val actual = render(preview = true)
      assertTrue(canonical(actual) == canonical(expectedPreview))
    },
    test("withDeleted - withDeleted=true") {
      val actual = render(withDeleted = true)
      assertTrue(canonical(actual) == canonical(expectedWithDeleted))
    },
    test("versionDate - maybeVersionDate=Some(versionDate)") {
      val actual = render(maybeVersionDate = Some(versionDate))
      assertTrue(canonical(actual) == canonical(expectedVersionDate))
    },
    test("standoff - standoff=AllStandoff") {
      val actual = render(standoff = StandoffSelection.AllStandoff)
      assertTrue(canonical(actual) == canonical(expectedStandoff))
    },
    test("propertyIri - maybePropertyIri=Some(propertyIri)") {
      val actual = render(maybePropertyIri = Some(propertyIri))
      assertTrue(canonical(actual) == canonical(expectedPropertyIri))
    },
    test("valueUuid - maybeValueUuid=Some(valueUuid)") {
      val actual = render(maybeValueUuid = Some(valueUuid))
      assertTrue(canonical(actual) == canonical(expectedValueUuid))
    },
    test("multipleResources - resourceIris=Seq(resourceIri1, resourceIri2)") {
      val actual = render(resourceIris = Seq(resourceIri1, resourceIri2))
      assertTrue(canonical(actual) == canonical(expectedMultipleResources))
    },
    test("versionDateWithDeleted - withDeleted=true, maybeVersionDate=Some(versionDate)") {
      val actual = render(withDeleted = true, maybeVersionDate = Some(versionDate))
      assertTrue(canonical(actual) == canonical(expectedVersionDateWithDeleted))
    },
    test("versionDateWithUuid - maybeVersionDate=Some(versionDate), maybeValueUuid=Some(valueUuid)") {
      val actual = render(maybeVersionDate = Some(versionDate), maybeValueUuid = Some(valueUuid))
      assertTrue(canonical(actual) == canonical(expectedVersionDateWithUuid))
    },
    test("standoffTagFilter - matches the legacy rendering") {
      val footnoteTagIri = sf.toSmartIri("http://www.knora.org/ontology/standoff#StandoffFootnoteTag")
      val actual         = GetResourcePropertiesAndValuesQuery
        .build(
          resourceIris = Seq(resourceIri1),
          preview = false,
          withDeleted = false,
          standoff = StandoffSelection.AllStandoff,
          standoffTagFilter = Some(footnoteTagIri),
        )
        .sparql
      assertTrue(canonical(actual) == canonical(expectedStandoffTagFilter))
    },
    test("standoffTagFilter - OnlyWithoutStoredXml renders like AllStandoff") {
      val footnoteTagIri                                  = sf.toSmartIri("http://www.knora.org/ontology/standoff#StandoffFootnoteTag")
      def renderWith(standoff: StandoffSelection): String =
        GetResourcePropertiesAndValuesQuery
          .build(
            resourceIris = Seq(resourceIri1),
            preview = false,
            withDeleted = false,
            standoff = standoff,
            standoffTagFilter = Some(footnoteTagIri),
          )
          .sparql
      assertTrue(
        renderWith(StandoffSelection.OnlyWithoutStoredXml) == renderWith(StandoffSelection.AllStandoff),
        !renderWith(StandoffSelection.OnlyWithoutStoredXml).contains("valueHasXml"),
      )
    },
    test("standoffTagFilter - type constraint in SPARQL, no targetHasOriginalXMLID (U-2)") {
      val footnoteTagIri = sf.toSmartIri("http://www.knora.org/ontology/standoff#StandoffFootnoteTag")
      val actual         = GetResourcePropertiesAndValuesQuery
        .build(
          resourceIris = Seq(resourceIri1),
          preview = false,
          withDeleted = false,
          standoff = StandoffSelection.AllStandoff,
          standoffTagFilter = Some(footnoteTagIri),
        )
        .sparql
      assertTrue(
        actual.contains("<http://www.knora.org/ontology/standoff#StandoffFootnoteTag>"),
        !actual.contains("targetHasOriginalXMLID"),
      )
    },
    test("no standoffTagFilter - full standoff loads without type constraint (U-3)") {
      val actual = render(standoff = StandoffSelection.AllStandoff)
      assertTrue(
        actual.contains("targetHasOriginalXMLID"),
        !actual.contains("StandoffFootnoteTag"),
      )
    },
    test("current values - NoStandoff") {
      assertTrue(canonical(render(standoff = StandoffSelection.NoStandoff)) == canonical(expectedBasic))
    },
    test("current values - AllStandoff") {
      assertTrue(canonical(render(standoff = StandoffSelection.AllStandoff)) == canonical(expectedStandoff))
    },
    test("current values - OnlyWithoutStoredXml") {
      assertTrue(
        canonical(render(standoff = StandoffSelection.OnlyWithoutStoredXml)) ==
          canonical(expectedStandoffOnlyWithoutStoredXml),
      )
    },
    test("versioned values - NoStandoff") {
      val actual = render(standoff = StandoffSelection.NoStandoff, maybeVersionDate = Some(versionDate))
      assertTrue(canonical(actual) == canonical(expectedVersionDate))
    },
    test("versioned values - AllStandoff") {
      val actual = render(standoff = StandoffSelection.AllStandoff, maybeVersionDate = Some(versionDate))
      assertTrue(canonical(actual) == canonical(expectedVersionedStandoff))
    },
    test("versioned values - OnlyWithoutStoredXml") {
      val actual = render(standoff = StandoffSelection.OnlyWithoutStoredXml, maybeVersionDate = Some(versionDate))
      assertTrue(canonical(actual) == canonical(expectedVersionedStandoffOnlyWithoutStoredXml))
    },
  )
}
