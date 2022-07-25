/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.constants.SalsahGui
import dsp.errors.ValidationException
import dsp.valueobjects.User._
import zio.NonEmptyChunk
import zio.prelude.Validation
import zio.test._

/**
 * This spec is used to test the [[dsp.valueobjects.User]] value objects creation.
 */
object SchemaSpec extends ZIOSpecDefault {

  private val guiAttributeSize = Schema.GuiAttribute.make("size=80").fold(e => throw e.head, v => v)
  private val guiAttributeHlist =
    Schema.GuiAttribute
      .make("hlist=<http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA>")
      .fold(e => throw e.head, v => v)
  private val guiAttributeMin       = Schema.GuiAttribute.make("min=1").fold(e => throw e.head, v => v)
  private val guiAttributeMax       = Schema.GuiAttribute.make("max=10.0").fold(e => throw e.head, v => v)
  private val guiAttributeMaxlength = Schema.GuiAttribute.make("maxlength=80").fold(e => throw e.head, v => v)
  private val guiAttributeCols      = Schema.GuiAttribute.make("cols=100").fold(e => throw e.head, v => v)
  private val guiAttributeRows      = Schema.GuiAttribute.make("rows=10").fold(e => throw e.head, v => v)
  private val guiAttributeWidth     = Schema.GuiAttribute.make("width=80%").fold(e => throw e.head, v => v)
  private val guiAttributeWrap      = Schema.GuiAttribute.make("wrap=soft").fold(e => throw e.head, v => v)
  private val guiAttributeNumprops  = Schema.GuiAttribute.make("numprops=3").fold(e => throw e.head, v => v)
  private val guiAttributeNcolors   = Schema.GuiAttribute.make("ncolors=12").fold(e => throw e.head, v => v)

  private val guiElementList        = Schema.GuiElement.make(SalsahGui.List).fold(e => throw e.head, v => v)
  private val guiElementRadio       = Schema.GuiElement.make(SalsahGui.Radio).fold(e => throw e.head, v => v)
  private val guiElementPulldown    = Schema.GuiElement.make(SalsahGui.Pulldown).fold(e => throw e.head, v => v)
  private val guiElementSlider      = Schema.GuiElement.make(SalsahGui.Slider).fold(e => throw e.head, v => v)
  private val guiElementSpinbox     = Schema.GuiElement.make(SalsahGui.Spinbox).fold(e => throw e.head, v => v)
  private val guiElementSimpleText  = Schema.GuiElement.make(SalsahGui.SimpleText).fold(e => throw e.head, v => v)
  private val guiElementTextarea    = Schema.GuiElement.make(SalsahGui.Textarea).fold(e => throw e.head, v => v)
  private val guiElementSearchbox   = Schema.GuiElement.make(SalsahGui.Searchbox).fold(e => throw e.head, v => v)
  private val guiElementColorpicker = Schema.GuiElement.make(SalsahGui.Colorpicker).fold(e => throw e.head, v => v)
  private val guiElementCheckbox    = Schema.GuiElement.make(SalsahGui.Checkbox).fold(e => throw e.head, v => v)

  def spec = (
    guiAttributeTest +
      guiElementTest +
      guiObjectTest +
      guiObjectListTest +
      guiObjectRadioTest +
      guiObjectPulldownTest +
      guiObjectSliderTest +
      guiObjectSpinboxTest +
      guiObjectSimpleTextTest +
      guiObjectTextareaTest +
      guiObjectSearchboxTest +
      guiObjectColorpickerTest +
      guiObjectCheckboxTest
  )

