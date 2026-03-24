/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import java.time.Instant
import java.util.UUID

import dsp.valueobjects.UuidUtil
import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

/**
 * Builds a CONSTRUCT query that gets the values of all properties of one or more resources.
 *
 * Uses rdf4j SparqlBuilder for triple/graph patterns and string interpolation for the
 * top-level CONSTRUCT assembly (since SparqlBuilder does not support VALUES blocks).
 */
object GetResourcePropertiesAndValuesQuery extends QueryBuilderHelper {

  // Variables
  private val resource                = variable("resource")
  private val resourceType            = variable("resourceType")
  private val resourceProject         = variable("resourceProject")
  private val label                   = variable("label")
  private val resourceCreator         = variable("resourceCreator")
  private val resourcePermissions     = variable("resourcePermissions")
  private val creationDate            = variable("creationDate")
  private val lastModificationDate    = variable("lastModificationDate")
  private val isDeleted               = variable("isDeleted")
  private val deletionDate            = variable("deletionDate")
  private val deleteComment           = variable("deleteComment")
  private val valueObject             = variable("valueObject")
  private val resourceValueProperty   = variable("resourceValueProperty")
  private val valueObjectProperty     = variable("valueObjectProperty")
  private val valueObjectValue        = variable("valueObjectValue")
  private val valueObjectType         = variable("valueObjectType")
  private val currentValueUUID        = variable("currentValueUUID")
  private val currentValuePermissions = variable("currentValuePermissions")
  private val standoffNode            = variable("standoffNode")
  private val standoffProperty        = variable("standoffProperty")
  private val standoffValue           = variable("standoffValue")
  private val targetOriginalXMLID     = variable("targetOriginalXMLID")
  private val referredResource        = variable("referredResource")
  private val resourceLinkProperty    = variable("resourceLinkProperty")
  private val referredResourcePred    = variable("referredResourcePred")
  private val referredResourceObj     = variable("referredResourceObj")
  private val currentValue            = variable("currentValue")
  private val valueObjectCreationDate = variable("valueObjectCreationDate")
  private val startIndex              = variable("startIndex")
  private val standoffTag             = variable("standoffTag")
  private val targetStandoffTag       = variable("targetStandoffTag")

  // Property paths

