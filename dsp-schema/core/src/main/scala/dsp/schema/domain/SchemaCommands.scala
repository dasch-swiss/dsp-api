package dsp.schema.domain

import dsp.errors.ValidationException
import dsp.valueobjects.LangString
import dsp.valueobjects.Schema
import zio.prelude.Validation

import java.time.Instant

/**
 * SmartIri placeholder value object.
 * WARNING: don't use this in production code. First find a solution how we deal with SmartIri in the new codebase.
 *
 * // TODO: this is only a placeholder for SmartIri - eventually we need a proper solution for IRI value objects.
 */
case class SmartIri(value: String)

/**
 * Command/Value object representing a command to create a property on a schema/ontology.
 * WARNING: This should not be used in production code before the SmartIri value object is propertly implemented.
 */
sealed abstract case class CreatePropertyCommand private (
  ontologyIri: SmartIri,
  lastModificationDate: Instant,
  propertyIri: SmartIri,
  subjectType: Option[SmartIri],
  objectType: SmartIri, // must be `kb:Value`, unless it's a link property, then it should be a `kb:Resource`
  label: LangString,
  comment: Option[LangString],
  superProperties: List[SmartIri],
  guiObject: Schema.GuiObject
)

object CreatePropertyCommand {
  def make(
    ontologyIri: SmartIri, // TODO: should eventally be schemaId value object, etc.
    lastModificationDate: Instant,
    propertyIri: SmartIri,
    subjectType: Option[SmartIri],
    objectType: SmartIri,
    label: LangString,
    comment: Option[LangString],
    superProperties: List[SmartIri],
    guiObject: Schema.GuiObject
  ): Validation[ValidationException, CreatePropertyCommand] =
    Validation.succeed(
      new CreatePropertyCommand(
        ontologyIri = ontologyIri,
        lastModificationDate = lastModificationDate,
        propertyIri = propertyIri,
        subjectType = subjectType,
        objectType = objectType,
        label = label,
        comment = comment,
        superProperties = superProperties,
        guiObject = guiObject
      ) {}
    )
}
