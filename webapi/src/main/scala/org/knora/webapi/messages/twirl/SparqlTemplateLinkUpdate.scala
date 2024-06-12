/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.twirl

import java.time.Instant
import java.util.UUID

import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.CalendarNameV2
import org.knora.webapi.messages.util.DatePrecisionV2

/**
 * Contains instructions that can be given to a SPARQL template for updating direct links and `knora-base:LinkValue`
 * objects representing references to resources.
 *
 * @param linkPropertyIri         the IRI of the direct link property.
 * @param directLinkExists        `true` if a direct link already exists between the source and target resources.
 * @param insertDirectLink        `true` if the direct link should be inserted.
 * @param deleteDirectLink        `true` if the direct link should be deleted (because the reference count is being decremented
 *                                to 0).
 * @param linkValueExists         `true` if a `LinkValue` already exists describing a direct link between the source
 *                                and target resources.
 * @param linkTargetExists        `true` if the link target already exists, `false` if is being created in the same
 *                                SPARQL update operation.
 * @param newLinkValueIri         the IRI of the new `LinkValue` to be created.
 * @param linkTargetIri           the IRI of the target resource.
 * @param currentReferenceCount   the current reference count of the existing `LinkValue`, if any. This will be
 *                                0 if (a) there was previously a direct link but it was deleted (`directLinkExists` is
 *                                `false` and `linkValueExists` is `true`), or (b) there was never a direct link, and
 *                                there is no `LinkValue` (`directLinkExists` and `linkValueExists` will then be `false`).
 * @param newReferenceCount       the new reference count of the `LinkValue`.
 * @param newLinkValueCreator     the creator of the new `LinkValue`.
 * @param newLinkValuePermissions the literal that should be the object of the `hasPermissions` property of
 *                                the new `LinkValue`.
 */
case class SparqlTemplateLinkUpdate(
  linkPropertyIri: SmartIri,
  directLinkExists: Boolean,
  insertDirectLink: Boolean,
  deleteDirectLink: Boolean,
  linkValueExists: Boolean,
  linkTargetExists: Boolean,
  newLinkValueIri: IRI,
  linkTargetIri: IRI,
  currentReferenceCount: Int,
  newReferenceCount: Int,
  newLinkValueCreator: IRI,
  newLinkValuePermissions: String,
)

final case class NewLinkValueInfo(
  linkPropertyIri: IRI,
  newLinkValueIri: IRI,
  linkTargetIri: IRI,
  newReferenceCount: Int,
  newLinkValueCreator: IRI,
  newLinkValuePermissions: String,
  valueUuid: String,
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

final case class StandoffAttribute(
  propertyIri: IRI,
  value: String,
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
    valueHasStartPrecision: DatePrecisionV2,
    valueHasEndPrecision: DatePrecisionV2,
    valueHasCalendar: CalendarNameV2,
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
