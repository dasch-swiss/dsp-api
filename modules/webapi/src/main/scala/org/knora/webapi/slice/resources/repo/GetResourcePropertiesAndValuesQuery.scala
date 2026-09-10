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
 */
object GetResourcePropertiesAndValuesQuery {

  private val resource         = Variable("resource")
  private val valueObject      = Variable("valueObject")
  private val currentValue     = Variable("currentValue")
  private val currentValueUUID = Variable("currentValueUUID")
  private val standoffNode     = Variable("standoffNode")

  /**
   * @param resourceIris        the resources to load; must not be empty.
   * @param preview             when true, only the resource metadata is loaded, no values.
   * @param withDeleted         when true, deleted resources and values are included.
   * @param queryAllNonStandoff when true, link values and their targets are loaded.
   * @param queryStandoff       when true, the standoff markup of text values is loaded.
   * @param maybePropertyIri    restricts the values to one property.
   * @param maybeValueUuid      restricts the values to the one with this UUID.
   * @param maybeVersionDate    loads the value versions that were current at this point in time.
   * @param maybeValueIri       restricts the values to the one with this IRI.
   * @param standoffTagFilter   restricts the standoff markup to one standoff tag class.
   */
  def build(
    resourceIris: Seq[IRI],
    preview: Boolean,
    withDeleted: Boolean,
    queryAllNonStandoff: Boolean,
    queryStandoff: Boolean,
    maybePropertyIri: Option[SmartIri] = None,
    maybeValueUuid: Option[UUID] = None,
    maybeVersionDate: Option[Instant] = None,
    maybeValueIri: Option[IRI] = None,
    standoffTagFilter: Option[SmartIri] = None,
  ): Construct =
    Construct(
      sparql"""|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |CONSTRUCT {
               |  ${constructTemplate(withDeleted, queryStandoff, queryAllNonStandoff, standoffTagFilter)}
               |} WHERE {
               |  ${Fragments.values(resource, resourceIris.map(Iri.unsafeFrom))}
               |  ${wherePatterns(
          preview,
          withDeleted,
          queryAllNonStandoff,
          queryStandoff,
          maybePropertyIri,
          maybeValueUuid,
          maybeVersionDate,
          maybeValueIri,
          standoffTagFilter,
        )}
               |}""".render,
    )

  private def constructTemplate(
    withDeleted: Boolean,
    queryStandoff: Boolean,
    queryAllNonStandoff: Boolean,
    standoffTagFilter: Option[SmartIri],
  ): Fragment = {
    val deleted =
      if (withDeleted)
        sparql"""|$resource knora-base:isDeleted ?isDeleted ;
                 |  knora-base:deleteDate ?deletionDate ;
                 |  knora-base:deleteComment ?deleteComment ."""
      else sparql"$resource knora-base:isDeleted false ."

    val standoff = {
      val node = standoffTagFilter.fold(
        sparql"""|$standoffNode ?standoffProperty ?standoffValue ;
                 |  knora-base:targetHasOriginalXMLID ?targetOriginalXMLID .""",
      )(tagIri => sparql"""|$standoffNode a ${Iri.unsafeFrom(tagIri.toIri)} ;
                           |  ?standoffProperty ?standoffValue .""")
      sparql"""|$valueObject knora-base:valueHasStandoff $standoffNode .
               |$node""".when(queryStandoff)
    }

    val links =
      sparql"""|$resource knora-base:hasLinkTo ?referredResource ;
               |  ?resourceLinkProperty ?referredResource .
               |?referredResource a knora-base:Resource ;
               |  ?referredResourcePred ?referredResourceObj .""".when(queryAllNonStandoff)

    sparql"""|$resource a knora-base:Resource ;
             |  knora-base:isMainResource true ;
             |  knora-base:attachedToProject ?resourceProject ;
             |  rdfs:label ?label ;
             |  rdf:type ?resourceType ;
             |  knora-base:attachedToUser ?resourceCreator ;
             |  knora-base:hasPermissions ?resourcePermissions ;
             |  knora-base:creationDate ?creationDate ;
             |  knora-base:lastModificationDate ?lastModificationDate ;
             |  knora-base:hasResourceAuthorship ?resourceAuthorship .
             |$deleted
             |$resource knora-base:hasValue $valueObject ;
             |  ?resourceValueProperty $valueObject .
             |$valueObject ?valueObjectProperty ?valueObjectValue ;
             |  knora-base:valueHasUUID $currentValueUUID ;
             |  knora-base:hasPermissions ?currentValuePermissions .
             |$standoff
             |$links"""
  }

