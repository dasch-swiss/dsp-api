/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import java.time.Instant
import java.util.UUID

import dsp.valueobjects.UuidUtil
import org.knora.sparqlbuilder.*
import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

/**
 * Builds a CONSTRUCT query that gets the values of all properties of one or more resources.
 *
 * There are two shapes: one that loads the current version of each value, and one that walks the
 * version history back to the version that was current at a given point in time. Each is a
 * complete template of its own.
 */
object GetResourcePropertiesAndValuesQuery {

  /**
   * @param resourceIris      the resources to load; must not be empty.
   * @param preview           when true, only the resource metadata is loaded, no values.
   * @param withDeleted       when true, deleted resources and values are included.
   * @param queryStandoff     when true, the standoff markup of text values is loaded.
   * @param maybePropertyIri  restricts the values to one property.
   * @param maybeValueUuid    restricts the values to the one with this UUID.
   * @param maybeVersionDate  loads the value versions that were current at this point in time.
   * @param standoffTagFilter restricts the standoff markup to one standoff tag class.
   */
  def build(
    resourceIris: Seq[IRI],
    preview: Boolean,
    withDeleted: Boolean,
    queryStandoff: Boolean,
    maybePropertyIri: Option[SmartIri] = None,
    maybeValueUuid: Option[UUID] = None,
    maybeVersionDate: Option[Instant] = None,
    standoffTagFilter: Option[SmartIri] = None,
  ): Construct =
    maybeVersionDate match {
      case None =>
        currentValuesQuery(
          resourceIris,
          preview,
          withDeleted,
          queryStandoff,
          maybePropertyIri,
          maybeValueUuid,
          standoffTagFilter,
        )
      case Some(versionDate) =>
        versionedValuesQuery(
          resourceIris,
          preview,
          withDeleted,
          queryStandoff,
          maybePropertyIri,
          maybeValueUuid,
          versionDate,
          standoffTagFilter,
        )
    }

  /** Loads the current version of each value. */
  private def currentValuesQuery(
    resourceIris: Seq[IRI],
    preview: Boolean,
    withDeleted: Boolean,
    queryStandoff: Boolean,
    maybePropertyIri: Option[SmartIri],
    maybeValueUuid: Option[UUID],
    standoffTagFilter: Option[SmartIri],
  ): Construct = {
    val isDeletedConstruct = isDeletedConstructTriples(withDeleted)
    val standoffConstruct  = standoffConstructTriples(queryStandoff, standoffTagFilter)
    val isDeletedWhere     = isDeletedWherePatterns(withDeleted)
    val valuesOptional     =
      currentValuesOptional(queryStandoff, standoffTagFilter, maybePropertyIri, maybeValueUuid).unless(preview)

    Construct(
      sparql"""|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
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
               |  $isDeletedConstruct
               |  ?resource knora-base:hasValue ?valueObject ;
               |    ?resourceValueProperty ?valueObject .
               |  ?valueObject ?valueObjectProperty ?valueObjectValue ;
               |    knora-base:valueHasUUID ?currentValueUUID ;
               |    knora-base:hasPermissions ?currentValuePermissions .
               |  $standoffConstruct
               |  ?resource knora-base:hasLinkTo ?referredResource ;
               |    ?resourceLinkProperty ?referredResource .
               |  ?referredResource a knora-base:Resource ;
               |    ?referredResourcePred ?referredResourceObj .
               |} WHERE {
               |  ${Fragments.values(Variable("resource"), resourceIris.map(Iri.unsafeFrom))}
               |  {
               |    ?resource rdf:type ?resourceType .
               |    ?resourceType rdfs:subClassOf* knora-base:Resource .
               |  }
               |  ?resource knora-base:attachedToProject ?resourceProject ;
               |    knora-base:attachedToUser ?resourceCreator ;
               |    knora-base:hasPermissions ?resourcePermissions ;
               |    knora-base:creationDate ?creationDate ;
               |    rdfs:label ?label .
               |  $isDeletedWhere
               |  OPTIONAL { ?resource knora-base:lastModificationDate ?lastModificationDate . }
               |  OPTIONAL { ?resource knora-base:hasResourceAuthorship ?resourceAuthorship . }
               |  $valuesOptional
               |}""".render,
    )
  }

