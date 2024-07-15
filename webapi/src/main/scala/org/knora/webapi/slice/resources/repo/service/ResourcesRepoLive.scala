/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import org.eclipse.rdf4j.model.Namespace
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.InsertDataQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.literalOf
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.literalOfType
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.predicateObjectList
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfObject
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfPredicate
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfPredicateObjectList
import zio.*

import java.time.Instant

import dsp.constants.SalsahGui.IRI
import dsp.valueobjects.UuidUtil
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resources.repo.model.NewValueInfo
import org.knora.webapi.slice.resources.repo.model.ResourceReadyToCreate
import org.knora.webapi.slice.resources.repo.model.StandoffAttribute
import org.knora.webapi.slice.resources.repo.model.StandoffAttributeValue
import org.knora.webapi.slice.resources.repo.model.StandoffLinkValueInfo
import org.knora.webapi.slice.resources.repo.model.TypeSpecificValueInfo
import org.knora.webapi.slice.resources.repo.model.TypeSpecificValueInfo.*
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

trait ResourcesRepo {
  def createNewResource(
    dataGraphIri: InternalIri,
    resource: ResourceReadyToCreate,
    userIri: IRI,
    projectIri: IRI,
  ): Task[Unit]
}

final case class ResourcesRepoLive(triplestore: TriplestoreService) extends ResourcesRepo {

  def createNewResource(
    dataGraphIri: InternalIri,
    resource: ResourceReadyToCreate,
    userIri: IRI,
    projectIri: IRI,
  ): Task[Unit] =
    triplestore.query(ResourcesRepoLive.createNewResourceQuery(dataGraphIri, resource, projectIri, userIri))

}

object ResourcesRepoLive {
  val layer = ZLayer.derive[ResourcesRepoLive]

