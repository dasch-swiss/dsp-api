package org.knora.webapi.slice.api.v2.ontologies

import eu.timepit.refined.types.string.NonEmptyString
import org.eclipse.rdf4j.model.vocabulary.RDFS

import java.time.Instant
import java.util.UUID

import dsp.valueobjects.Schema
import org.knora.webapi.messages.store.triplestoremessages.LanguageTaggedStringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.PropertyInfoContentV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.KnoraIris
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri

/**
 * Requests a check if the user can remove class's cardinalities. A successful response will be a [[CanDoResponseV2]].
 *
 * @param classInfoContent     a [[ClassInfoContentV2]] containing the cardinalities to be removed.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
final case class CanDeleteCardinalitiesFromClassRequestV2(
  classInfoContent: ClassInfoContentV2,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
)

/**
 * Requests that a class's labels or comments are changed. A successful response will be a [[ReadOntologyV2]].
 *
 * @param classIri             the IRI of the property.
 * @param predicateToUpdate    `rdfs:label` or `rdfs:comment`.
 * @param newObjects           the class's new labels or comments.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class ChangeClassLabelsOrCommentsRequestV2(
  classIri: ResourceClassIri,
  predicateToUpdate: LabelOrComment,
  newObjects: Seq[LanguageTaggedStringLiteralV2],
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
)

/**
 * Requests that the `salsah-gui:guiElement` and `salsah-gui:guiAttribute` of a property are changed.
 *
 * @param propertyIri          the IRI of the property to be changed.
 * @param newGuiObject         the GUI object with the new GUI element and/or GUI attributes.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class ChangePropertyGuiElementRequest(
  propertyIri: KnoraIris.PropertyIri,
  newGuiObject: Schema.GuiObject,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
)

/**
 * Requests the creation of an empty ontology. A successful response will be a [[ReadOntologyV2]].
 *
 * @param ontologyName   the name of the ontology to be created.
 * @param projectIri     the IRI of the project that the ontology will belong to.
 * @param isShared       the flag that shows if an ontology is a shared one.
 * @param label          the label of the ontology.
 * @param comment        the optional comment that described the ontology to be created.
 * @param apiRequestID   the ID of the API request.
 * @param requestingUser the user making the request.
 */
case class CreateOntologyRequestV2(
  ontologyName: String,
  projectIri: ProjectIri,
  isShared: Boolean,
  label: String,
  comment: Option[NonEmptyString],
  apiRequestID: UUID,
  requestingUser: User,
)

/**
 * Requests the addition of a property to an ontology. A successful response will be a [[ReadOntologyV2]].
 *
 * @param propertyInfoContent  an [[PropertyInfoContentV2]] containing the property definition.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class CreatePropertyRequestV2(
  propertyInfoContent: PropertyInfoContentV2,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
)

/**
 * Requests the removal of a class's cardinalities. A successful response will be a [[ReadOntologyV2]].
 *
 * @param classInfoContent     a [[ClassInfoContentV2]] containing the cardinalities to be removed.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
final case class DeleteCardinalitiesFromClassRequestV2(
  classInfoContent: ClassInfoContentV2,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
)

enum LabelOrComment {
  case Label
  case Comment

  override def toString: String = this match {
    case Label   => RDFS.LABEL.toString
    case Comment => RDFS.COMMENT.toString
  }
}

object LabelOrComment {
  def fromString(str: String): Option[LabelOrComment] =
    LabelOrComment.values.find(_.toString == str)
}
