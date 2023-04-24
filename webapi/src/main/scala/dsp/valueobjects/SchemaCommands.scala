package dsp.valueobjects

import zio.prelude.Validation

import java.time.Instant

import dsp.errors.ValidationException
import dsp.valueobjects.LangString
import dsp.valueobjects.Schema
import org.knora.webapi.messages.SmartIri

// below file was moved from dsp-shared, for more info refer to issue DEV-745 and BL

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