  private[service] def createNewResourceQuery(
    dataGraphIri: InternalIri,
    resourceToCreate: ResourceReadyToCreate,
    projectIri: IRI,
    creatorIri: IRI,
  ) = {
    import CreateResourceQueryBuilder.*

    val query: InsertDataQuery =
      Queries
        .INSERT_DATA()
        .into(iri(dataGraphIri.value))
        .prefix(KB.NS, RDF.NS, RDFS.NS, XSD.NS)

    val resourcePattern = buildResourcePattern(resourceToCreate, projectIri, creatorIri)
    query.insertData(resourcePattern)

    resourceToCreate.newValueInfos.foreach { valueInfo =>
      resourcePattern.andHas(iri(valueInfo.propertyIri), iri(valueInfo.valueIri))
      val valuePattern = buildValuePattern(valueInfo)
      query.insertData(valuePattern)
      valueInfo.value match
        case v: LinkValueInfo =>
          val triples: List[TriplePattern] =
            buildLinkValuePatterns(v, valueInfo.valueIri, valueInfo.propertyIri, resourceToCreate.resourceIri)
          query.insertData(triples: _*)
        case UnformattedTextValueInfo(valueHasLanguage) =>
          val triples: List[TriplePattern] =
            iri(valueInfo.valueIri).has(KB.valueHasLanguage, valueHasLanguage.map(literalOf)).toList
          query.insertData(triples: _*)
        case v: FormattedTextValueInfo =>
          val triples: List[TriplePattern] = buildFormattedTextValuePatterns(v, valueInfo.valueIri)
          query.insertData(triples: _*)
        case IntegerValueInfo(valueHasInteger) =>
          val triples = List(valuePattern.andHas(KB.valueHasInteger, literalOf(valueHasInteger)))
          query.insertData(triples: _*)
        case DecimalValueInfo(valueHasDecimal) =>
          val triples = List(valuePattern.andHas(KB.valueHasDecimal, literalOf(valueHasDecimal)))
          query.insertData(triples: _*)
        case BooleanValueInfo(valueHasBoolean) =>
          val triples = List(valuePattern.andHas(KB.valueHasBoolean, literalOf(valueHasBoolean)))
          query.insertData(triples: _*)
        case UriValueInfo(valueHasUri) =>
          val triples = List(valuePattern.andHas(KB.valueHasUri, literalOfType(valueHasUri, XSD.ANYURI)))
          query.insertData(triples: _*)
        case v: DateValueInfo =>
          val triples = buildDateValuePattern(v, valueInfo.valueIri)
          query.insertData(triples: _*)
        case ColorValueInfo(valueHasColor) =>
          valuePattern.andHas(KB.valueHasColor, literalOf(valueHasColor))
        case GeomValueInfo(valueHasGeometry) =>
          valuePattern.andHas(KB.valueHasGeometry, literalOf(valueHasGeometry))
        case StillImageFileValueInfo(
              internalFilename,
              internalMimeType,
              originalFilename,
              originalMimeType,
              dimX,
              dimY,
            ) =>
          valuePattern
            .andHas(KB.internalFilename, literalOf(internalFilename))
            .andHas(KB.internalMimeType, literalOf(internalMimeType))
            .andHas(KB.dimX, literalOf(dimX))
            .andHas(KB.dimY, literalOf(dimY))
          originalFilename.foreach(filename => valuePattern.andHas(KB.originalFilename, literalOf(filename)))
          originalMimeType.foreach(mimeType => valuePattern.andHas(KB.originalMimeType, literalOf(mimeType)))
        case StillImageExternalFileValueInfo(
              internalFilename,
              internalMimeType,
              originalFilename,
              originalMimeType,
              externalUrl,
            ) =>
          valuePattern
            .andHas(KB.internalFilename, literalOf(internalFilename))
            .andHas(KB.internalMimeType, literalOf(internalMimeType))
            .andHas(KB.externalUrl, literalOf(externalUrl))
          originalFilename.foreach(filename => valuePattern.andHas(KB.originalFilename, literalOf(filename)))
          originalMimeType.foreach(mimeType => valuePattern.andHas(KB.originalMimeType, literalOf(mimeType)))
        case DocumentFileValueInfo(
              internalFilename,
              internalMimeType,
              originalFilename,
              originalMimeType,
              dimX,
              dimY,
              pageCount,
            ) =>
          valuePattern
            .andHas(KB.internalFilename, literalOf(internalFilename))
            .andHas(KB.internalMimeType, literalOf(internalMimeType))
          originalFilename.foreach(filename => valuePattern.andHas(KB.originalFilename, literalOf(filename)))
          originalMimeType.foreach(mimeType => valuePattern.andHas(KB.originalMimeType, literalOf(mimeType)))
          dimX.foreach(x => valuePattern.andHas(KB.dimX, literalOf(x)))
          dimY.foreach(y => valuePattern.andHas(KB.dimY, literalOf(y)))
          pageCount.foreach(count => valuePattern.andHas(KB.pageCount, literalOf(count)))
        case OtherFileValueInfo(internalFilename, internalMimeType, originalFilename, originalMimeType) =>
          valuePattern
            .andHas(KB.internalFilename, literalOf(internalFilename))
            .andHas(KB.internalMimeType, literalOf(internalMimeType))
          originalFilename.foreach(filename => valuePattern.andHas(KB.originalFilename, literalOf(filename)))
          originalMimeType.foreach(mimeType => valuePattern.andHas(KB.originalMimeType, literalOf(mimeType)))
        case HierarchicalListValueInfo(valueHasListNode) =>
          valuePattern.andHas(KB.valueHasListNode, iri(valueHasListNode))
        case IntervalValueInfo(valueHasIntervalStart, valueHasIntervalEnd) =>
          valuePattern
            .andHas(KB.valueHasIntervalStart, literalOf(valueHasIntervalStart))
            .andHas(KB.valueHasIntervalEnd, literalOf(valueHasIntervalEnd))
        case TimeValueInfo(valueHasTimeStamp) =>
          valuePattern.andHas(KB.valueHasTimeStamp, literalOfType(valueHasTimeStamp.toString(), XSD.DATETIME))
        case GeonameValueInfo(valueHasGeonameCode) =>
          valuePattern.andHas(KB.valueHasGeonameCode, literalOf(valueHasGeonameCode))
    }

    resourceToCreate.standoffLinks.foreach { standoffLink =>
      resourcePattern.andHas(iri(standoffLink.linkPropertyIri), iri(standoffLink.linkTargetIri))
      resourcePattern.andHas(iri(standoffLink.linkPropertyIri + "Value"), iri(standoffLink.newLinkValueIri))
      val standoffLinkPattern = buildStandoffLinkPattern(
        standoffLink,
        resourceToCreate.resourceIri,
        resourceToCreate.creationDate,
      )
      query.insertData(standoffLinkPattern)
    }

    Update(query.getQueryString())
  }

