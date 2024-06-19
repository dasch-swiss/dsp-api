/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import org.eclipse.rdf4j.model.Namespace
import org.eclipse.rdf4j.model.impl.SimpleNamespace
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.InsertDataQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.iri
import zio.*

import java.time.Instant

import dsp.constants.SalsahGui.IRI
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.twirl.NewLinkValueInfo
import org.knora.webapi.messages.twirl.NewValueInfo
import org.knora.webapi.messages.twirl.TypeSpecificValueInfo
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

case class ResourceReadyToCreate(
  resourceIri: IRI,
  resourceClassIri: IRI,
  resourceLabel: String,
  creationDate: Instant,
  permissions: String,
  newValueInfos: Seq[NewValueInfo],
  linkUpdates: Seq[NewLinkValueInfo],
)

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
    triplestore.query(
      ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri,
        resource,
        projectIri,
        userIri,
      ),
    )

}

object ResourcesRepoLive {
  val layer = ZLayer.derive[ResourcesRepoLive]

  private[service] def createNewResourceQueryTwirl(
    dataGraphIri: InternalIri,
    resourceToCreate: ResourceReadyToCreate,
    projectIri: IRI,
    creatorIri: IRI,
  ): Update =
    Update(
      sparql.v2.txt.createNewResource(
        dataNamedGraph = dataGraphIri.value,
        projectIri = projectIri,
        creatorIri = creatorIri,
        creationDate = resourceToCreate.creationDate,
        resourceIri = resourceToCreate.resourceIri,
        resourceClassIri = resourceToCreate.resourceClassIri,
        resourceLabel = resourceToCreate.resourceLabel,
        permissions = resourceToCreate.permissions,
        linkUpdates = resourceToCreate.linkUpdates,
        newValueInfos = resourceToCreate.newValueInfos,
      ),
    )