  private val subClassOfPath    = zeroOrMore(RDFS.SUBCLASSOF)
  private val subPropertyOfPath = zeroOrMore(RDFS.SUBPROPERTYOF)
  private val previousValuePath = zeroOrMore(KnoraBase.previousValue)

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
  ): String = {
    val valuesClause = resourceIris.map(iri => s"<$iri>").mkString(" ")

    val constructPatterns = buildConstructPatterns(withDeleted, queryStandoff, queryAllNonStandoff)
    val wherePatterns     = buildWherePatterns(
      preview,
      withDeleted,
      queryAllNonStandoff,
      queryStandoff,
      maybePropertyIri,
      maybeValueUuid,
      maybeVersionDate,
      maybeValueIri,
    )

    // Assemble with string interpolation because SparqlBuilder does not support VALUES blocks.
    s"""PREFIX xsd: <${XSD.NAMESPACE}>
       |PREFIX rdf: <${RDF.NAMESPACE}>
       |PREFIX rdfs: <${RDFS.NAMESPACE}>
       |PREFIX knora-base: <${KnoraBase.NS.getName}>
       |
       |CONSTRUCT {
       |${constructPatterns.map(p => s"  ${p.getQueryString}").mkString("\n")}
       |} WHERE {
       |  VALUES ?resource { $valuesClause }
       |${wherePatterns.map(p => s"  ${p.getQueryString}").mkString("\n")}
       |}""".stripMargin
  }

  private def buildConstructPatterns(
    withDeleted: Boolean,
    queryStandoff: Boolean,
    queryAllNonStandoff: Boolean,
  ): Seq[TriplePattern] = {
    val resourceMetadata = Seq(
      resource
        .isA(KnoraBase.Resource)
        .andHas(KnoraBase.isMainResource, Rdf.literalOf(true))
        .andHas(KnoraBase.attachedToProject, resourceProject)
        .andHas(RDFS.LABEL, label)
        .andHas(RDF.TYPE, resourceType)
        .andHas(KnoraBase.attachedToUser, resourceCreator)
        .andHas(KnoraBase.hasPermissions, resourcePermissions)
        .andHas(KnoraBase.creationDate, creationDate)
        .andHas(KnoraBase.lastModificationDate, lastModificationDate),
    )

    val deletedPatterns =
      if (!withDeleted) Seq(resource.has(KnoraBase.isDeleted, Rdf.literalOf(false)))
      else
        Seq(
          resource
            .has(KnoraBase.isDeleted, isDeleted)
            .andHas(KnoraBase.deleteDate, deletionDate)
            .andHas(KnoraBase.deleteComment, deleteComment),
        )

    val valuePatterns = Seq(
      resource.has(KnoraBase.hasValue, valueObject).andHas(resourceValueProperty, valueObject),
      valueObject
        .has(valueObjectProperty, valueObjectValue)
        .andHas(KnoraBase.valueHasUUID, currentValueUUID)
        .andHas(KnoraBase.hasPermissions, currentValuePermissions),
    )

    val standoffPatterns =
      if (queryStandoff)
        Seq(
          valueObject.has(KnoraBase.valueHasStandoff, standoffNode),
          standoffNode
            .has(standoffProperty, standoffValue)
            .andHas(KnoraBase.targetHasOriginalXMLID, targetOriginalXMLID),
        )
      else Seq.empty

    val linkPatterns =
      if (queryAllNonStandoff)
        Seq(
          resource.has(KnoraBase.hasLinkTo, referredResource).andHas(resourceLinkProperty, referredResource),
          referredResource.isA(KnoraBase.Resource).andHas(referredResourcePred, referredResourceObj),
        )
      else Seq.empty

    resourceMetadata ++ deletedPatterns ++ valuePatterns ++ standoffPatterns ++ linkPatterns
  }

  private def buildWherePatterns(
    preview: Boolean,
    withDeleted: Boolean,
    queryAllNonStandoff: Boolean,
    queryStandoff: Boolean,
    maybePropertyIri: Option[SmartIri],
    maybeValueUuid: Option[UUID],
    maybeVersionDate: Option[Instant],
    maybeValueIri: Option[IRI],
  ): Seq[GraphPattern] = {
    val resourceTypePattern: GraphPattern =
      resource.has(RDF.TYPE, resourceType).and(resourceType.has(subClassOfPath, KnoraBase.Resource))

    val resourceMetadataPattern: GraphPattern = resource
      .has(KnoraBase.attachedToProject, resourceProject)
      .andHas(KnoraBase.attachedToUser, resourceCreator)
      .andHas(KnoraBase.hasPermissions, resourcePermissions)
      .andHas(KnoraBase.creationDate, creationDate)
      .andHas(RDFS.LABEL, label)

    val deletedWherePattern: GraphPattern =
      if (!withDeleted)
        resource.has(KnoraBase.isDeleted, Rdf.literalOf(false))
      else
        resource
          .has(KnoraBase.isDeleted, isDeleted)
          .andHas(KnoraBase.deleteDate, deletionDate)
          .optional()
          .and(resource.has(KnoraBase.deleteComment, deleteComment).optional())

    val versionDateFilter: Seq[GraphPattern] = maybeVersionDate.toSeq.map { vd =>
      resource
        .has(KnoraBase.creationDate, creationDate)
        .filter(Expressions.lte(creationDate, Rdf.literalOfType(vd.toString, XSD.DATETIME)))
    }

    val lastModDateOptional: GraphPattern =
      resource.has(KnoraBase.lastModificationDate, lastModificationDate).optional()

    val valuesOptionalBlock: Seq[GraphPattern] =
      if (!preview)
        Seq(
          buildValuesOptionalBlock(
            withDeleted,
            queryAllNonStandoff,
            queryStandoff,
            maybePropertyIri,
            maybeValueUuid,
            maybeVersionDate,
            maybeValueIri,
          ),
        )
      else Seq.empty

    Seq(resourceTypePattern, resourceMetadataPattern, deletedWherePattern) ++
      versionDateFilter ++
      Seq(lastModDateOptional) ++
      valuesOptionalBlock
  }

  private def buildValuesOptionalBlock(
    withDeleted: Boolean,
    queryAllNonStandoff: Boolean,
    queryStandoff: Boolean,
    maybePropertyIri: Option[SmartIri],
    maybeValueUuid: Option[UUID],
    maybeVersionDate: Option[Instant],
    maybeValueIri: Option[IRI],
  ): GraphPattern = {
    val valueRetrievalPatterns: GraphPattern = maybeVersionDate match {
      case Some(versionDate) =>
        buildVersionDateValuePatterns(withDeleted, maybePropertyIri, maybeValueUuid, versionDate)
      case None => buildCurrentValuePatterns(maybePropertyIri, maybeValueUuid)
    }

    val valueIriFilter: Seq[GraphPattern] = maybeValueIri.toSeq.map { vi =>
      valueObject
        .has(valueObjectProperty, valueObjectValue)
        .filter(Expressions.equals(valueObject, Rdf.iri(vi)))
    }

    // Value object type + properties block
    val valueObjectBlock: GraphPattern = valueObject
      .isA(valueObjectType)
      .andHas(valueObjectProperty, valueObjectValue)
      .filter(
        Expressions.and(
          Expressions.notEquals(valueObjectProperty, KnoraBase.valueHasStandoff),
          Expressions.notEquals(valueObjectProperty, KnoraBase.hasPermissions),
        ),
      )

    val valueObjectBlockWithFilter: GraphPattern =
      if (!queryAllNonStandoff)
        valueObjectBlock.filter(Expressions.notEquals(valueObjectProperty, KnoraBase.valueHasString))
      else
        valueObjectBlock

    // Standoff UNION
    val standoffUnion: Seq[GraphPattern] =
      if (queryStandoff) {
        val standoffPattern = valueObject
          .has(KnoraBase.valueHasStandoff, standoffNode)
          .and(
            standoffNode
              .has(standoffProperty, standoffValue)
              .andHas(KnoraBase.standoffTagHasStartIndex, startIndex),
          )
          .and(
            standoffTag
              .has(KnoraBase.standoffTagHasInternalReference, targetStandoffTag)
              .and(targetStandoffTag.has(KnoraBase.standoffTagHasOriginalXMLID, targetOriginalXMLID))
              .optional(),
          )
          .filter(Expressions.gte(startIndex, Rdf.literalOf(0)))

        Seq(GraphPatterns.union(valueObjectBlockWithFilter, standoffPattern))
      } else Seq(valueObjectBlockWithFilter)

    // Link UNION
    val linkUnion: Seq[GraphPattern] =
      if (queryAllNonStandoff) {
        val linkPattern = valueObject
          .isA(KnoraBase.linkValue)
          .andHas(RDF.PREDICATE, resourceLinkProperty)
          .andHas(RDF.OBJECT, referredResource)
          .and(
            referredResource
              .has(referredResourcePred, referredResourceObj)
              .andHas(KnoraBase.isDeleted, Rdf.literalOf(false)),
          )

        if (queryStandoff)
          // standoffUnion already contains a UNION with valueObjectBlock, add linkPattern to it
          Seq(GraphPatterns.union(standoffUnion.head, linkPattern))
        else
          Seq(GraphPatterns.union(valueObjectBlockWithFilter, linkPattern))
      } else standoffUnion

    val allInnerPatterns = valueRetrievalPatterns +: valueIriFilter ++: linkUnion
    allInnerPatterns.reduce((a, b) => a.and(b)).optional()
  }

  private def buildVersionDateValuePatterns(
    withDeleted: Boolean,
    maybePropertyIri: Option[SmartIri],
    maybeValueUuid: Option[UUID],
    versionDate: Instant,
  ): GraphPattern = {
    val versionDateLiteral = Rdf.literalOfType(versionDate.toString, XSD.DATETIME)

    val basePatterns: GraphPattern = resource
      .has(resourceValueProperty, currentValue)
      .and(resourceValueProperty.has(subPropertyOfPath, KnoraBase.hasValue))

    val propertyFilter: Seq[GraphPattern] = maybePropertyIri.toSeq.map { pi =>
      resource
        .has(resourceValueProperty, currentValue)
        .filter(Expressions.equals(resourceValueProperty, Rdf.iri(pi.toIri)))
    }

    val deleteFilter: Seq[GraphPattern] =
      if (!withDeleted) {
        val currentValueDeleteDate = variable("currentValueDeleteDate")
        Seq(
          GraphPatterns.filterNotExists(
            currentValue
              .has(KnoraBase.deleteDate, currentValueDeleteDate)
              .filter(Expressions.lte(currentValueDeleteDate, versionDateLiteral)),
          ),
        )
      } else Seq.empty

    val uuidPattern: GraphPattern = currentValue.has(KnoraBase.valueHasUUID, currentValueUUID)

    val uuidFilter: Seq[GraphPattern] = maybeValueUuid.toSeq.map { uuid =>
      currentValue
        .has(KnoraBase.valueHasUUID, currentValueUUID)
        .filter(Expressions.equals(currentValueUUID, Rdf.literalOf(UuidUtil.base64Encode(uuid))))
    }

    val otherValueObject             = variable("otherValueObject")
    val otherValueObjectCreationDate = variable("otherValueObjectCreationDate")

    val historyTraversal: GraphPattern = currentValue
      .has(previousValuePath, valueObject)
      .and(valueObject.has(KnoraBase.valueCreationDate, valueObjectCreationDate))
      .filter(Expressions.lte(valueObjectCreationDate, versionDateLiteral))

    val moreRecentFilter: GraphPattern = GraphPatterns.filterNotExists(
      currentValue
        .has(previousValuePath, otherValueObject)
        .and(otherValueObject.has(KnoraBase.valueCreationDate, otherValueObjectCreationDate))
        .filter(
          Expressions.and(
            Expressions.lte(otherValueObjectCreationDate, versionDateLiteral),
            Expressions.gt(otherValueObjectCreationDate, valueObjectCreationDate),
          ),
        ),
    )

    val permissionsPattern: GraphPattern = currentValue.has(KnoraBase.hasPermissions, currentValuePermissions)

    val allPatterns: Seq[GraphPattern] =
      Seq(basePatterns) ++ propertyFilter ++ deleteFilter ++ Seq(uuidPattern) ++ uuidFilter ++
        Seq(historyTraversal, moreRecentFilter, permissionsPattern)

    allPatterns.reduce((a, b) => a.and(b))
  }

  private def buildCurrentValuePatterns(
    maybePropertyIri: Option[SmartIri],
    maybeValueUuid: Option[UUID],
  ): GraphPattern = {
    val basePatterns: GraphPattern = resource
      .has(resourceValueProperty, valueObject)
      .and(resourceValueProperty.has(subPropertyOfPath, KnoraBase.hasValue))

    val propertyFilter: Seq[GraphPattern] = maybePropertyIri.toSeq.map { pi =>
      resource
        .has(resourceValueProperty, valueObject)
        .filter(Expressions.equals(resourceValueProperty, Rdf.iri(pi.toIri)))
    }

    val uuidPattern: Seq[GraphPattern] = maybeValueUuid.toSeq.map { uuid =>
      valueObject.has(KnoraBase.valueHasUUID, Rdf.literalOf(UuidUtil.base64Encode(uuid)))
    }

    val permissionsPattern: GraphPattern = valueObject.has(KnoraBase.hasPermissions, currentValuePermissions)

    val allPatterns: Seq[GraphPattern] = Seq(basePatterns) ++ propertyFilter ++ uuidPattern ++ Seq(permissionsPattern)
    allPatterns.reduce((a, b) => a.and(b))
  }
}