  private object CreateResourceQueryBuilder {
    def buildResourcePattern(resource: ResourceReadyToCreate, projectIri: IRI, creatorIri: IRI): TriplePattern =
      iri(resource.resourceIri)
        .isA(iri(resource.resourceClassIri))
        .andHas(RDFS.LABEL, literalOf(resource.resourceLabel))
        .andHas(KB.isDeleted, literalOf(false))
        .andHas(KB.attachedToUser, iri(creatorIri))
        .andHas(KB.attachedToProject, iri(projectIri))
        .andHas(KB.hasPermissions, literalOf(resource.permissions))
        .andHas(KB.creationDate, literalOfType(resource.creationDate.toString(), XSD.DATETIME))

    def buildValuePattern(valueInfo: NewValueInfo): TriplePattern =
      iri(valueInfo.valueIri)
        .isA(iri(valueInfo.valueTypeIri))
        .andHas(KB.isDeleted, literalOf(false))
        .andHas(KB.valueHasString, literalOf(valueInfo.valueHasString))
        .andHas(KB.valueHasUUID, literalOf(UuidUtil.base64Encode(valueInfo.valueUUID)))
        .andHas(KB.attachedToUser, iri(valueInfo.valueCreator))
        .andHas(KB.hasPermissions, literalOf(valueInfo.valuePermissions))
        .andHas(KB.valueHasOrder, literalOf(valueInfo.valueHasOrder))
        .andHas(KB.valueCreationDate, literalOfType(valueInfo.creationDate.toString(), XSD.DATETIME))
        .andHas(KB.valueHasComment, valueInfo.comment.map(literalOf))

    def buildStandoffLinkPattern(
      standoffLink: StandoffLinkValueInfo,
      resourceIri: String,
      resourceCreationDate: Instant,
    ): TriplePattern =
      iri(standoffLink.newLinkValueIri)
        .isA(KB.linkValue)
        .andHas(RDF.SUBJECT, iri(resourceIri))
        .andHas(RDF.PREDICATE, iri(standoffLink.linkPropertyIri))
        .andHas(RDF.OBJECT, iri(standoffLink.linkTargetIri))
        .andHas(KB.valueHasString, literalOf(standoffLink.linkTargetIri))
        .andHas(KB.valueHasRefCount, literalOf(standoffLink.newReferenceCount))
        .andHas(KB.isDeleted, literalOf(false))
        .andHas(KB.valueCreationDate, literalOfType(resourceCreationDate.toString(), XSD.DATETIME))
        .andHas(KB.attachedToUser, iri(standoffLink.newLinkValueCreator))
        .andHas(KB.hasPermissions, literalOf(standoffLink.newLinkValuePermissions))
        .andHas(KB.valueHasUUID, literalOf(standoffLink.valueUuid))

