/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.constants.SalsahGui
import dsp.errors.ValidationException
import dsp.valueobjects.User._
import zio.prelude.Validation
import zio.test._

/**
 * This spec is used to test the [[dsp.valueobjects.User]] value objects creation.
 */
object SchemaSpec extends ZIOSpecDefault {

  private val guiAttributeSize = Schema.GuiAttribute.make("size=80").fold(e => throw e.head, v => v)
  private val guiAttributeHlist =
    Schema.GuiAttribute
      .make("hlist=http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA")
      .fold(e => throw e.head, v => v)
  private val guiAttributeMin = Schema.GuiAttribute.make("min=1").fold(e => throw e.head, v => v)
  private val guiAttributeMax = Schema.GuiAttribute.make("max=10").fold(e => throw e.head, v => v)

  private val guiElementList     = Schema.GuiElement.make(SalsahGui.List).fold(e => throw e.head, v => v)
  private val guiElementPulldown = Schema.GuiElement.make(SalsahGui.Pulldown).fold(e => throw e.head, v => v)
  private val guiElementRadio    = Schema.GuiElement.make(SalsahGui.Radio).fold(e => throw e.head, v => v)
  private val guiElementSlider   = Schema.GuiElement.make(SalsahGui.Slider).fold(e => throw e.head, v => v)
  private val guiElementCheckbox = Schema.GuiElement.make(SalsahGui.Checkbox).fold(e => throw e.head, v => v)

  def spec = (
    guiAttributeTest +
      guiElementTest +
      guiObjectTest +
      validateGuiObjectsPointingToListTest +
      validateGuiObjectSliderTest +
      guiObjectListTest +
      guiObjectRadioTest +
      guiObjectPulldownTest +
      guiObjectSliderTest +
      guiObjectCheckboxTest
  )