  /** Loads the value versions that were current at `versionDate`. */
  private def versionedValuesQuery(
    resourceIris: Seq[IRI],
    preview: Boolean,
    withDeleted: Boolean,
    queryStandoff: Boolean,
    maybePropertyIri: Option[SmartIri],
    maybeValueUuid: Option[UUID],
    versionDate: Instant,
    standoffTagFilter: Option[SmartIri],
  ): Construct = {
    val isDeletedConstruct = isDeletedConstructTriples(withDeleted)
    val standoffConstruct  = standoffConstructTriples(queryStandoff, standoffTagFilter)
    val isDeletedWhere     = isDeletedWherePatterns(withDeleted)
    val versionDateLiteral = Literal.dateTime(versionDate)
    val valuesOptional     = versionedValuesOptional(
      withDeleted,
      queryStandoff,
      standoffTagFilter,
      maybePropertyIri,
      maybeValueUuid,
      versionDate,
    ).unless(preview)

    Construct(
      sparql"""|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
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
               |  $isDeletedConstruct
               |  ?resource knora-base:hasValue ?valueObject ;
               |    ?resourceValueProperty ?valueObject .
               |  ?valueObject ?valueObjectProperty ?valueObjectValue ;
               |    knora-base:valueHasUUID ?currentValueUUID ;
               |    knora-base:hasPermissions ?currentValuePermissions .
               |  $standoffConstruct
               |  ?resource knora-base:hasLinkTo ?referredResource ;
               |    ?resourceLinkProperty ?referredResource .
               |  ?referredResource a knora-base:Resource ;
               |    ?referredResourcePred ?referredResourceObj .
               |} WHERE {
               |  ${Fragments.values(Variable("resource"), resourceIris.map(Iri.unsafeFrom))}
               |  {
               |    ?resource rdf:type ?resourceType .
               |    ?resourceType rdfs:subClassOf* knora-base:Resource .
               |  }
               |  ?resource knora-base:attachedToProject ?resourceProject ;
               |    knora-base:attachedToUser ?resourceCreator ;
               |    knora-base:hasPermissions ?resourcePermissions ;
               |    knora-base:creationDate ?creationDate ;
               |    rdfs:label ?label .
               |  $isDeletedWhere
               |  {
               |    ?resource knora-base:creationDate ?creationDate .
               |    FILTER(?creationDate <= $versionDateLiteral)
               |  }
               |  OPTIONAL { ?resource knora-base:lastModificationDate ?lastModificationDate . }
               |  OPTIONAL { ?resource knora-base:hasResourceAuthorship ?resourceAuthorship . }
               |  $valuesOptional
               |}""".render,
    )
  }

  private def isDeletedConstructTriples(withDeleted: Boolean): Fragment =
    if (withDeleted)
      sparql"""|?resource knora-base:isDeleted ?isDeleted ;
               |  knora-base:deleteDate ?deletionDate ;
               |  knora-base:deleteComment ?deleteComment ."""
    else sparql"?resource knora-base:isDeleted false ."

  private def isDeletedWherePatterns(withDeleted: Boolean): Fragment =
    if (withDeleted)
      sparql"""|OPTIONAL {
               |  ?resource knora-base:isDeleted ?isDeleted ;
               |    knora-base:deleteDate ?deletionDate .
               |  OPTIONAL { ?resource knora-base:deleteComment ?deleteComment . }
               |}"""
    else sparql"?resource knora-base:isDeleted false ."