  private[service] def createNewResourceQuery(
    dataGraphIri: InternalIri,
    resourceToCreate: ResourceReadyToCreate,
    projectIri: IRI,
    creatorIri: IRI,
  ) = {
    import TypeSpecificValueInfo.*

    val graph = iri(dataGraphIri.value)

    val query: InsertDataQuery =
      Queries
        .INSERT_DATA()
        .into(graph)
        .prefix(KnoraBaseVocab.NS, RDF.NS, RDFS.NS, XSD.NS)

    val resourcePattern =
      Rdf
        .iri(resourceToCreate.resourceIri)
        .isA(iri(resourceToCreate.resourceClassIri))
        .andHas(RDFS.LABEL, Rdf.literalOf(resourceToCreate.resourceLabel))
        .andHas(KnoraBaseVocab.isDeleted, Rdf.literalOf(false))
        .andHas(KnoraBaseVocab.attachedToUser, iri(creatorIri))
        .andHas(KnoraBaseVocab.attachedToProject, iri(projectIri))
        .andHas(KnoraBaseVocab.hasPermissions, Rdf.literalOf(resourceToCreate.permissions))
        .andHas(KnoraBaseVocab.creationDate, Rdf.literalOfType(resourceToCreate.creationDate.toString(), XSD.DATETIME))

    query.insertData(resourcePattern)

    for (newValueInfo <- resourceToCreate.newValueInfos) {

      resourcePattern.andHas(iri(newValueInfo.propertyIri), Rdf.iri(newValueInfo.valueIri))

      val valuePattern =
        Rdf
          .iri(newValueInfo.valueIri)
          .isA(iri(newValueInfo.valueTypeIri))

      query.insertData(valuePattern)

      valuePattern
        .andHas(KnoraBaseVocab.isDeleted, Rdf.literalOf(false))
        .andHas(KnoraBaseVocab.valueHasString, Rdf.literalOf(newValueInfo.valueHasString))
        .andHas(KnoraBaseVocab.valueHasUUID, Rdf.literalOf(UuidUtil.base64Encode(newValueInfo.valueUUID)))
        .andHas(KnoraBaseVocab.attachedToUser, iri(newValueInfo.valueCreator))
        .andHas(KnoraBaseVocab.hasPermissions, Rdf.literalOf(newValueInfo.valuePermissions))
        .andHas(KnoraBaseVocab.valueHasOrder, Rdf.literalOf(newValueInfo.valueHasOrder))
        .andHas(
          KnoraBaseVocab.valueCreationDate,
          Rdf.literalOfType(newValueInfo.creationDate.toString(), XSD.DATETIME),
        )

      newValueInfo.comment.foreach(comment =>
        valuePattern.andHas(KnoraBaseVocab.valueHasComment, Rdf.literalOf(comment)),
      )

      newValueInfo.value match
        case LinkValueInfo(referredResourceIri) =>
          val directLinkPropertyIri = newValueInfo.propertyIri.stripSuffix("Value")
          resourcePattern.andHas(Rdf.iri(directLinkPropertyIri), Rdf.iri(referredResourceIri))
          valuePattern
            .andHas(RDF.SUBJECT, Rdf.iri(resourceToCreate.resourceIri))
            .andHas(RDF.PREDICATE, Rdf.iri(directLinkPropertyIri))
            .andHas(RDF.OBJECT, Rdf.iri(referredResourceIri))
            .andHas(KnoraBaseVocab.valueHasRefCount, Rdf.literalOf(1))
        case UnformattedTextValueInfo(valueHasLanguage) =>
          valueHasLanguage.foreach(lang => valuePattern.andHas(KnoraBaseVocab.valueHasLanguage, Rdf.literalOf(lang)))
        case FormattedTextValueInfo(valueHasLanguage, mappingIri, maxStandoffStartIndex, standoff) =>
          valueHasLanguage.foreach(lang => valuePattern.andHas(KnoraBaseVocab.valueHasLanguage, Rdf.literalOf(lang)))
          valuePattern
            .andHas(KnoraBaseVocab.valueHasMapping, Rdf.iri(mappingIri))
            .andHas(KnoraBaseVocab.valueHasMaxStandoffStartIndex, Rdf.literalOf(maxStandoffStartIndex))
          for (standoffTagInfo <- standoff) {
            valuePattern
              .andHas(KnoraBaseVocab.valueHasStandoff, Rdf.iri(standoffTagInfo.standoffTagInstanceIri))
            val standoffPattern = Rdf
              .iri(standoffTagInfo.standoffTagInstanceIri)
              .isA(iri(standoffTagInfo.standoffTagClassIri))

            standoffTagInfo.endIndex.foreach(endIndex =>
              standoffPattern.andHas(KnoraBaseVocab.standoffTagHasEndIndex, Rdf.literalOf(endIndex)),
            )
            standoffTagInfo.startParentIri.foreach(startParentIri =>
              standoffPattern.andHas(KnoraBaseVocab.standoffTagHasStartParent, Rdf.iri(startParentIri)),
            )
            standoffTagInfo.endParentIri.foreach(endParentIri =>
              standoffPattern.andHas(KnoraBaseVocab.standoffTagHasEndParent, Rdf.iri(endParentIri)),
            )
            standoffTagInfo.originalXMLID.foreach(originalXMLID =>
              standoffPattern.andHas(KnoraBaseVocab.standoffTagHasOriginalXMLID, Rdf.literalOf(originalXMLID)),
            )
            for (attribute <- standoffTagInfo.attributes) {
              standoffPattern.andHas(Rdf.iri(attribute.propertyIri), Rdf.literalOf(attribute.value))
            }
            standoffPattern
              .andHas(KnoraBaseVocab.standoffTagHasStartIndex, Rdf.literalOf(standoffTagInfo.startIndex))
              .andHas(KnoraBaseVocab.standoffTagHasUUID, Rdf.literalOf(UuidUtil.base64Encode(standoffTagInfo.uuid)))
              .andHas(KnoraBaseVocab.standoffTagHasStart, Rdf.literalOf(standoffTagInfo.startPosition))
              .andHas(KnoraBaseVocab.standoffTagHasEnd, Rdf.literalOf(standoffTagInfo.endPosition))
            query.insertData(standoffPattern)
          }
        case IntegerValueInfo(valueHasInteger) =>
          valuePattern.andHas(KnoraBaseVocab.valueHasInteger, Rdf.literalOf(valueHasInteger))
        case DecimalValueInfo(valueHasDecimal) =>
          valuePattern.andHas(KnoraBaseVocab.valueHasDecimal, Rdf.literalOf(valueHasDecimal))
        case BooleanValueInfo(valueHasBoolean) =>
          valuePattern.andHas(KnoraBaseVocab.valueHasBoolean, Rdf.literalOf(valueHasBoolean))
        case UriValueInfo(valueHasUri) =>
          valuePattern.andHas(KnoraBaseVocab.valueHasUri, Rdf.literalOfType(valueHasUri, XSD.ANYURI))
        case DateValueInfo(startJDN, endJDN, startPrecision, endPrecision, calendar) =>
          valuePattern
            .andHas(KnoraBaseVocab.valueHasStartJDN, Rdf.literalOf(startJDN))
            .andHas(KnoraBaseVocab.valueHasEndJDN, Rdf.literalOf(endJDN))
            .andHas(KnoraBaseVocab.valueHasStartPrecision, Rdf.literalOf(startPrecision.toString()))
            .andHas(KnoraBaseVocab.valueHasEndPrecision, Rdf.literalOf(endPrecision.toString()))
            .andHas(KnoraBaseVocab.valueHasCalendar, Rdf.literalOf(calendar.toString()))
        case ColorValueInfo(valueHasColor) =>
          valuePattern.andHas(KnoraBaseVocab.valueHasColor, Rdf.literalOf(valueHasColor))
        case GeomValueInfo(valueHasGeometry) =>
          valuePattern.andHas(KnoraBaseVocab.valueHasGeometry, Rdf.literalOf(valueHasGeometry))
        case StillImageFileValueInfo(
              internalFilename,
              internalMimeType,
              originalFilename,
              originalMimeType,
              dimX,
              dimY,
            ) =>
          valuePattern
            .andHas(KnoraBaseVocab.internalFilename, Rdf.literalOf(internalFilename))
            .andHas(KnoraBaseVocab.internalMimeType, Rdf.literalOf(internalMimeType))
            .andHas(KnoraBaseVocab.dimX, Rdf.literalOf(dimX))
            .andHas(KnoraBaseVocab.dimY, Rdf.literalOf(dimY))
          originalFilename.foreach(filename =>
            valuePattern.andHas(KnoraBaseVocab.originalFilename, Rdf.literalOf(filename)),
          )
          originalMimeType.foreach(mimeType =>
            valuePattern.andHas(KnoraBaseVocab.originalMimeType, Rdf.literalOf(mimeType)),
          )
        case StillImageExternalFileValueInfo(
              internalFilename,
              internalMimeType,
              originalFilename,
              originalMimeType,
              externalUrl,
            ) =>
          valuePattern
            .andHas(KnoraBaseVocab.internalFilename, Rdf.literalOf(internalFilename))
            .andHas(KnoraBaseVocab.internalMimeType, Rdf.literalOf(internalMimeType))
            .andHas(KnoraBaseVocab.externalUrl, Rdf.literalOf(externalUrl))
          originalFilename.foreach(filename =>
            valuePattern.andHas(KnoraBaseVocab.originalFilename, Rdf.literalOf(filename)),
          )
          originalMimeType.foreach(mimeType =>
            valuePattern.andHas(KnoraBaseVocab.originalMimeType, Rdf.literalOf(mimeType)),
          )
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
            .andHas(KnoraBaseVocab.internalFilename, Rdf.literalOf(internalFilename))
            .andHas(KnoraBaseVocab.internalMimeType, Rdf.literalOf(internalMimeType))
          originalFilename.foreach(filename =>
            valuePattern.andHas(KnoraBaseVocab.originalFilename, Rdf.literalOf(filename)),
          )
          originalMimeType.foreach(mimeType =>
            valuePattern.andHas(KnoraBaseVocab.originalMimeType, Rdf.literalOf(mimeType)),
          )
          dimX.foreach(x => valuePattern.andHas(KnoraBaseVocab.dimX, Rdf.literalOf(x)))
          dimY.foreach(y => valuePattern.andHas(KnoraBaseVocab.dimY, Rdf.literalOf(y)))
          pageCount.foreach(count => valuePattern.andHas(KnoraBaseVocab.pageCount, Rdf.literalOf(count)))
        case OtherFileValueInfo(internalFilename, internalMimeType, originalFilename, originalMimeType) =>
          valuePattern
            .andHas(KnoraBaseVocab.internalFilename, Rdf.literalOf(internalFilename))
            .andHas(KnoraBaseVocab.internalMimeType, Rdf.literalOf(internalMimeType))
          originalFilename.foreach(filename =>
            valuePattern.andHas(KnoraBaseVocab.originalFilename, Rdf.literalOf(filename)),
          )
          originalMimeType.foreach(mimeType =>
            valuePattern.andHas(KnoraBaseVocab.originalMimeType, Rdf.literalOf(mimeType)),
          )
        case HierarchicalListValueInfo(valueHasListNode) =>
          valuePattern.andHas(KnoraBaseVocab.valueHasListNode, Rdf.iri(valueHasListNode))
        case IntervalValueInfo(valueHasIntervalStart, valueHasIntervalEnd) =>
          valuePattern
            .andHas(KnoraBaseVocab.valueHasIntervalStart, Rdf.literalOf(valueHasIntervalStart))
            .andHas(KnoraBaseVocab.valueHasIntervalEnd, Rdf.literalOf(valueHasIntervalEnd))
        case TimeValueInfo(valueHasTimeStamp) =>
          valuePattern.andHas(
            KnoraBaseVocab.valueHasTimeStamp,
            Rdf.literalOfType(valueHasTimeStamp.toString(), XSD.DATETIME),
          )
        case GeonameValueInfo(valueHasGeonameCode) =>
          valuePattern.andHas(KnoraBaseVocab.valueHasGeonameCode, Rdf.literalOf(valueHasGeonameCode))

    }

    Update(query.getQueryString())
  }

}

