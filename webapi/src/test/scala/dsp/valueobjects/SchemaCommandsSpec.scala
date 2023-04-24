package dsp.valueobjects

import zio.test.Assertion._
import zio.test._

import java.time.Instant

import dsp.constants.SalsahGui
import dsp.valueobjects.LangString
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.Schema
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import scala.collection.immutable.List
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.IriConverterLive
import zio.ZIO

/**
 * This spec is used to test [[dsp.schema.domain.SchemaCommandsSpec]].
 */
object SchemaCommandsSpec extends ZIOSpecDefault {

  def spec = (createPropertyCommandTest).provide(IriConverter.layer, StringFormatter.test)

  private val createPropertyCommandTest = suite("CreatePropertyCommand")(
    test("create a createPropertyCommand") {
      val lastModificationDate = Instant.now()
      val subjectType          = None
      (for {
        ontologyIri <- IriConverter.asSmartIri("http://www.knora.org/ontology/0001/anything")
        propertyIri <- IriConverter.asSmartIri("http://www.knora.org/ontology/0001/anything#someProperty")
        objectType  <- IriConverter.asSmartIri("http://www.knora.org/ontology/0001/anything#SomeClass")
        superProperties <-
          IriConverter.asSmartIri("http://www.knora.org/ontology/0001/anything#someSuperCoolProperty").map(List(_))
        label             <- LangString.make(LanguageCode.en, "some label").toZIO
        commentLangString <- LangString.make(LanguageCode.en, "some comment").toZIO
        comment            = Some(commentLangString)
        guiAttribute      <- Schema.GuiAttribute.make("hlist=<http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA>").toZIO
        guiElement        <- Schema.GuiElement.make(SalsahGui.List).toZIO
        guiObject         <- Schema.GuiObject.make(Set(guiAttribute), Some(guiElement)).toZIO
        command =
          CreatePropertyCommand.make(
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
      } yield assert(command.toEither)(isRight))
    }
  )
}
