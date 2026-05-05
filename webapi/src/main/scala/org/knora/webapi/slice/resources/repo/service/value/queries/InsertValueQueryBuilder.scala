/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service.value.queries

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.constraint.Bind
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.constraint.SparqlFunction
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.Variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.literalOf
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.literalOfType
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfObject
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfPredicate
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfSubject

import java.time.Instant
import java.util.UUID
import scala.util.chaining.scalaUtilChainingOps

import dsp.valueobjects.UuidUtil
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffTagAttributeV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValueContentV2
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object InsertValueQueryBuilder extends QueryBuilderHelper {

  def createValueQuery(
    dataNamedGraph: InternalIri,
    resourceIri: ResourceIri,
    propertyIri: SmartIri,
    newValueIri: ValueIri,
    newUuidOrCurrentIri: Either[UUID, ValueIri],
    valueInitial: ValueContentV2,
    linkUpdates: Seq[SparqlTemplateLinkUpdate],
    valueCreator: InternalIri,
    valuePermissions: String,
    creationDate: Instant,
  ): Update = {
    val value = valueInitial.toOntologySchema(InternalSchema)

    val dataGraphVar = variable("dataNamedGraph")
    val resourceVar  = variable("resource")
    val currentVar   = variable("currentValue")

    val resource = toRdfIri(resourceIri)
    val property = toRdfIri(propertyIri)

    val currentValue     = newUuidOrCurrentIri.toOption
    val currentVarOpt    = currentValue.map(_ => currentVar)
    val currentValueUUID = variable("currentValueUuid")
    val newValueUUID     = newUuidOrCurrentIri.map(_ => currentValueUUID)

    val resourceLastModDate = variable("resourceLastModificationDate")
    val nextOrder           = variable("nextOrder")

    // Build delete patterns
    val deletePatterns = buildDeletePatterns(
      resourceVar,
      property,
      resourceLastModDate,
      linkUpdates,
      currentVarOpt,
      currentValueUUID,
    )

    // Build insert patterns
    val insertPatterns = buildInsertPatterns(
      resource,
      resourceVar,
      property,
      newValueIri,
      newValueUUID,
      value,
      linkUpdates,
      valueCreator,
      valuePermissions,
      creationDate,
      nextOrder,
      currentVarOpt,
    )

    // Build where clause
    val wherePatterns = buildWhereClause(
      dataNamedGraph.value,
      dataGraphVar,
      resourceIri.value,
      resourceVar,
      value,
      linkUpdates,
      resourceLastModDate,
      nextOrder,
      resourceIri,
      propertyIri.toIri,
      currentVar,
      currentValue,
      currentValueUUID,
    )

    val query = Queries
      .MODIFY()
      .from(dataGraphVar)
      .delete(deletePatterns: _*)
      .into(dataGraphVar)
      .insert(insertPatterns: _*)
      .where(wherePatterns: _*)
      .prefix(KB.NS, RDF.NS, RDFS.NS, XSD.NS, OWL.NS)

    Update(query.getQueryString())
  }

  private def buildDeletePatterns(
    resource: Variable,
    property: rdf.Iri,
    resourceLastModDate: Variable,
    linkUpdates: Seq[SparqlTemplateLinkUpdate],
    currentValue: Option[Variable],
    currentValueUUID: Variable,
  ): List[TriplePattern] = {
    val resourceModPattern = resource.has(KB.lastModificationDate, resourceLastModDate)

    val currentValuePatterns = currentValue.toList.flatMap { currentValue =>
      List(
        resource.has(property, currentValue),
        currentValue.has(KB.valueHasUUID, currentValueUUID),
        currentValue.has(KB.hasPermissions, variable("currentValuePermissions")),
      )
    }

    val linkValueDeletePatterns = linkUpdates.zipWithIndex.flatMap { case (linkUpdate, index) =>
      val deleteDirectLink = Option.when(linkUpdate.deleteDirectLink) {
        resource.has(toRdfIri(linkUpdate.linkPropertyIri), toRdfIri(linkUpdate.linkTargetIri))
      }

      val linkValueExists = Option.when(linkUpdate.linkValueExists) {
        val linkValue            = variable(s"linkValue$index")
        val linkValueUUID        = variable(s"linkValueUUID$index")
        val linkValuePermissions = variable(s"linkValuePermissions$index")

        List(
          resource.has(Rdf.iri(linkUpdate.linkPropertyIri.toString + "Value"), linkValue),
          linkValue.has(KB.valueHasUUID, linkValueUUID),
          linkValue.has(KB.hasPermissions, linkValuePermissions),
        )
      }

      deleteDirectLink.toList ++ linkValueExists.toList.flatten
    }.toList

    resourceModPattern :: currentValuePatterns ::: linkValueDeletePatterns
  }

  private def buildInsertPatterns(
    resource: rdf.Iri,
    resourceVar: Variable,
    property: rdf.Iri,
    valueIri: ValueIri,
    valueUUID: Either[UUID, Variable],
    value: ValueContentV2,
    linkUpdates: Seq[SparqlTemplateLinkUpdate],
    valueCreator: InternalIri,
    valuePermissions: String,
    creationDate: Instant,
    nextOrder: Variable,
    currentValue: Option[Variable],
  ): List[TriplePattern] = {
    // Resource modification date
    val resourceModPattern =
      resourceVar.has(KB.lastModificationDate, literalOfType(creationDate.toString, XSD.DATETIME))

    // Basic value pattern
    val valueRdfIri      = toRdfIri(valueIri)
    val baseValuePattern = valueRdfIri
      .isA(toRdfIri(value.valueType))
      .andHas(KB.isDeleted, literalOf(false))
      .andHas(KB.valueHasString, literalOf(value.valueHasString))

    val baseValuePatternWithUUID =
      valueUUID.fold(
        uuid => baseValuePattern.andHas(KB.valueHasUUID, literalOf(UuidUtil.base64Encode(uuid))),
        variable => baseValuePattern.andHas(KB.valueHasUUID, variable),
      )

    val base2 = valueRdfIri
      .hasOptional(KB.valueHasComment, value.comment.map(literalOf))
      .fold(valueRdfIri.has(KB.attachedToUser, toRdfIri(valueCreator)))(
        _.andHas(KB.attachedToUser, toRdfIri(valueCreator)),
      )
      .andHas(KB.hasPermissions, literalOf(valuePermissions))
      .andHas(KB.valueHasOrder, nextOrder)
      .andHas(KB.valueCreationDate, literalOfType(creationDate.toString, XSD.DATETIME))

    // Type-specific patterns
    val typeSpecificPatterns = buildTypeSpecificPatterns(valueIri, value)

    // Link patterns
    val linkPatterns = buildLinkPatterns(resource, linkUpdates, creationDate, valueCreator)

    // Resource to value link
    val resourceValuePattern = resource.has(property, valueRdfIri)
    val previousValuePattern = currentValue.toList.map(valueRdfIri.has(KB.previousValue, _))

    List(
      List(resourceModPattern),
      List(baseValuePatternWithUUID),
      previousValuePattern,
      typeSpecificPatterns,
      List(base2),
      linkPatterns,
      List(resourceValuePattern),
    ).fold(List[TriplePattern]())(_ ::: _)
  }

  private def buildTypeSpecificPatterns(
    valueIri: ValueIri,
    value: ValueContentV2,
  ): List[TriplePattern] = {
    import org.knora.webapi.messages.v2.responder.valuemessages.*

    val valueRdfIri = toRdfIri(valueIri)
    value match {
      case textValue: TextValueContentV2 =>
        buildTextValuePatterns(valueIri, textValue)
      case intValue: IntegerValueContentV2 =>
        List(valueRdfIri.has(KB.valueHasInteger, literalOf(intValue.valueHasInteger)))
      case decimalValue: DecimalValueContentV2 =>
        List(valueRdfIri.has(KB.valueHasDecimal, literalOfType(decimalValue.valueHasDecimal.toString, XSD.DECIMAL)))
      case booleanValue: BooleanValueContentV2 =>
        List(valueRdfIri.has(KB.valueHasBoolean, literalOf(booleanValue.valueHasBoolean)))
      case uriValue: UriValueContentV2 =>
        List(valueRdfIri.has(KB.valueHasUri, literalOfType(uriValue.valueHasUri, XSD.ANYURI)))
      case dateValue: DateValueContentV2 =>
        buildDateValuePatterns(valueIri, dateValue)
      case colorValue: ColorValueContentV2 =>
        List(valueRdfIri.has(KB.valueHasColor, literalOf(colorValue.valueHasColor)))
      case geometryValue: GeomValueContentV2 =>
        List(valueRdfIri.has(KB.valueHasGeometry, literalOf(geometryValue.valueHasGeometry)))
      case fileValue: FileValueContentV2 =>
        buildFileValuePatterns(valueIri, fileValue)
      case listValue: HierarchicalListValueContentV2 =>
        List(valueRdfIri.has(KB.valueHasListNode, Rdf.iri(listValue.valueHasListNode)))
      case intervalValue: IntervalValueContentV2 =>
        List(
          valueRdfIri
            .has(KB.valueHasIntervalStart, literalOfType(intervalValue.valueHasIntervalStart.toString, XSD.DECIMAL))
            .andHas(KB.valueHasIntervalEnd, literalOfType(intervalValue.valueHasIntervalEnd.toString, XSD.DECIMAL)),
        )
      case timeValue: TimeValueContentV2 =>
        List(valueRdfIri.has(KB.valueHasTimeStamp, literalOfType(timeValue.valueHasTimeStamp.toString, XSD.DATETIME)))
      case geonameValue: GeonameValueContentV2 =>
        List(valueRdfIri.has(KB.valueHasGeonameCode, literalOf(geonameValue.valueHasGeonameCode)))
      case _ => List.empty
    }
  }

  private def buildTextValuePatterns(
    valueIri: ValueIri,
    textValue: org.knora.webapi.messages.v2.responder.valuemessages.TextValueContentV2,
  ): List[TriplePattern] = {
    val valueRdfIri     = toRdfIri(valueIri)
    val languagePattern = textValue.valueHasLanguage.toList.map { lang =>
      valueRdfIri.has(KB.valueHasLanguage, literalOf(lang))
    }

    if (textValue.standoff.nonEmpty) {
      val mappingPattern = textValue.mappingIri.map { mappingIri =>
        valueRdfIri.has(KB.valueHasMapping, Rdf.iri(mappingIri))
      }.toList

      val maxIndexPattern = textValue.computedMaxStandoffStartIndex.map { maxIndex =>
        valueRdfIri.has(KB.valueHasMaxStandoffStartIndex, literalOf(maxIndex))
      }.toList

      val standoffPatterns = buildStandoffPatterns(valueIri, textValue)

      languagePattern ::: mappingPattern ::: maxIndexPattern ::: standoffPatterns
    } else {
      languagePattern
    }
  }

  private def standoffAttributeToRdfValue(
    attr: org.knora.webapi.messages.v2.responder.standoffmessages.StandoffTagAttributeV2,
  ): RdfObject = {
    import org.knora.webapi.messages.v2.responder.standoffmessages._

    attr match {
      case iriAttr: StandoffTagIriAttributeV2           => Rdf.iri(iriAttr.value)
      case uri: StandoffTagUriAttributeV2               => literalOfType(uri.value, XSD.ANYURI)
      case ref: StandoffTagInternalReferenceAttributeV2 => Rdf.iri(ref.value)
      case str: StandoffTagStringAttributeV2            => literalOf(str.value)
      case int: StandoffTagIntegerAttributeV2           => literalOf(int.value)
      case dec: StandoffTagDecimalAttributeV2           => literalOfType(dec.value.toString, XSD.DECIMAL)
      case bool: StandoffTagBooleanAttributeV2          => literalOf(bool.value)
      case time: StandoffTagTimeAttributeV2             => literalOfType(time.value.toString, XSD.DATETIME)
    }
  }

  private def buildStandoffPatterns(
    valueIri: ValueIri,
    textValue: org.knora.webapi.messages.v2.responder.valuemessages.TextValueContentV2,
  ): List[TriplePattern] = {
    import dsp.valueobjects.UuidUtil

    val valueRdfIri = toRdfIri(valueIri)
    if (textValue.standoff.nonEmpty) {
      // Follow Twirl template lines 100-161: Create standoff nodes for each standoff tag
      val standoffTags = textValue.prepareForSparqlInsert(valueIri)

      standoffTags.flatMap { createStandoff =>
        val standoffTagIri = Rdf.iri(createStandoff.standoffTagInstanceIri)

        // Base pattern: valueHasStandoff connection
        val valueHasStandoffPattern = valueRdfIri.has(KB.valueHasStandoff, standoffTagIri)

        // Build the standoff tag properties
        val standoffPattern = standoffTagIri
          .hasOptional(KB.standoffTagHasStartParent, createStandoff.startParentIri.map(Rdf.iri))
          .andHasOptional(
            standoffTagIri,
            KB.standoffTagHasOriginalXMLID,
            createStandoff.standoffNode.originalXMLID.map(literalOf),
          )
          .andHasOptional(
            standoffTagIri,
            KB.standoffTagHasEndIndex,
            createStandoff.standoffNode.endIndex.map(i => literalOf(i)),
          )
          .andHasOptional(standoffTagIri, KB.standoffTagHasEndParent, createStandoff.endParentIri.map(Rdf.iri))
          .andHasAllFn(createStandoff.standoffNode.attributes)((attr: StandoffTagAttributeV2) =>
            (standoffTagIri, toRdfIri(attr.standoffPropertyIri), standoffAttributeToRdfValue(attr)),
          )
          .andHas(standoffTagIri, KB.standoffTagHasStartIndex, literalOf(createStandoff.standoffNode.startIndex))
          .andHas(KB.standoffTagHasUUID, literalOf(UuidUtil.base64Encode(createStandoff.standoffNode.uuid)))
          .andHas(KB.standoffTagHasStart, literalOf(createStandoff.standoffNode.startPosition))
          .andHas(KB.standoffTagHasEnd, literalOf(createStandoff.standoffNode.endPosition))
          .andHas(RDF.TYPE, toRdfIri(createStandoff.standoffNode.standoffTagClassIri))

        List(valueHasStandoffPattern, standoffPattern)
      }.toList
    } else {
      List.empty
    }
  }

  private def buildDateValuePatterns(
    valueIri: ValueIri,
    dateValue: org.knora.webapi.messages.v2.responder.valuemessages.DateValueContentV2,
  ): List[TriplePattern] =
    List(
      toRdfIri(valueIri)
        .has(KB.valueHasStartJDN, literalOf(dateValue.valueHasStartJDN))
        .andHas(KB.valueHasEndJDN, literalOf(dateValue.valueHasEndJDN))
        .andHas(KB.valueHasStartPrecision, literalOf(dateValue.valueHasStartPrecision.toString))
        .andHas(KB.valueHasEndPrecision, literalOf(dateValue.valueHasEndPrecision.toString))
        .andHas(KB.valueHasCalendar, literalOf(dateValue.valueHasCalendar.toString)),
    )

  private def buildFileValuePatterns(
    valueIri: ValueIri,
    fileValue: org.knora.webapi.messages.v2.responder.valuemessages.FileValueContentV2,
  ): List[TriplePattern] = {
    val valueRdfIri = toRdfIri(valueIri)
    val basePattern = valueRdfIri
      .has(KB.internalFilename, literalOf(fileValue.fileValue.internalFilename))
      .andHas(KB.internalMimeType, literalOf(fileValue.fileValue.internalMimeType))

    val withOptionalFields = fileValue.fileValue.originalFilename match {
      case Some(originalFilename) => basePattern.andHas(KB.originalFilename, literalOf(originalFilename))
      case None                   => basePattern
    }

    val withMimeType = fileValue.fileValue.originalMimeType match {
      case Some(originalMimeType) => withOptionalFields.andHas(KB.originalMimeType, literalOf(originalMimeType))
      case None                   => withOptionalFields
    }

    val withCopyright = fileValue.fileValue.copyrightHolder match {
      case Some(copyrightHolder) => withMimeType.andHas(KB.hasCopyrightHolder, literalOf(copyrightHolder.value))
      case None                  => withMimeType
    }

    val withLicense = fileValue.fileValue.licenseIri match {
      case Some(licenseIri) => withCopyright.andHas(KB.hasLicense, toRdfIri(licenseIri))
      case None             => withCopyright
    }

    val withAuthorship = fileValue.fileValue.authorship match {
      case Some(authorshipList) =>
        authorshipList.foldLeft(withLicense) { (pattern, author) =>
          pattern.andHas(KB.hasAuthorship, literalOf(author.value))
        }
      case None => withLicense
    }

    // Add type-specific properties
    val finalPattern = fileValue match {
      case stillImage: org.knora.webapi.messages.v2.responder.valuemessages.StillImageFileValueContentV2 =>
        withAuthorship.andHas(KB.dimX, literalOf(stillImage.dimX)).andHas(KB.dimY, literalOf(stillImage.dimY))
      case stillImageExternal: org.knora.webapi.messages.v2.responder.valuemessages.StillImageExternalFileValueContentV2 =>
        withAuthorship.andHas(KB.externalUrl, literalOf(stillImageExternal.externalUrl.value.toString))
      case document: org.knora.webapi.messages.v2.responder.valuemessages.DocumentFileValueContentV2 =>
        val withDimensions = (document.dimX, document.dimY) match {
          case (Some(dimX), Some(dimY)) =>
            withAuthorship.andHas(KB.dimX, literalOf(dimX)).andHas(KB.dimY, literalOf(dimY))
          case (Some(dimX), None) => withAuthorship.andHas(KB.dimX, literalOf(dimX))
          case (None, Some(dimY)) => withAuthorship.andHas(KB.dimY, literalOf(dimY))
          case _                  => withAuthorship
        }
        document.pageCount match {
          case Some(pageCount) => withDimensions.andHas(KB.pageCount, literalOf(pageCount))
          case None            => withDimensions
        }
      case _ => withAuthorship
    }

    List(finalPattern)
  }

  private def buildLinkPatterns(
    resource: org.eclipse.rdf4j.sparqlbuilder.rdf.Iri,
    linkUpdates: Seq[SparqlTemplateLinkUpdate],
    creationDate: Instant,
    valueCreator: InternalIri,
  ): List[TriplePattern] =
    linkUpdates.flatMap { linkUpdate =>
      val directLink = if (linkUpdate.insertDirectLink) {
        Some(resource.has(toRdfIri(linkUpdate.linkPropertyIri), toRdfIri(linkUpdate.linkTargetIri)))
      } else None

      val isDeleted = linkUpdate.newReferenceCount == 0

      val linkValue = toRdfIri(linkUpdate.newLinkValueIri)
        .isA(KB.linkValue)
        .andHas(RDF.SUBJECT, resource)
        .andHas(RDF.PREDICATE, toRdfIri(linkUpdate.linkPropertyIri))
        .andHas(RDF.OBJECT, toRdfIri(linkUpdate.linkTargetIri))
        .andHas(KB.valueHasString, toRdfLiteral(linkUpdate.linkTargetIri))
        .andHas(KB.valueHasRefCount, toRdfLiteral(linkUpdate.newReferenceCount))
        .andHas(KB.isDeleted, toRdfLiteral(isDeleted))
        .andHas(KB.valueCreationDate, literalOfType(creationDate.toString, XSD.DATETIME))
        .andHas(KB.attachedToUser, Rdf.iri(linkUpdate.newLinkValueCreator))
        .andHas(KB.hasPermissions, toRdfLiteral(linkUpdate.newLinkValuePermissions))
        .pipe { linkValue =>
          if (linkUpdate.linkValueExists) {
            val linkValueIndex    = linkUpdates.indexOf(linkUpdate)
            val linkValueUUID     = variable(s"linkValueUUID$linkValueIndex")
            val previousLinkValue = variable(s"linkValue$linkValueIndex")
            linkValue
              .andHas(KB.previousValue, previousLinkValue)
              .andHas(KB.valueHasUUID, linkValueUUID)
          } else {
            linkValue.andHas(KB.valueHasUUID, literalOf(UuidUtil.base64Encode(UUID.randomUUID())))
          }
        }
        .pipe { linkValue =>
          if (isDeleted) {
            linkValue
              .andHas(KB.deleteDate, literalOfType(creationDate.toString, XSD.DATETIME))
              .andHas(KB.deletedBy, toRdfIri(valueCreator))
          } else {
            linkValue
          }
        }

      val resourceToLinkValue =
        resource.has(Rdf.iri(linkUpdate.linkPropertyIri.toString + "Value"), toRdfIri(linkUpdate.newLinkValueIri))

      List(directLink, Some(linkValue), Some(resourceToLinkValue)).flatten
    }.toList

  private def buildWhereClause(
    dataNamedGraph: String,
    dataNamedGraphVar: Variable,
    resourceString: String,
    resourceVar: Variable,
    value: ValueContentV2,
    linkUpdates: Seq[SparqlTemplateLinkUpdate],
    resourceLastModDate: Variable,
    nextOrder: Variable,
    resourceIri: ResourceIri,
    propertyIri: String,
    currentVar: Variable,
    currentValue: Option[ValueIri],
    currentValueUUID: Variable,
  ): List[GraphPattern] = {
    val resourceClass = variable("resourceClass")
    val valueType     = variable("valueType")
    val propertyRange = variable("propertyRange")
    val restriction   = variable("restriction")
    val propertyVar   = variable("property")

    val subClassOfPath = PropertyPathBuilder.of(RDFS.SUBCLASSOF).zeroOrMore.build()

    // Add the BIND statements that are present in the Twirl template (lines 392, 410, 411)
    val bindPatterns = List(
      Expressions.bind(Expressions.function(SparqlFunction.IRI, literalOf(dataNamedGraph)), dataNamedGraphVar),
      Expressions.bind(Expressions.function(SparqlFunction.IRI, literalOf(resourceString)), resourceVar),
    ) ++ currentValue.toList.map { currentValue =>
      Expressions.bind(Expressions.function(SparqlFunction.IRI, literalOf(currentValue.value)), currentVar)
    }

    val basicPatterns = List(
      // Resource validation
      resourceVar
        .isA(resourceClass)
        .andHas(KB.isDeleted, literalOf(false)),
      resourceClass.has(subClassOfPath, KB.Resource),

      // Optional resource last modification date
      resourceVar.has(KB.lastModificationDate, resourceLastModDate).optional(),

      // more bind
      Expressions.bind(Expressions.function(SparqlFunction.IRI, literalOf(propertyIri)), propertyVar),
      Expressions.bind(Expressions.function(SparqlFunction.IRI, literalOf(value.valueType.toString)), valueType),

      // Property validation
      propertyVar.has(Rdf.iri(OntologyConstants.KnoraBase.ObjectClassConstraint), propertyRange),
      valueType.has(subClassOfPath, propertyRange),

      // Cardinality validation
      resourceClass.has(subClassOfPath, restriction),
      restriction
        .isA(OWL.RESTRICTION)
        .andHas(OWL.ONPROPERTY, propertyVar),
    )

    val currentValuePatterns = currentValue.toList.flatMap { _ =>
      val currentValueType = variable("currentValueType")
      List(
        resourceVar.has(propertyVar, currentVar),
        currentVar.has(RDF.TYPE, currentValueType),
        currentValueType.has(subClassOfPath, KB.Value),
        currentVar.has(KB.isDeleted, literalOf(false)),
        currentVar.has(KB.valueHasUUID, currentValueUUID),
        currentVar.has(KB.hasPermissions, variable("currentValuePermissions")),
      )
    }

    // List node validation for hierarchical list values
    val listNodeValidation = value match {
      case listValue: org.knora.webapi.messages.v2.responder.valuemessages.HierarchicalListValueContentV2 =>
        List(Rdf.iri(listValue.valueHasListNode).isA(Rdf.iri(OntologyConstants.KnoraBase.ListNode)))
      case _ => List.empty
    }

    // Link update validations
    val linkValidations = linkUpdates.zipWithIndex.flatMap { case (linkUpdate, index) =>
      if (linkUpdate.linkTargetExists) {
        val linkTargetClass      = variable(s"linkTargetClass$index")
        val expectedTargetClass  = variable(s"expectedTargetClass$index")
        val linkValue            = variable(s"linkValue$index")
        val linkValueUUID        = variable(s"linkValueUUID$index")
        val linkValuePermissions = variable(s"linkValuePermissions$index")

        val targetValidation = if (linkUpdate.insertDirectLink) {
          List(
            toRdfIri(linkUpdate.linkTargetIri)
              .isA(linkTargetClass)
              .andHas(KB.isDeleted, literalOf(false)),
            linkTargetClass.has(subClassOfPath, KB.Resource),
            toRdfIri(linkUpdate.linkPropertyIri)
              .has(Rdf.iri(OntologyConstants.KnoraBase.ObjectClassConstraint), expectedTargetClass),
            linkTargetClass.has(subClassOfPath, expectedTargetClass),
          )
        } else List.empty

        val directLinkValidation = if (linkUpdate.directLinkExists) {
          List(resourceVar.has(toRdfIri(linkUpdate.linkPropertyIri), toRdfIri(linkUpdate.linkTargetIri)))
        } else {
          // Follow Twirl template lines 471-473: Make sure there is no such direct link (MINUS clause)
          // Use MINUS pattern instead of filterNotExists for semantic equivalence
          List(
            GraphPatterns.minus(
              resourceVar.has(toRdfIri(linkUpdate.linkPropertyIri), toRdfIri(linkUpdate.linkTargetIri)),
            ),
          )
        }

        val linkValueValidation = if (linkUpdate.linkValueExists) {
          List(
            resourceVar.has(Rdf.iri(linkUpdate.linkPropertyIri.toString + "Value"), linkValue),
            linkValue
              .isA(KB.linkValue)
              .andHas(RDF.SUBJECT, resourceVar)
              .andHas(RDF.PREDICATE, toRdfIri(linkUpdate.linkPropertyIri))
              .andHas(RDF.OBJECT, toRdfIri(linkUpdate.linkTargetIri))
              .andHas(KB.valueHasRefCount, literalOf(linkUpdate.currentReferenceCount))
              .andHas(KB.isDeleted, literalOf(false))
              .andHas(KB.valueHasUUID, linkValueUUID)
              .andHas(KB.hasPermissions, linkValuePermissions),
          )
        } else {
          // Follow Twirl template lines 494-501: Make sure there is no such LinkValue (MINUS clause)
          // Use MINUS pattern instead of filterNotExists for semantic equivalence
          List(
            GraphPatterns.minus(
              resourceVar
                .has(Rdf.iri(linkUpdate.linkPropertyIri.toString + "Value"), linkValue)
                .and(
                  linkValue
                    .isA(KB.linkValue)
                    .andHas(RDF.SUBJECT, resourceVar)
                    .andHas(RDF.PREDICATE, toRdfIri(linkUpdate.linkPropertyIri))
                    .andHas(RDF.OBJECT, toRdfIri(linkUpdate.linkTargetIri))
                    .andHas(KB.isDeleted, literalOf(false)),
                ),
            ),
          )
        }

        targetValidation ::: directLinkValidation ::: linkValueValidation
      } else List.empty
    }.toList

    // Order calculation: different strategy for creates vs updates
    val orderPatterns: List[GraphPattern] = currentValue match {
      case Some(_) =>
        // Update case: preserve the existing order from the current value.
        // Uses OPTIONAL to handle values that may not have valueHasOrder (e.g. file values),
        // falling back to 0 (matching the old Twirl template behavior).
        val existingOrder = variable("existingOrder")
        List(
          GraphPatterns.optional(currentVar.has(KB.valueHasOrder, existingOrder)),
          Expressions.bind(
            Expressions.iff(Expressions.bound(existingOrder), existingOrder, literalOf(0)),
            nextOrder,
          ),
        )
      case None =>
        // Create case: append at end using MAX(order) + 1
        val maxOrder   = variable("maxOrder")
        val order      = variable("order")
        val otherValue = variable("otherValue")
        List(
          GraphPatterns
            .select()
            .select(
              Expressions.max(order).as(maxOrder),
              Expressions
                .iff(
                  Expressions.bound(maxOrder),
                  Expressions.add(maxOrder, literalOf(1)),
                  literalOf(0),
                )
                .as(nextOrder),
            )
            .where(
              toRdfIri(resourceIri).has(Rdf.iri(propertyIri), otherValue),
              otherValue.has(KB.valueHasOrder, order).andHas(KB.isDeleted, literalOf(false)),
            ),
        )
    }

    List(
      bindPatterns,
      basicPatterns,
      currentValuePatterns,
      listNodeValidation,
      linkValidations,
      orderPatterns,
    ).fold(List[GraphPattern]())(_ ::: _)
  }
}

