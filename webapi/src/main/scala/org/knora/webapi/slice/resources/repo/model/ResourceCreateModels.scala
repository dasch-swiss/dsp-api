/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.model

import java.time.Instant
import java.util.UUID

import org.knora.webapi.IRI

final case class ResourceReadyToCreate(
  resourceIri: IRI,
  resourceClassIri: IRI,
  resourceLabel: String,
  creationDate: Instant,
  permissions: String,
  newValueInfos: Seq[NewValueInfo],
  standoffLinks: Seq[StandoffLinkValueInfo],
)

final case class NewValueInfo(
  resourceIri: IRI,
  propertyIri: IRI,
  valueIri: IRI,
  valueTypeIri: IRI,
  valueUUID: UUID,
  value: TypeSpecificValueInfo,
  valuePermissions: String,
  valueCreator: IRI,
  creationDate: Instant,
  valueHasOrder: Int,
  valueHasString: String,
  comment: Option[String],
)

enum TypeSpecificValueInfo {
  case LinkValueInfo(referredResourceIri: IRI)
  case UnformattedTextValueInfo(valueHasLanguage: Option[String])
  case FormattedTextValueInfo(
    valueHasLanguage: Option[String],
    mappingIri: IRI,
    maxStandoffStartIndex: Int,
    standoff: Seq[StandoffTagInfo],
  )
  case IntegerValueInfo(valueHasInteger: Int)
  case DecimalValueInfo(valueHasDecimal: BigDecimal)
  case BooleanValueInfo(valueHasBoolean: Boolean)
  case UriValueInfo(valueHasUri: String)
  case DateValueInfo(
    valueHasStartJDN: Int,
    valueHasEndJDN: Int,
    valueHasStartPrecision: String,
    valueHasEndPrecision: String,
    valueHasCalendar: String,
  )
  case ColorValueInfo(valueHasColor: String)
  case GeomValueInfo(valueHasGeometry: String)
  case StillImageFileValueInfo(
    internalFilename: String,
    internalMimeType: String,
    originalFilename: Option[String],
    originalMimeType: Option[String],
    dimX: Int,
    dimY: Int,
  )
  case StillImageExternalFileValueInfo(
    internalFilename: String,
    internalMimeType: String,
    originalFilename: Option[String],
    originalMimeType: Option[String],
    externalUrl: String,
  )
  case DocumentFileValueInfo(
    internalFilename: String,
    internalMimeType: String,
    originalFilename: Option[String],
    originalMimeType: Option[String],
    dimX: Option[Int],
    dimY: Option[Int],
    pageCount: Option[Int],
  )
  case OtherFileValueInfo(
    internalFilename: String,
    internalMimeType: String,
    originalFilename: Option[String],
    originalMimeType: Option[String],
  )
  case HierarchicalListValueInfo(valueHasListNode: IRI)
  case IntervalValueInfo(valueHasIntervalStart: BigDecimal, valueHasIntervalEnd: BigDecimal)
  case TimeValueInfo(valueHasTimeStamp: Instant)
  case GeonameValueInfo(valueHasGeonameCode: String)
}

final case class StandoffLinkValueInfo(
  linkPropertyIri: IRI,
  newLinkValueIri: IRI,
  linkTargetIri: IRI,
  newReferenceCount: Int,
  newLinkValueCreator: IRI,
  newLinkValuePermissions: String,
  valueUuid: String,
)

enum StandoffAttributeValue {
  case IriAttribute(value: IRI)
  case UriAttribute(value: String)
  case InternalReferenceAttribute(value: IRI)
  case StringAttribute(value: String)
  case IntegerAttribute(value: Int)
  case DecimalAttribute(value: BigDecimal)
  case BooleanAttribute(value: Boolean)
  case TimeAttribute(value: Instant)
}

final case class StandoffAttribute(
  propertyIri: IRI,
  value: StandoffAttributeValue,
)

final case class StandoffTagInfo(
  standoffTagClassIri: IRI,
  standoffTagInstanceIri: IRI,
  startParentIri: Option[IRI],
  endParentIri: Option[IRI],
  uuid: UUID,
  originalXMLID: Option[String],
  startIndex: Int,
  endIndex: Option[Int],
  startPosition: Int,
  endPosition: Int,
  attributes: Seq[StandoffAttribute],
)
