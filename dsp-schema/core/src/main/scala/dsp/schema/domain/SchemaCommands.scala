package dsp.schema.domain

import dsp.errors.ValidationException
import dsp.valueobjects.LangString
import dsp.valueobjects.Schema
import zio.prelude.Validation

import java.time.Instant

case class SmartIri(value: String)

sealed abstract case class CreatePropertyCommand private (
  ontologyIri: SmartIri, // TODO: make SmartIri a ValueObject at some point
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
    ontologyIri: SmartIri,
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