    def standoffAttributeLiterals(attributes: Seq[StandoffAttribute]): List[RdfPredicateObjectList] =
      attributes.map { attribute =>
        val v = attribute.value match
          case StandoffAttributeValue.IriAttribute(value)               => iri(value)
          case StandoffAttributeValue.UriAttribute(value)               => literalOfType(value, XSD.ANYURI)
          case StandoffAttributeValue.InternalReferenceAttribute(value) => iri(value)
          case StandoffAttributeValue.StringAttribute(value)            => literalOf(value)
          case StandoffAttributeValue.IntegerAttribute(value)           => literalOf(value)
          case StandoffAttributeValue.DecimalAttribute(value)           => literalOf(value)
          case StandoffAttributeValue.BooleanAttribute(value)           => literalOf(value)
          case StandoffAttributeValue.TimeAttribute(value)              => literalOfType(value.toString(), XSD.DATETIME)
        val p = iri(attribute.propertyIri)
        predicateObjectList(p, v)
      }.toList

    def buildLinkValuePatterns(
      v: LinkValueInfo,
      valueIri: String,
      propertyIri: String,
      resourceIri: String,
    ): List[TriplePattern] =
      List(
        iri(resourceIri).has(iri(propertyIri.stripSuffix("Value")), iri(v.referredResourceIri)),
        iri(valueIri)
          .has(RDF.SUBJECT, iri(resourceIri))
          .andHas(RDF.PREDICATE, iri(propertyIri.stripSuffix("Value")))
          .andHas(RDF.OBJECT, iri(v.referredResourceIri))
          .andHas(KB.valueHasRefCount, literalOf(1)),
      )

    def buildFormattedTextValuePatterns(v: FormattedTextValueInfo, valueIri: String): List[TriplePattern] =
      val valuePattern =
        iri(valueIri)
          .has(KB.valueHasMapping, iri(v.mappingIri))
          .andHas(KB.valueHasMaxStandoffStartIndex, literalOf(v.maxStandoffStartIndex))
          .andHas(KB.valueHasLanguage, v.valueHasLanguage.map(literalOf))
      List(valuePattern) ::: v.standoff.map { standoffTagInfo =>
        valuePattern.andHas(KB.valueHasStandoff, iri(standoffTagInfo.standoffTagInstanceIri))
        iri(standoffTagInfo.standoffTagInstanceIri)
          .isA(iri(standoffTagInfo.standoffTagClassIri))
          .andHas(KB.standoffTagHasEndIndex, standoffTagInfo.endIndex.map(i => literalOf(i)))
          .andHas(KB.standoffTagHasStartParent, standoffTagInfo.startParentIri.map(iri))
          .andHas(KB.standoffTagHasEndParent, standoffTagInfo.endParentIri.map(iri))
          .andHas(KB.standoffTagHasOriginalXMLID, standoffTagInfo.originalXMLID.map(literalOf))
          .andHas(standoffAttributeLiterals(standoffTagInfo.attributes): _*)
          .andHas(KB.standoffTagHasStartIndex, literalOf(standoffTagInfo.startIndex))
          .andHas(KB.standoffTagHasUUID, literalOf(UuidUtil.base64Encode(standoffTagInfo.uuid)))
          .andHas(KB.standoffTagHasStart, literalOf(standoffTagInfo.startPosition))
          .andHas(KB.standoffTagHasEnd, literalOf(standoffTagInfo.endPosition))
      }.toList

    def buildDateValuePattern(v: DateValueInfo, valueIri: String): List[TriplePattern] =
      List(
        iri(valueIri)
          .has(KB.valueHasStartJDN, literalOf(v.valueHasStartJDN))
          .andHas(KB.valueHasEndJDN, literalOf(v.valueHasEndJDN))
          .andHas(KB.valueHasStartPrecision, literalOf(v.valueHasStartPrecision.toString()))
          .andHas(KB.valueHasEndPrecision, literalOf(v.valueHasEndPrecision.toString()))
          .andHas(KB.valueHasCalendar, literalOf(v.valueHasCalendar.toString())),
      )

  }
}

extension (tp: TriplePattern)
  def andHas(p: RdfPredicate, o: Option[RdfObject]): TriplePattern =
    o.fold(tp)(o => tp.andHas(p, o))

extension (iri: Iri)
  def has(p: RdfPredicate, o: Option[RdfObject]): Option[TriplePattern] =
    o.map(o => iri.has(p, o))

// TODO:
// - clean up rdf building