  private def wherePatterns(
    preview: Boolean,
    withDeleted: Boolean,
    queryAllNonStandoff: Boolean,
    queryStandoff: Boolean,
    maybePropertyIri: Option[SmartIri],
    maybeValueUuid: Option[UUID],
    maybeVersionDate: Option[Instant],
    maybeValueIri: Option[IRI],
    standoffTagFilter: Option[SmartIri],
  ): Fragment = {
    val deleted =
      if (withDeleted)
        Fragments.optional(
          sparql"""|$resource knora-base:isDeleted ?isDeleted ;
                   |  knora-base:deleteDate ?deletionDate .
                   |OPTIONAL { $resource knora-base:deleteComment ?deleteComment . }""",
        )
      else sparql"$resource knora-base:isDeleted false ."

    val versionDateFilter = maybeVersionDate.whenSome { vd =>
      sparql"""|{
               |  $resource knora-base:creationDate ?creationDate .
               |  FILTER(?creationDate <= ${Literal.dateTime(vd)})
               |}"""
    }

    val values = Fragments
      .optional(
        valuesBlock(
          withDeleted,
          queryAllNonStandoff,
          queryStandoff,
          maybePropertyIri,
          maybeValueUuid,
          maybeVersionDate,
          maybeValueIri,
          standoffTagFilter,
        ),
      )
      .unless(preview)

    sparql"""|{
             |  $resource rdf:type ?resourceType .
             |  ?resourceType rdfs:subClassOf* knora-base:Resource .
             |}
             |$resource knora-base:attachedToProject ?resourceProject ;
             |  knora-base:attachedToUser ?resourceCreator ;
             |  knora-base:hasPermissions ?resourcePermissions ;
             |  knora-base:creationDate ?creationDate ;
             |  rdfs:label ?label .
             |$deleted
             |$versionDateFilter
             |OPTIONAL { $resource knora-base:lastModificationDate ?lastModificationDate . }
             |OPTIONAL { $resource knora-base:hasResourceAuthorship ?resourceAuthorship . }
             |$values"""
  }

  private def valuesBlock(
    withDeleted: Boolean,
    queryAllNonStandoff: Boolean,
    queryStandoff: Boolean,
    maybePropertyIri: Option[SmartIri],
    maybeValueUuid: Option[UUID],
    maybeVersionDate: Option[Instant],
    maybeValueIri: Option[IRI],
    standoffTagFilter: Option[SmartIri],
  ): Fragment = {
    val valueRetrieval = maybeVersionDate match {
      case Some(versionDate) => versionedValuePatterns(withDeleted, maybePropertyIri, maybeValueUuid, versionDate)
      case None              => currentValuePatterns(maybePropertyIri, maybeValueUuid)
    }

    val valueIriFilter = maybeValueIri.whenSome { vi =>
      sparql"""|{
               |  $valueObject ?valueObjectProperty ?valueObjectValue .
               |  FILTER($valueObject = ${Iri.unsafeFrom(vi)})
               |}"""
    }

    // The value object's type and its non-standoff properties.
    val valueObjectBody =
      sparql"""|$valueObject a ?valueObjectType ;
               |  ?valueObjectProperty ?valueObjectValue .
               |FILTER(?valueObjectProperty != knora-base:valueHasStandoff && ?valueObjectProperty != knora-base:hasPermissions)
               |${sparql"FILTER(?valueObjectProperty != knora-base:valueHasString)".unless(queryAllNonStandoff)}"""

    val standoffBody = {
      val internalReferences = Fragments
        .optional(
          sparql"""|?standoffTag knora-base:standoffTagHasInternalReference ?targetStandoffTag .
                   |?targetStandoffTag knora-base:standoffTagHasOriginalXMLID ?targetOriginalXMLID .""",
        )
        .when(standoffTagFilter.isEmpty)
      val tagConstraint =
        standoffTagFilter.whenSome(tagIri => sparql"$standoffNode a ${Iri.unsafeFrom(tagIri.toIri)} .")
      sparql"""|$valueObject knora-base:valueHasStandoff $standoffNode .
               |$tagConstraint
               |$standoffNode ?standoffProperty ?standoffValue ;
               |  knora-base:standoffTagHasStartIndex ?startIndex .
               |$internalReferences
               |FILTER(?startIndex >= 0)"""
    }

    val linkBody =
      sparql"""|$valueObject a knora-base:LinkValue ;
               |  rdf:predicate ?resourceLinkProperty ;
               |  rdf:object ?referredResource .
               |?referredResource ?referredResourcePred ?referredResourceObj ;
               |  knora-base:isDeleted false ."""

    // The nesting is load-bearing: with both flags the standoff UNION is itself a branch of the
    // link UNION, as the previous builder emitted it.
    val alternatives = (queryStandoff, queryAllNonStandoff) match {
      case (true, true)   => Fragments.union(Fragments.union(valueObjectBody, standoffBody), linkBody)
      case (true, false)  => Fragments.union(valueObjectBody, standoffBody)
      case (false, true)  => Fragments.union(valueObjectBody, linkBody)
      case (false, false) => sparql"""|{
                                      |  $valueObjectBody
                                      |}"""
    }

    sparql"""|$valueRetrieval
             |$valueIriFilter
             |$alternatives"""
  }

