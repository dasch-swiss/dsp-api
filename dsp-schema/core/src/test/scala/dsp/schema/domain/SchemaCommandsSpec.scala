package dsp.schema.domain

import zio.test.Assertion._
import zio.test._

import java.time.Instant

import dsp.constants.SalsahGui
import dsp.valueobjects.LangString
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.Schema

/**
 * This spec is used to test [[dsp.schema.domain.SchemaCommandsSpec]].
 */
object SchemaCommandsSpec extends ZIOSpecDefault {

  def spec = (createPropertyCommandTest)

  private val createPropertyCommandTest = suite("CreatePropertyCommand")(
    test("create a createPropertyCommand") {
      val ontologyIri          = SmartIri("Ontology IRI")
      val lastModificationDate = Instant.now()
      val propertyIri          = SmartIri("")
      val subjectType          = None
      val objectType           = SmartIri("Object Type")
      val superProperties      = List(SmartIri("Super Property IRI"))
      (for {
        label             <- LangString.make(LanguageCode.en, "some label")
        commentLangString <- LangString.make(LanguageCode.en, "some comment")
        comment            = Some(commentLangString)
        guiAttribute      <- Schema.GuiAttribute.make("hlist=<http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA>")
        guiElement        <- Schema.GuiElement.make(SalsahGui.List)
        guiObject         <- Schema.GuiObject.make(guiAttributes = Set(guiAttribute), guiElement = Some(guiElement))
        command = CreatePropertyCommand.make(
                    ontologyIri = ontologyIri,
                    lastModificationDate = lastModificationDate,
                    propertyIri = propertyIri,
                    subjectType = subjectType,
                    objectType = objectType,
                    label = label,
                    comment = comment,
                    superProperties = superProperties,
                    guiObject = guiObject
                  )
      } yield assert(command.toEither)(isRight)).toZIO
    }
  )
}
