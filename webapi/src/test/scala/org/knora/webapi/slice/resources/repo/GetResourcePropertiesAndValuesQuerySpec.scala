/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*

import java.time.Instant
import java.util.UUID

import org.knora.webapi.messages.StringFormatter

object GetResourcePropertiesAndValuesQuerySpec extends ZIOSpecDefault {

  private val sf = StringFormatter.getInitializedTestInstance

  private val resourceIri1 = "http://rdfh.ch/0001/resource1"
  private val resourceIri2 = "http://rdfh.ch/0001/resource2"
  private val propertyIri  = sf.toSmartIri("http://www.knora.org/ontology/0001/anything#hasText")
  private val valueUuid    = UUID.fromString("12345678-1234-1234-1234-123456789012")
  private val versionDate  = Instant.parse("2019-08-30T10:36:54.024Z")
  private val valueIri     = "http://rdfh.ch/0001/resource1/values/value1"

  private def render(
    resourceIris: Seq[String] = Seq(resourceIri1),
    preview: Boolean = false,
    withDeleted: Boolean = false,
    queryAllNonStandoff: Boolean = true,
    queryStandoff: Boolean = false,
    maybePropertyIri: Option[org.knora.webapi.messages.SmartIri] = None,
    maybeValueUuid: Option[UUID] = None,
    maybeVersionDate: Option[Instant] = None,
    maybeValueIri: Option[String] = None,
  ): String =
    GetResourcePropertiesAndValuesQuery.build(
      resourceIris = resourceIris,
      preview = preview,
      withDeleted = withDeleted,
      queryAllNonStandoff = queryAllNonStandoff,
      queryStandoff = queryStandoff,
      maybePropertyIri = maybePropertyIri,
      maybeValueUuid = maybeValueUuid,
      maybeVersionDate = maybeVersionDate,
      maybeValueIri = maybeValueIri,
    )

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
       |    <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate .
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
       |    <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate .
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
       |    <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate .
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
       |    <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate .
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
       |    <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate .
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
       |    <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate .
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
       |    <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate .
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

  private val expectedValueIri =
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
       |    <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate .
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
       |  OPTIONAL { ?resource ?resourceValueProperty ?valueObject .
       |?resourceValueProperty <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>* <http://www.knora.org/ontology/knora-base#hasValue> .
       |?valueObject <http://www.knora.org/ontology/knora-base#hasPermissions> ?currentValuePermissions .
       |{ ?valueObject ?valueObjectProperty ?valueObjectValue .
       |FILTER ( ?valueObject = <http://rdfh.ch/0001/resource1/values/value1> ) }
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
       |    <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate .
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

  private val expectedNoNonStandoff =
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
       |    <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate .
       |  ?resource <http://www.knora.org/ontology/knora-base#isDeleted> false .
       |  ?resource <http://www.knora.org/ontology/knora-base#hasValue> ?valueObject ;
       |    ?resourceValueProperty ?valueObject .
       |  ?valueObject ?valueObjectProperty ?valueObjectValue ;
       |    <http://www.knora.org/ontology/knora-base#valueHasUUID> ?currentValueUUID ;
       |    <http://www.knora.org/ontology/knora-base#hasPermissions> ?currentValuePermissions .
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
       |  OPTIONAL { ?resource ?resourceValueProperty ?valueObject .
       |?resourceValueProperty <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>* <http://www.knora.org/ontology/knora-base#hasValue> .
       |?valueObject <http://www.knora.org/ontology/knora-base#hasPermissions> ?currentValuePermissions .
       |{ ?valueObject a ?valueObjectType ;
       |    ?valueObjectProperty ?valueObjectValue .
       |FILTER ( ( ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#valueHasStandoff> && ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#hasPermissions> ) )
       |FILTER ( ?valueObjectProperty != <http://www.knora.org/ontology/knora-base#valueHasString> ) } }
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
       |    <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate .
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
       |    <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate .
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
  // @formatter:on

  override val spec: Spec[Any, Nothing] = suite("GetResourcePropertiesAndValuesQuery")(
    test("basic - default params") {
      val actual = render()
      assertTrue(actual == expectedBasic)
    },
    test("preview - preview=true") {
      val actual = render(preview = true)
      assertTrue(actual == expectedPreview)
    },
    test("withDeleted - withDeleted=true") {
      val actual = render(withDeleted = true)
      assertTrue(actual == expectedWithDeleted)
    },
    test("versionDate - maybeVersionDate=Some(versionDate)") {
      val actual = render(maybeVersionDate = Some(versionDate))
      assertTrue(actual == expectedVersionDate)
    },
    test("standoff - queryStandoff=true") {
      val actual = render(queryStandoff = true)
      assertTrue(actual == expectedStandoff)
    },
    test("propertyIri - maybePropertyIri=Some(propertyIri)") {
      val actual = render(maybePropertyIri = Some(propertyIri))
      assertTrue(actual == expectedPropertyIri)
    },
    test("valueUuid - maybeValueUuid=Some(valueUuid)") {
      val actual = render(maybeValueUuid = Some(valueUuid))
      assertTrue(actual == expectedValueUuid)
    },
    test("valueIri - maybeValueIri=Some(valueIri)") {
      val actual = render(maybeValueIri = Some(valueIri))
      assertTrue(actual == expectedValueIri)
    },
    test("multipleResources - resourceIris=Seq(resourceIri1, resourceIri2)") {
      val actual = render(resourceIris = Seq(resourceIri1, resourceIri2))
      assertTrue(actual == expectedMultipleResources)
    },
    test("noNonStandoff - queryAllNonStandoff=false") {
      val actual = render(queryAllNonStandoff = false)
      assertTrue(actual == expectedNoNonStandoff)
    },
    test("versionDateWithDeleted - withDeleted=true, maybeVersionDate=Some(versionDate)") {
      val actual = render(withDeleted = true, maybeVersionDate = Some(versionDate))
      assertTrue(actual == expectedVersionDateWithDeleted)
    },
    test("versionDateWithUuid - maybeVersionDate=Some(versionDate), maybeValueUuid=Some(valueUuid)") {
      val actual = render(maybeVersionDate = Some(versionDate), maybeValueUuid = Some(valueUuid))
      assertTrue(actual == expectedVersionDateWithUuid)
    },
  )
}