/**
 * Extends the `Option` class to add the `andHas` method.
 *
 * This method allows adding a triple to an optional triple pattern if the object is defined.
 *
 * @param s The RDF subject of the optional triple pattern.
 * @param p The RDF predicate of the optional triple pattern.
 * @param o The RDF object of the optional triple pattern.
 * @return A `TriplePattern` object that represents the combined triple pattern.
 */
extension (optTriplePattern: Option[TriplePattern])
  def andHas(s: RdfSubject, p: RdfPredicate, o: RdfObject): TriplePattern =
    optTriplePattern.fold(s.has(p, o))(_.andHas(p, o))

/**
 * Extends the `Option` class to add the `andHasOptional` method.
 *
 * This method allows adding an optional triple to an optional triple pattern.
 *
 * @param s The RDF subject of the optional triple pattern.
 * @param p The RDF predicate of the optional triple pattern.
 * @param o The RDF object of the optional triple pattern.
 * @return An optional `TriplePattern` object that represents the combined triple pattern.
 */
extension (optTriplePattern: Option[TriplePattern])
  def andHasOptional(s: RdfSubject, p: RdfPredicate, o: Option[RdfObject]): Option[TriplePattern] =
    (optTriplePattern, o) match {
      case (Some(triplePattern), Some(obj)) => Some(triplePattern.andHas(p, obj))
      case (Some(triplePattern), None)      => Some(triplePattern)
      case (None, Some(obj))                => Some(s.has(p, obj))
      case (None, None)                     => None
    }