  private val guiAttributeTest = suite("gui attribute")(
    test("pass an empty value and return an error") {
      assertTrue(
        Schema.GuiAttribute.make("") == Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributeMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        Schema.GuiAttribute.make("invalid") == Validation.fail(
          ValidationException(SchemaErrorMessages.GuiAttributeUnknown)
        )
      )
    },
    test("pass an unknown value and return an error") {
      assertTrue(
        Schema.GuiAttribute.make("unknown=10") == Validation.fail(
          ValidationException(SchemaErrorMessages.GuiAttributeUnknown)
        )
      )
    },
    test("pass a valid value with whitespace and successfully create value object") {
      val guiAttributeWithWhitespace = "  size    =  80 "
      assertTrue(Schema.GuiAttribute.make(guiAttributeWithWhitespace).toOption.get.k == "size") &&
      assertTrue(Schema.GuiAttribute.make(guiAttributeWithWhitespace).toOption.get.v == "80")
    },
    test("pass a valid value and successfully create value object") {
      val guiAttributeSizeString = "size=80"
      assertTrue(Schema.GuiAttribute.make(guiAttributeSizeString).toOption.get.k == "size") &&
      assertTrue(Schema.GuiAttribute.make(guiAttributeSizeString).toOption.get.v == "80") &&
      assertTrue(Schema.GuiAttribute.make(guiAttributeSizeString).toOption.get.value == "size=80")
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
          ValidationException(SchemaErrorMessages.GuiElementUnknown)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Schema.GuiElement.make(SalsahGui.List).toOption.get.value == SalsahGui.List)
    }
  )

  private val validateGuiObjectsPointingToListTest = suite("validateGuiObjectsPointingToList")(
    test(
      "pass gui element 'salsah-gui#List' with gui attribute 'hlist' and successfully create value object"
    ) {
      val guiElement    = guiElementList
      val guiAttributes = scala.collection.immutable.List(guiAttributeHlist)
      val result        = Schema.validateGuiObjectsPointingToList(guiElement, guiAttributes)
      assertTrue(result == Validation.succeed(guiAttributes))
    },
    test(
      "pass a gui element that points to a list but has a misfitting gui attribute and return an error"
    ) {
      val guiElement    = guiElementList
      val guiAttributes = scala.collection.immutable.List(guiAttributeSize)
      assertTrue(
        Schema.validateGuiObjectsPointingToList(guiElement, guiAttributes) == Validation.fail(
          ValidationException(
            "salsah-gui:guiAttribute for salsah-gui:guiElement GuiElement(http://www.knora.org/ontology/salsah-gui#List) has to be a list reference of the form 'hlist=<LIST_IRI>', but found GuiAttribute(size,80)."
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#List' with too many gui attributes 'min=1','hlist=http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA' and return an error"
    ) {
      val guiElement    = guiElementList
      val guiAttributes = scala.collection.immutable.List(guiAttributeMin, guiAttributeHlist)
      assertTrue(
        Schema.validateGuiObjectsPointingToList(guiElement, guiAttributes) == Validation.fail(
          ValidationException(
            "Wrong number of gui attributes. salsah-gui:guiElement GuiElement(http://www.knora.org/ontology/salsah-gui#List) needs a salsah-gui:guiAttribute referencing a list of the form 'hlist=<LIST_IRI>', but found List(GuiAttribute(min,1), GuiAttribute(hlist,http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA))."
          )
        )
      )
    }
  )

  private val validateGuiObjectSliderTest = suite("validateGuiObjectSlider")(
    test(
      "pass gui element 'salsah-gui#Slider' with gui attributes 'min=1' and 'max=10' and successfully create value object"
    ) {
      val guiElement    = guiElementSlider
      val guiAttributes = scala.collection.immutable.List(guiAttributeMin, guiAttributeMax)
      val result        = Schema.validateGuiObjectSlider(guiElement, guiAttributes)
      assertTrue(
        result == Validation.succeed(guiAttributes)
      )
    },
    test(
      "pass gui element 'salsah-gui#Slider' with too many gui attributes 'min=1','max=10', and 'min=80' and return an error"
    ) {
      val guiElement    = guiElementSlider
      val guiAttributes = scala.collection.immutable.List(guiAttributeMin, guiAttributeMax, guiAttributeSize)
      assertTrue(
        Schema.validateGuiObjectSlider(guiElement, guiAttributes) == Validation.fail(
          ValidationException(
            "Wrong number of gui attributes. salsah-gui:guiElement GuiElement(http://www.knora.org/ontology/salsah-gui#Slider) needs 2 salsah-gui:guiAttribute 'min' and 'max', but found 3: List(GuiAttribute(min,1), GuiAttribute(max,10), GuiAttribute(size,80))."
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#Slider' with too many gui attributes 'min=1','hlist=http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA' and return an error"
    ) {
      val guiElement    = guiElementSlider
      val guiAttributes = scala.collection.immutable.List(guiAttributeSize)
      assertTrue(
        Schema.validateGuiObjectSlider(guiElement, guiAttributes) == Validation.fail(
          ValidationException(
            "Wrong number of gui attributes. salsah-gui:guiElement GuiElement(http://www.knora.org/ontology/salsah-gui#Slider) needs 2 salsah-gui:guiAttribute 'min' and 'max', but found 1: List(GuiAttribute(size,80))."
          )
        )
      )
    }
  )

  private val guiObjectTest = suite("gui object")(
    test(
      "pass valid gui element with duplicated gui attributes and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeMin, guiAttributeMin),
            Some(guiElementSlider)
          ) == Validation.fail(
          ValidationException(
            "Duplicate gui attributes for salsah-gui:guiElement Some(GuiElement(http://www.knora.org/ontology/salsah-gui#Slider))."
          )
        )
      )
    }
  )

  private val guiObjectListTest = suite("gui object - List")(
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
            SchemaErrorMessages.GuiAttributesMissing
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#List' with too many gui attributes 'min=1','hlist=http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeMin, guiAttributeHlist),
            Some(guiElementList)
          ) == Validation.fail(
          ValidationException(
            "Wrong number of gui attributes. salsah-gui:guiElement GuiElement(http://www.knora.org/ontology/salsah-gui#List) needs a salsah-gui:guiAttribute referencing a list of the form 'hlist=<LIST_IRI>', but found List(GuiAttribute(min,1), GuiAttribute(hlist,http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA))."
          )
        )
      )
    },
    test("pass gui element 'salsah-gui#List' with misfitting gui attribute 'size=80' and return an error") {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeSize),
            Some(guiElementList)
          ) == Validation.fail(
          ValidationException(
            "salsah-gui:guiAttribute for salsah-gui:guiElement GuiElement(http://www.knora.org/ontology/salsah-gui#List) has to be a list reference of the form 'hlist=<LIST_IRI>', but found GuiAttribute(size,80)."
          )
        )
      )
    }
  )

  private val guiObjectRadioTest = suite("gui object - Radio")(
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
            SchemaErrorMessages.GuiAttributesMissing
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#Radio' with too many gui attributes 'min=1','hlist=http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeMin, guiAttributeHlist),
            Some(guiElementRadio)
          ) == Validation.fail(
          ValidationException(
            "Wrong number of gui attributes. salsah-gui:guiElement GuiElement(http://www.knora.org/ontology/salsah-gui#Radio) needs a salsah-gui:guiAttribute referencing a list of the form 'hlist=<LIST_IRI>', but found List(GuiAttribute(min,1), GuiAttribute(hlist,http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA))."
          )
        )
      )
    },
    test("pass gui element 'salsah-gui#Radio' with misfitting gui attribute 'size=80' and return an error") {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeSize),
            Some(guiElementRadio)
          ) == Validation.fail(
          ValidationException(
            "salsah-gui:guiAttribute for salsah-gui:guiElement GuiElement(http://www.knora.org/ontology/salsah-gui#Radio) has to be a list reference of the form 'hlist=<LIST_IRI>', but found GuiAttribute(size,80)."
          )
        )
      )
    }
  )

  private val guiObjectPulldownTest = suite("gui object - Pulldown")(
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
            SchemaErrorMessages.GuiAttributesMissing
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#Pulldown' with too many gui attributes 'min=1','hlist=http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeMin, guiAttributeHlist),
            Some(guiElementPulldown)
          ) == Validation.fail(
          ValidationException(
            "Wrong number of gui attributes. salsah-gui:guiElement GuiElement(http://www.knora.org/ontology/salsah-gui#Pulldown) needs a salsah-gui:guiAttribute referencing a list of the form 'hlist=<LIST_IRI>', but found List(GuiAttribute(min,1), GuiAttribute(hlist,http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA))."
          )
        )
      )
    },
    test("pass gui element 'salsah-gui#Pulldown' with misfitting gui attribute 'size=80' and return an error") {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeSize),
            Some(guiElementPulldown)
          ) == Validation.fail(
          ValidationException(
            "salsah-gui:guiAttribute for salsah-gui:guiElement GuiElement(http://www.knora.org/ontology/salsah-gui#Pulldown) has to be a list reference of the form 'hlist=<LIST_IRI>', but found GuiAttribute(size,80)."
          )
        )
      )
    }
  )

  private val guiObjectSliderTest = suite("gui object - Slider")(
    test(
      "pass gui element 'salsah-gui#Slider' with gui attributes 'min=1' and 'max=10' and successfully create value object"
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
            SchemaErrorMessages.GuiAttributesMissing
          )
        )
      )
    },
    test(
      "pass gui element 'salsah-gui#Slider' with too many gui attributes 'min=1','max=10', and 'min=80' and return an error"
    ) {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeMin, guiAttributeMax, guiAttributeSize),
            Some(guiElementSlider)
          ) == Validation.fail(
          ValidationException(
            "Wrong number of gui attributes. salsah-gui:guiElement GuiElement(http://www.knora.org/ontology/salsah-gui#Slider) needs 2 salsah-gui:guiAttribute 'min' and 'max', but found 3: List(GuiAttribute(min,1), GuiAttribute(max,10), GuiAttribute(size,80))."
          )
        )
      )
    },
    test("pass gui element 'salsah-gui#Slider' with misfitting gui attribute 'size=80' and return an error") {
      assertTrue(
        Schema.GuiObject
          .make(
            scala.collection.immutable.List(guiAttributeSize),
            Some(guiElementSlider)
          ) == Validation.fail(
          ValidationException(
            "Wrong number of gui attributes. salsah-gui:guiElement GuiElement(http://www.knora.org/ontology/salsah-gui#Slider) needs 2 salsah-gui:guiAttribute 'min' and 'max', but found 1: List(GuiAttribute(size,80))."
          )
        )
      )
    }
  )

  private val guiObjectCheckboxTest = suite("gui object - Checkbox")(
    test(
      "pass gui element 'salsah-gui#Checkbox' without gui attributes and successfully create value object"
    ) {
      val guiObject = Schema.GuiObject
        .make(
          scala.collection.immutable.List(),
          Some(guiElementCheckbox)
        )
        .fold(e => throw e.head, v => v)

      assertTrue(guiObject.guiAttributes == scala.collection.immutable.List()) &&
      assertTrue(guiObject.guiElement == Some(guiElementCheckbox))
    }
  )
}