  private def standoffConstructTriples(queryStandoff: Boolean, standoffTagFilter: Option[SmartIri]): Fragment =
    (queryStandoff, standoffTagFilter) match {
      case (false, _)   => Fragment.empty
      case (true, None) =>
        sparql"""|?valueObject knora-base:valueHasStandoff ?standoffNode .
                 |?standoffNode ?standoffProperty ?standoffValue ;
                 |  knora-base:targetHasOriginalXMLID ?targetOriginalXMLID ."""
      case (true, Some(tagIri)) =>
        sparql"""|?valueObject knora-base:valueHasStandoff ?standoffNode .
                 |?standoffNode a ${Iri.unsafeFrom(tagIri.toIri)} ;
                 |  ?standoffProperty ?standoffValue ."""
    }

  private def currentValuesOptional(
    queryStandoff: Boolean,
    standoffTagFilter: Option[SmartIri],
    maybePropertyIri: Option[SmartIri],
    maybeValueUuid: Option[UUID],
  ): Fragment = {
    val valueBody          = valueBodyAlternatives(queryStandoff, standoffTagFilter)
    val propertyIriPattern = maybePropertyIri.map(pi => Iri.unsafeFrom(pi.toIri)).whenSome { propertyIri =>
      sparql"{ ?resource ?resourceValueProperty ?valueObject . FILTER(?resourceValueProperty = $propertyIri) }"
    }
    val valueUuidPattern = maybeValueUuid.map(uuid => Literal.string(UuidUtil.base64Encode(uuid))).whenSome {
      valueUuid => sparql"?valueObject knora-base:valueHasUUID $valueUuid ."
    }

    sparql"""|OPTIONAL {
             |  ?resource ?resourceValueProperty ?valueObject .
             |  ?resourceValueProperty rdfs:subPropertyOf* knora-base:hasValue .
             |  $propertyIriPattern
             |  $valueUuidPattern
             |  ?valueObject knora-base:hasPermissions ?currentValuePermissions .
             |  $valueBody
             |}"""
  }

  private def versionedValuesOptional(
    withDeleted: Boolean,
    queryStandoff: Boolean,
    standoffTagFilter: Option[SmartIri],
    maybePropertyIri: Option[SmartIri],
    maybeValueUuid: Option[UUID],
    versionDate: Instant,
  ): Fragment = {
    val valueBody               = valueBodyAlternatives(queryStandoff, standoffTagFilter)
    val vd                      = Literal.dateTime(versionDate)
    val notDeletedAtVersionDate = sparql"""|FILTER NOT EXISTS {
                                           |  ?currentValue knora-base:deleteDate ?currentValueDeleteDate .
                                           |  FILTER(?currentValueDeleteDate <= $vd)
                                           |}""".unless(withDeleted)
    val propertyIriPattern = maybePropertyIri.map(pi => Iri.unsafeFrom(pi.toIri)).whenSome { propertyIri =>
      sparql"{ ?resource ?resourceValueProperty ?currentValue . FILTER(?resourceValueProperty = $propertyIri) }"
    }
    val valueUuidPattern =
      maybeValueUuid.map(uuid => Literal.string(UuidUtil.base64Encode(uuid))).whenSome { valueUuid =>
        sparql"{ ?currentValue knora-base:valueHasUUID ?currentValueUUID . FILTER(?currentValueUUID = $valueUuid) }"
      }

    // The second FILTER NOT EXISTS asserts that no version of the value was created later than the
    // one selected but still at or before the version date.
    sparql"""|OPTIONAL {
             |  ?resource ?resourceValueProperty ?currentValue .
             |  ?resourceValueProperty rdfs:subPropertyOf* knora-base:hasValue .
             |  $propertyIriPattern
             |  $notDeletedAtVersionDate
             |  ?currentValue knora-base:valueHasUUID ?currentValueUUID .
             |  $valueUuidPattern
             |  {
             |    ?currentValue knora-base:previousValue* ?valueObject .
             |    ?valueObject knora-base:valueCreationDate ?valueObjectCreationDate .
             |    FILTER(?valueObjectCreationDate <= $vd)
             |  }
             |  FILTER NOT EXISTS {
             |    ?currentValue knora-base:previousValue* ?otherValueObject .
             |    ?otherValueObject knora-base:valueCreationDate ?otherValueObjectCreationDate .
             |    FILTER(?otherValueObjectCreationDate <= $vd && ?otherValueObjectCreationDate > ?valueObjectCreationDate)
             |  }
             |  ?currentValue knora-base:hasPermissions ?currentValuePermissions .
             |  $valueBody
             |}"""
  }