  private val guiAttributeTest = suite("gui attribute")(
    test("pass an empty value and return an error") {
      assertTrue(
        Schema.GuiAttribute.make("") == Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributeMissing))
      )
    },
    test("pass an invalid key and return an error") {
      assertTrue(
        Schema.GuiAttribute.make("invalid") == Validation.fail(
          ValidationException(SchemaErrorMessages.GuiAttributeUnknown("invalid"))
        )
      )
    },
    test("pass an unknown key and return an error") {
      assertTrue(
        Schema.GuiAttribute.make("unknown=10") == Validation.fail(
          ValidationException(SchemaErrorMessages.GuiAttributeUnknown("unknown"))
        )
      )
    },
    test("pass an invalid value for gui attribute 'wrap' and return an error") {
      assertTrue(
        Schema.GuiAttribute.make("wrap=invalid") == Validation.fail(
          ValidationException(SchemaErrorMessages.GuiAttributeWrongValueType("wrap", "invalid"))
        )
      )
    },
    test("pass an invalid value type for gui attribute 'rows' and return an error") {
      assertTrue(
        Schema.GuiAttribute.make("rows=20.5") == Validation.fail(
          ValidationException(SchemaErrorMessages.GuiAttributeWrongValueType("rows", "20.5"))
        )
      )
    },
    test("pass an invalid value type for gui attribute 'hlist' and return an error") {
      assertTrue(
        Schema.GuiAttribute.make("hlist=notAnIri") == Validation.fail(
          ValidationException(SchemaErrorMessages.GuiAttributeWrongValueType("hlist", "notAnIri"))
        )
      )
    },
    test("pass a valid value with whitespace and successfully create value object") {
      val guiAttributeWithWhitespace = "  size    =  80 "
      assertTrue(Schema.GuiAttribute.make(guiAttributeWithWhitespace).toOption.get.k == "size") &&
      assertTrue(Schema.GuiAttribute.make(guiAttributeWithWhitespace).toOption.get.v == "80")
    },
    test("pass a valid value for 'size' and successfully create value object") {
      val guiAttributeSizeString = "size=80"
      assertTrue(Schema.GuiAttribute.make(guiAttributeSizeString).toOption.get.k == "size") &&
      assertTrue(Schema.GuiAttribute.make(guiAttributeSizeString).toOption.get.v == "80") &&
      assertTrue(Schema.GuiAttribute.make(guiAttributeSizeString).toOption.get.value == "size=80")
    },
    test(
      "pass a valid value for 'hlist=<http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA>' and successfully create value object"
    ) {
      val guiAttributeHlistString = "hlist=<http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA>"
      assertTrue(Schema.GuiAttribute.make(guiAttributeHlistString).toOption.get.k == "hlist") &&
      assertTrue(
        Schema.GuiAttribute
          .make(guiAttributeHlistString)
          .toOption
          .get
          .v == "http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA"
      ) &&
      assertTrue(
        Schema.GuiAttribute
          .make(guiAttributeHlistString)
          .toOption
          .get
          .value == "hlist=http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA"
      )
    }
  )

  private val guiElementTest = suite("gui element")(
    test("pass an empty value and return an error") {
      assertTrue(
        Schema.GuiElement.make("") == Validation.fail(ValidationException(SchemaErrorMessages.GuiElementMissing))
      )
    },
    test("pass an unknown value and return an error") {
      assertTrue(
        Schema.GuiElement.make("http://www.knora.org/ontology/salsah-gui#Unknown") == Validation.fail(
          ValidationException(SchemaErrorMessages.GuiElementUnknown("http://www.knora.org/ontology/salsah-gui#Unknown"))
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Schema.GuiElement.make(SalsahGui.List).toOption.get.value == SalsahGui.List)
    }
  )

  private val guiObjectTest = suite("gui object - makeFromStrings")(
    test(
      "pass valid gui element with valid gui attributes and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .makeFromStrings(
          scala.collection.immutable.List("hlist=<http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA>"),
          Some(guiElementList.value)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List(guiAttributeHlist)) &&
      assertTrue(guiObject.guiElement == Some(guiElementList))
    },
    test(
      "pass valid gui element with multiple wrong gui attributes and return multiple errors"
    ) {
      assertTrue(
        Schema.GuiObject
          .makeFromStrings(
            scala.collection.immutable.List("mini", "maxi"),
            Some(guiElementSlider.value)
          ) == Validation.failNonEmptyChunk(
          NonEmptyChunk(
            ValidationException(SchemaErrorMessages.GuiAttributeUnknown("mini")),
            ValidationException(SchemaErrorMessages.GuiAttributeUnknown("maxi"))
          )
        )
      )
    },
    test(
      "pass invalid gui element with multiple wrong gui attributes and return multiple errors"
    ) {
      assertTrue(
        Schema.GuiObject
          .makeFromStrings(
            scala.collection.immutable.List("mini", "maxi"),
            Some("unknown")
          ) == Validation.failNonEmptyChunk(
          NonEmptyChunk(
            ValidationException(SchemaErrorMessages.GuiAttributeUnknown("mini")),
            ValidationException(SchemaErrorMessages.GuiAttributeUnknown("maxi")),
            ValidationException(SchemaErrorMessages.GuiElementUnknown("unknown"))
          )
        )
      )
    },
    test(
      "pass no gui element but gui attributes and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .makeFromStrings(
            scala.collection.immutable.List(guiAttributeMin.value, guiAttributeMax.value),
            None
          ) == Validation.failNonEmptyChunk(
          NonEmptyChunk(
            ValidationException(SchemaErrorMessages.GuiAttributeNotEmpty)
          )
        )
      )
    }
  )

  private val guiObjectListTest = suite("gui object - List (mandatory gui attribute)")(
    test(
      "pass gui element 'salsah-gui#List' with gui attribute 'hlist' and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(guiAttributeHlist),
          Some(guiElementList)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List(guiAttributeHlist)) &&
      assertTrue(guiObject.guiElement == Some(guiElementList))
    },
    test(
      "pass gui element 'salsah-gui#List' without gui attribute and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(),
            Some(guiElementList)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementList.value,
              scala.collection.immutable.List(),
              scala.collection.immutable.List("hlist")
            )
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#List' with too many gui attributes 'min=1.0','hlist=<http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA>' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeMin, guiAttributeHlist),
            Some(guiElementList)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementList.value,
              scala.collection.immutable.List("min", "hlist"),
              scala.collection.immutable.List("hlist")
            )
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#List' with misfitting gui attribute 'size=80' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeSize),
            Some(guiElementList)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementList.value,
              scala.collection.immutable.List("size"),
              scala.collection.immutable.List("hlist")
            )
          )
        )
      )
    }
  )

  private val guiObjectRadioTest = suite("gui object - Radio (mandatory gui attribute)")(
    test(
      "pass gui element 'salsah-gui#Radio' with gui attribute 'hlist' and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(guiAttributeHlist),
          Some(guiElementRadio)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List(guiAttributeHlist)) &&
      assertTrue(guiObject.guiElement == Some(guiElementRadio))
    },
    test(
      "pass gui element 'salsah-gui#Radio' without gui attribute and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(),
            Some(guiElementRadio)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementRadio.value,
              scala.collection.immutable.List(),
              scala.collection.immutable.List("hlist")
            )
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#Radio' with too many gui attributes 'min=1.0','hlist=<http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA>' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeMin, guiAttributeHlist),
            Some(guiElementRadio)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementRadio.value,
              scala.collection.immutable.List("min", "hlist"),
              scala.collection.immutable.List("hlist")
            )
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#Radio' with misfitting gui attribute 'size=80' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeSize),
            Some(guiElementRadio)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementRadio.value,
              scala.collection.immutable.List("size"),
              scala.collection.immutable.List("hlist")
            )
          )
        )
      )
    }
  )

  private val guiObjectPulldownTest = suite("gui object - Pulldown (mandatory gui attribute)")(
    test(
      "pass gui element 'salsah-gui#Pulldown' with gui attribute 'hlist' and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(guiAttributeHlist),
          Some(guiElementPulldown)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List(guiAttributeHlist)) &&
      assertTrue(guiObject.guiElement == Some(guiElementPulldown))
    },
    test(
      "pass gui element 'salsah-gui#Pulldown' without gui attribute and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(),
            Some(guiElementPulldown)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementPulldown.value,
              scala.collection.immutable.List(),
              scala.collection.immutable.List("hlist")
            )
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#Pulldown' with too many gui attributes 'min=1.0','hlist=<http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA>' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeMin, guiAttributeHlist),
            Some(guiElementPulldown)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementPulldown.value,
              scala.collection.immutable.List("min", "hlist"),
              scala.collection.immutable.List("hlist")
            )
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#Pulldown' with misfitting gui attribute 'size=80' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeSize),
            Some(guiElementPulldown)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementPulldown.value,
              scala.collection.immutable.List("size"),
              scala.collection.immutable.List("hlist")
            )
          )
        )
      )
    }
  )

  private val guiObjectSliderTest = suite("gui object - Slider (mandatory gui attribute)")(
    test(
      "pass gui element 'salsah-gui#Slider' with gui attributes 'min=1.0' and 'max=10.0' and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(guiAttributeMin, guiAttributeMax),
          Some(guiElementSlider)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List(guiAttributeMin, guiAttributeMax)) &&
      assertTrue(guiObject.guiElement == Some(guiElementSlider))
    },
    test(
      "pass gui element 'salsah-gui#Slider' without gui attribute and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(),
            Some(guiElementSlider)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementSlider.value,
              scala.collection.immutable.List(),
              scala.collection.immutable.List("min", "max")
            )
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#Slider' with too many gui attributes 'min=1.0','max=10.0', and 'size=80' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeMin, guiAttributeMax, guiAttributeSize),
            Some(guiElementSlider)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementSlider.value,
              scala.collection.immutable.List("min", "max", "size"),
              scala.collection.immutable.List("min", "max")
            )
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#Slider' with duplicate gui attributes 'min=1.0','max=10.0', and 'min=1.0' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeMin, guiAttributeMax, guiAttributeMin),
            Some(guiElementSlider)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementSlider.value,
              scala.collection.immutable.List("min", "max", "min"),
              scala.collection.immutable.List("min", "max")
            )
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#Slider' with misfitting gui attribute 'size=80' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeSize),
            Some(guiElementSlider)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementSlider.value,
              scala.collection.immutable.List("size"),
              scala.collection.immutable.List("min", "max")
            )
          )
        )
      )
    }
  )

  private val guiObjectSpinboxTest = suite("gui object - Spinbox (optional gui attributes)")(
    test(
      "pass gui element 'salsah-gui#Spinbox' without gui attributes and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(),
          Some(guiElementSpinbox)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List()) &&
      assertTrue(guiObject.guiElement == Some(guiElementSpinbox))
    },
    test(
      "pass gui element 'salsah-gui#Spinbox' with one gui attribute 'min=1.0' and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(guiAttributeMin),
          Some(guiElementSpinbox)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List(guiAttributeMin)) &&
      assertTrue(guiObject.guiElement == Some(guiElementSpinbox))
    },
    test(
      "pass gui element 'salsah-gui#Spinbox' with two gui attributes 'min=1.0', 'max=10.0' and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(guiAttributeMin, guiAttributeMax),
          Some(guiElementSpinbox)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List(guiAttributeMin, guiAttributeMax)) &&
      assertTrue(guiObject.guiElement == Some(guiElementSpinbox))
    },
    test(
      "pass gui element 'salsah-gui#Spinbox' with too many gui attributes 'min=1.0', 'max=10.0', 'size=80' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeMin, guiAttributeMax, guiAttributeSize),
            Some(guiElementSpinbox)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementSpinbox.value,
              scala.collection.immutable.List("min", "max", "size"),
              scala.collection.immutable.List("min", "max")
            )
          )
        )
      )

    }
  )

  private val guiObjectSimpleTextTest = suite("gui object - SimpleText (optional gui attributes)")(
    test(
      "pass gui element 'salsah-gui#SimpleText' without gui attributes and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(),
          Some(guiElementSimpleText)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List()) &&
      assertTrue(guiObject.guiElement == Some(guiElementSimpleText))
    },
    test(
      "pass gui element 'salsah-gui#SimpleText' with one gui attribute 'size=80' and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(guiAttributeSize),
          Some(guiElementSimpleText)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List(guiAttributeSize)) &&
      assertTrue(guiObject.guiElement == Some(guiElementSimpleText))
    },
    test(
      "pass gui element 'salsah-gui#SimpleText' with two gui attributes 'size=80', 'maxlength=80' and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(guiAttributeSize, guiAttributeMaxlength),
          Some(guiElementSimpleText)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List(guiAttributeSize, guiAttributeMaxlength)) &&
      assertTrue(guiObject.guiElement == Some(guiElementSimpleText))
    },
    test(
      "pass gui element 'salsah-gui#SimpleText' with too many gui attributes 'size=80', 'maxlength=80', 'max=10.0' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeSize, guiAttributeMaxlength, guiAttributeMax),
            Some(guiElementSimpleText)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementSimpleText.value,
              scala.collection.immutable.List("size", "maxlength", "max"),
              scala.collection.immutable.List("size", "maxlength")
            )
          )
        )
      )
    }
  )

  private val guiObjectTextareaTest = suite("gui object - Textarea (optional gui attributes)")(
    test(
      "pass gui element 'salsah-gui#Textarea' without gui attributes and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(),
          Some(guiElementTextarea)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List()) &&
      assertTrue(guiObject.guiElement == Some(guiElementTextarea))
    },
    test(
      "pass gui element 'salsah-gui#Textarea' with four gui attributes 'width=80%', 'cols=100', 'rows=10', 'wrap=soft' and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(guiAttributeCols, guiAttributeRows, guiAttributeWidth, guiAttributeWrap),
          Some(guiElementTextarea)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(
        guiObject.guiAttributes == scala.collection.immutable
          .List(guiAttributeCols, guiAttributeRows, guiAttributeWidth, guiAttributeWrap)
      ) &&
      assertTrue(guiObject.guiElement == Some(guiElementTextarea))
    },
    test(
      "pass gui element 'salsah-gui#Textarea' with two gui attributes 'cols=100', 'rows=10' and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(guiAttributeCols, guiAttributeRows),
          Some(guiElementTextarea)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List(guiAttributeCols, guiAttributeRows)) &&
      assertTrue(guiObject.guiElement == Some(guiElementTextarea))
    },
    test(
      "pass gui element 'salsah-gui#Textarea' with wrong gui attribute 'size=80' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeSize),
            Some(guiElementTextarea)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementTextarea.value,
              scala.collection.immutable.List("size"),
              scala.collection.immutable.List("cols", "rows", "width", "wrap")
            )
          )
        )
      )
    }
  )

  private val guiObjectSearchboxTest = suite("gui object - Searchbox (optional gui attributes)")(
    test(
      "pass gui element 'salsah-gui#Searchbox' without gui attributes and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(),
          Some(guiElementSearchbox)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List()) &&
      assertTrue(guiObject.guiElement == Some(guiElementSearchbox))
    },
    test(
      "pass gui element 'salsah-gui#Searchbox' with a gui attribute 'numprops=3' and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(guiAttributeNumprops),
          Some(guiElementSearchbox)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(
        guiObject.guiAttributes == scala.collection.immutable
          .List(guiAttributeNumprops)
      ) &&
      assertTrue(guiObject.guiElement == Some(guiElementSearchbox))
    },
    test(
      "pass gui element 'salsah-gui#Searchbox' with wrong gui attribute 'size=80' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeSize),
            Some(guiElementSearchbox)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementSearchbox.value,
              scala.collection.immutable.List("size"),
              scala.collection.immutable.List("numprops")
            )
          )
        )
      )
    }
  )

  private val guiObjectColorpickerTest = suite("gui object - Colorpicker (optional gui attributes)")(
    test(
      "pass gui element 'salsah-gui#Colorpicker' without gui attributes and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(),
          Some(guiElementColorpicker)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List()) &&
      assertTrue(guiObject.guiElement == Some(guiElementColorpicker))
    },
    test(
      "pass gui element 'salsah-gui#Colorpicker' with a gui attribute 'ncolors=12' and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(guiAttributeNcolors),
          Some(guiElementColorpicker)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(
        guiObject.guiAttributes == scala.collection.immutable
          .List(guiAttributeNcolors)
      ) &&
      assertTrue(guiObject.guiElement == Some(guiElementColorpicker))
    },
    test(
      "pass gui element 'salsah-gui#Colorpicker' with wrong gui attribute 'size=80' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeSize),
            Some(guiElementColorpicker)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementColorpicker.value,
              scala.collection.immutable.List("size"),
              scala.collection.immutable.List("ncolors")
            )
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#Colorpicker' with duplicate gui attributes 'ncolors=12', 'ncolors=12' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeNcolors, guiAttributeNcolors),
            Some(guiElementColorpicker)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeWrong(
              guiElementColorpicker.value,
              scala.collection.immutable.List("ncolors", "ncolors"),
              scala.collection.immutable.List("ncolors")
            )
          )
        )
      )
    }
  )

  private val guiObjectCheckboxTest = suite("gui object - Checkbox (no gui attributes allowed)")(
    test(
      "pass gui element 'salsah-gui#Checkbox' without gui attribute and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(),
          Some(guiElementCheckbox)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List()) &&
      assertTrue(guiObject.guiElement == Some(guiElementCheckbox))
    },
    test(
      "pass gui element 'salsah-gui#Checkbox' with gui attribute 'min=1.0' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeMin),
            Some(guiElementCheckbox)
          ) == Validation.fail(
          ValidationException(
            SchemaErrorMessages.GuiAttributeNotEmpty
          )
        )
      )
    }
  )
}