/**
 * Extends the `RdfSubject` class to add the `hasOptional` method.
 *
 * This method allows adding an optional triple to a subject.
 *
 * @param p The RDF predicate of the optional triple pattern.
 * @param o The RDF object of the optional triple pattern.
 * @return An optional `TriplePattern` object that represents the combined triple pattern.
 */
extension (s: RdfSubject)
  def hasOptional(p: RdfPredicate, o: Option[RdfObject]): Option[TriplePattern] =
    o.map(obj => s.has(p, obj))

/**
 * Extends the `Option` class to add the `andHasAll` method.
 *
 * This method allows adding multiple triples to an optional triple pattern.
 *
 * @param in A sequence of items
 * @param fn A function that takes an item and returns a tuple of (RdfSubject, RdfPredicate, RdfObject)
 * @return An optional `TriplePattern` object that represents the combined triple patterns.
 */
extension (optTriplePattern: Option[TriplePattern])
  def andHasAllFn[A](in: Seq[A])(fn: A => (RdfSubject, RdfPredicate, RdfObject)): Option[TriplePattern] =
    in.foldLeft(optTriplePattern) { (acc, item) =>
      val (s, p, o) = fn(item)
      val pattern   = acc.fold(s.has(p, o))(_.andHas(p, o))
      Some(pattern)
    }