  // The UNION nesting is load-bearing: with standoff, the value/standoff UNION is itself one branch
  // of the link UNION, as the previous builder emitted it. Link values are always queried.
  private def valueBodyAlternatives(queryStandoff: Boolean, standoffTagFilter: Option[SmartIri]): Fragment =
    (queryStandoff, standoffTagFilter) match {
      case (false, _) =>
        sparql"""|{
                 |  ?valueObject a ?valueObjectType ;
                 |    ?valueObjectProperty ?valueObjectValue .
                 |  FILTER(?valueObjectProperty != knora-base:valueHasStandoff && ?valueObjectProperty != knora-base:hasPermissions)
                 |} UNION {
                 |  ?valueObject a knora-base:LinkValue ;
                 |    rdf:predicate ?resourceLinkProperty ;
                 |    rdf:object ?referredResource .
                 |  ?referredResource ?referredResourcePred ?referredResourceObj ;
                 |    knora-base:isDeleted false .
                 |}"""
      case (true, None) =>
        sparql"""|{
                 |  {
                 |    ?valueObject a ?valueObjectType ;
                 |      ?valueObjectProperty ?valueObjectValue .
                 |    FILTER(?valueObjectProperty != knora-base:valueHasStandoff && ?valueObjectProperty != knora-base:hasPermissions)
                 |  } UNION {
                 |    ?valueObject knora-base:valueHasStandoff ?standoffNode .
                 |    ?standoffNode ?standoffProperty ?standoffValue ;
                 |      knora-base:standoffTagHasStartIndex ?startIndex .
                 |    OPTIONAL {
                 |      ?standoffTag knora-base:standoffTagHasInternalReference ?targetStandoffTag .
                 |      ?targetStandoffTag knora-base:standoffTagHasOriginalXMLID ?targetOriginalXMLID .
                 |    }
                 |    FILTER(?startIndex >= 0)
                 |  }
                 |} UNION {
                 |  ?valueObject a knora-base:LinkValue ;
                 |    rdf:predicate ?resourceLinkProperty ;
                 |    rdf:object ?referredResource .
                 |  ?referredResource ?referredResourcePred ?referredResourceObj ;
                 |    knora-base:isDeleted false .
                 |}"""
      case (true, Some(tagIri)) =>
        sparql"""|{
                 |  {
                 |    ?valueObject a ?valueObjectType ;
                 |      ?valueObjectProperty ?valueObjectValue .
                 |    FILTER(?valueObjectProperty != knora-base:valueHasStandoff && ?valueObjectProperty != knora-base:hasPermissions)
                 |  } UNION {
                 |    ?valueObject knora-base:valueHasStandoff ?standoffNode .
                 |    ?standoffNode a ${Iri.unsafeFrom(tagIri.toIri)} .
                 |    ?standoffNode ?standoffProperty ?standoffValue ;
                 |      knora-base:standoffTagHasStartIndex ?startIndex .
                 |    FILTER(?startIndex >= 0)
                 |  }
                 |} UNION {
                 |  ?valueObject a knora-base:LinkValue ;
                 |    rdf:predicate ?resourceLinkProperty ;
                 |    rdf:object ?referredResource .
                 |  ?referredResource ?referredResourcePred ?referredResourceObj ;
                 |    knora-base:isDeleted false .
                 |}"""
    }
}