  /** Walks the value's version history back to the version that was current at `versionDate`. */
  private def versionedValuePatterns(
    withDeleted: Boolean,
    maybePropertyIri: Option[SmartIri],
    maybeValueUuid: Option[UUID],
    versionDate: Instant,
  ): Fragment = {
    val versionDateLiteral = Literal.dateTime(versionDate)

    val propertyFilter = maybePropertyIri.whenSome { pi =>
      sparql"""|{
               |  $resource ?resourceValueProperty $currentValue .
               |  FILTER(?resourceValueProperty = ${Iri.unsafeFrom(pi.toIri)})
               |}"""
    }

    val deleteFilter = Fragments
      .filterNotExists(
        sparql"""|$currentValue knora-base:deleteDate ?currentValueDeleteDate .
                 |FILTER(?currentValueDeleteDate <= $versionDateLiteral)""",
      )
      .unless(withDeleted)

    val uuidFilter = maybeValueUuid.whenSome { uuid =>
      sparql"""|{
               |  $currentValue knora-base:valueHasUUID $currentValueUUID .
               |  FILTER($currentValueUUID = ${Literal.string(UuidUtil.base64Encode(uuid))})
               |}"""
    }

    // No version of the value was created later than the one selected but still at or before the version date.
    val noLaterVersion = Fragments.filterNotExists(
      sparql"""|$currentValue knora-base:previousValue* ?otherValueObject .
               |?otherValueObject knora-base:valueCreationDate ?otherValueObjectCreationDate .
               |FILTER(?otherValueObjectCreationDate <= $versionDateLiteral && ?otherValueObjectCreationDate > ?valueObjectCreationDate)""",
    )

    sparql"""|$resource ?resourceValueProperty $currentValue .
             |?resourceValueProperty rdfs:subPropertyOf* knora-base:hasValue .
             |$propertyFilter
             |$deleteFilter
             |$currentValue knora-base:valueHasUUID $currentValueUUID .
             |$uuidFilter
             |{
             |  $currentValue knora-base:previousValue* $valueObject .
             |  $valueObject knora-base:valueCreationDate ?valueObjectCreationDate .
             |  FILTER(?valueObjectCreationDate <= $versionDateLiteral)
             |}
             |$noLaterVersion
             |$currentValue knora-base:hasPermissions ?currentValuePermissions ."""
  }

  /** Loads the current version of each value. */
  private def currentValuePatterns(
    maybePropertyIri: Option[SmartIri],
    maybeValueUuid: Option[UUID],
  ): Fragment = {
    val propertyFilter = maybePropertyIri.whenSome { pi =>
      sparql"""|{
               |  $resource ?resourceValueProperty $valueObject .
               |  FILTER(?resourceValueProperty = ${Iri.unsafeFrom(pi.toIri)})
               |}"""
    }

    val uuidPattern = maybeValueUuid.whenSome(uuid =>
      sparql"$valueObject knora-base:valueHasUUID ${Literal.string(UuidUtil.base64Encode(uuid))} .",
    )

    sparql"""|$resource ?resourceValueProperty $valueObject .
             |?resourceValueProperty rdfs:subPropertyOf* knora-base:hasValue .
             |$propertyFilter
             |$uuidPattern
             |$valueObject knora-base:hasPermissions ?currentValuePermissions ."""
  }
}
