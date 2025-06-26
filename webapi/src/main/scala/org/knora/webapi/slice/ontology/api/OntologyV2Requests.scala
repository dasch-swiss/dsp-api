/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api
import eu.timepit.refined.types.string.NonEmptyString

import java.time.Instant
import java.util.UUID
import scala.collection.immutable.Seq
import scala.language.implicitConversions

import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LabelOrComment
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.KnoraIris
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.jena.StatementOps.*

/**
 * Requests a change in the metadata of an ontology.
 *
 * @param ontologyIri          the external ontology IRI.
 * @param label                the ontology's new label.
 * @param comment              the ontology's new comment.
 * @param lastModificationDate the ontology's last modification date, returned in a previous operation.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class ChangeOntologyMetadataRequestV2(
  ontologyIri: OntologyIri,
  label: Option[String] = None,
  comment: Option[NonEmptyString] = None,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
)

/**
 * Requests the addition of a class to an ontology.
 *
 * @param classInfoContent     a [[ClassInfoContentV2]] containing the class definition.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class CreateClassRequestV2(
  classInfoContent: ClassInfoContentV2,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
)

/**
 * Requests the addition of cardinalities to a class.
 *
 * @param classInfoContent     a [[ClassInfoContentV2]] containing the class definition.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class AddCardinalitiesToClassRequestV2(
  classInfoContent: ClassInfoContentV2,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
)

/**
 * Requests that a property's labels or comments are changed.
 *
 * @param propertyIri          the IRI of the property.
 * @param predicateToUpdate    `rdfs:label` or `rdfs:comment`.
 * @param newObjects           the property's new labels or comments.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class ChangePropertyLabelsOrCommentsRequestV2(
  propertyIri: PropertyIri,
  predicateToUpdate: LabelOrComment,
  newObjects: Seq[StringLiteralV2],
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
)

/**
 * Requests the replacement of a class's cardinalities with new ones.
 *
 * @param classInfoContent     a [[ClassInfoContentV2]] containing the new cardinalities.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class ChangeGuiOrderRequestV2(
  classInfoContent: ClassInfoContentV2,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
)

/**
 * Requests the replacement of a class's cardinalities with new ones. A successful response will be a [[ReadOntologyV2]].
 *
 * @param classInfoContent     a [[ClassInfoContentV2]] containing the new cardinalities.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class ReplaceClassCardinalitiesRequestV2(
  classInfoContent: ClassInfoContentV2,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
)