object KnoraBaseVocab {
  private val kb = "http://www.knora.org/ontology/knora-base#"

  val NS: Namespace = new SimpleNamespace("knora-base", kb)

  val isDeleted         = iri(kb + "isDeleted")
  val attachedToUser    = iri(kb + "attachedToUser")
  val attachedToProject = iri(kb + "attachedToProject")
  val hasPermissions    = iri(kb + "hasPermissions")
  val creationDate      = iri(kb + "creationDate")

  val valueHasString    = iri(kb + "valueHasString")
  val valueHasUUID      = iri(kb + "valueHasUUID")
  val valueHasComment   = iri(kb + "valueHasComment")
  val valueHasOrder     = iri(kb + "valueHasOrder")
  val valueCreationDate = iri(kb + "valueCreationDate")

  val valueHasInteger               = iri(kb + "valueHasInteger")
  val valueHasBoolean               = iri(kb + "valueHasBoolean")
  val valueHasDecimal               = iri(kb + "valueHasDecimal")
  val valueHasUri                   = iri(kb + "valueHasUri")
  val valueHasStartJDN              = iri(kb + "valueHasStartJDN")
  val valueHasEndJDN                = iri(kb + "valueHasEndJDN")
  val valueHasStartPrecision        = iri(kb + "valueHasStartPrecision")
  val valueHasEndPrecision          = iri(kb + "valueHasEndPrecision")
  val valueHasCalendar              = iri(kb + "valueHasCalendar")
  val valueHasColor                 = iri(kb + "valueHasColor")
  val valueHasGeometry              = iri(kb + "valueHasGeometry")
  val valueHasListNode              = iri(kb + "valueHasListNode")
  val valueHasIntervalStart         = iri(kb + "valueHasIntervalStart")
  val valueHasIntervalEnd           = iri(kb + "valueHasIntervalEnd")
  val valueHasTimeStamp             = iri(kb + "valueHasTimeStamp")
  val valueHasGeonameCode           = iri(kb + "valueHasGeonameCode")
  val valueHasRefCount              = iri(kb + "valueHasRefCount")
  val valueHasLanguage              = iri(kb + "valueHasLanguage")
  val valueHasMapping               = iri(kb + "valueHasMapping")
  val valueHasMaxStandoffStartIndex = iri(kb + "valueHasMaxStandoffStartIndex")
  val valueHasStandoff              = iri(kb + "valueHasStandoff")

  val internalFilename = iri(kb + "internalFilename")
  val internalMimeType = iri(kb + "internalMimeType")
  val originalFilename = iri(kb + "originalFilename")
  val originalMimeType = iri(kb + "originalMimeType")
  val dimX             = iri(kb + "dimX")
  val dimY             = iri(kb + "dimY")
  val externalUrl      = iri(kb + "externalUrl")
  val pageCount        = iri(kb + "pageCount")

  val standoffTagHasStartIndex    = iri(kb + "standoffTagHasStartIndex")
  val standoffTagHasEndIndex      = iri(kb + "standoffTagHasEndIndex")
  val standoffTagHasStartParent   = iri(kb + "standoffTagHasStartParent")
  val standoffTagHasEndParent     = iri(kb + "standoffTagHasEndParent")
  val standoffTagHasOriginalXMLID = iri(kb + "standoffTagHasOriginalXMLID")
  val standoffTagHasUUID          = iri(kb + "standoffTagHasUUID")
  val standoffTagHasStart         = iri(kb + "standoffTagHasStart")
  val standoffTagHasEnd           = iri(kb + "standoffTagHasEnd")

}
