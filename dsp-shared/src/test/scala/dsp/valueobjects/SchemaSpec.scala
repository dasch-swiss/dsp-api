/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.errors.BadRequestException
import dsp.valueobjects.User._
import zio.prelude.Validation
import zio.test._

/**
 * This spec is used to test the [[dsp.valueobjects.User]] value objects creation.
 */
object SchemaSpec extends ZIOSpecDefault {

  private val guiElementUnknownIri  = "http://www.knora.org/ontology/salsah-gui#Unknown"
  private val guiElementListIri     = "http://www.knora.org/ontology/salsah-gui#List"
  private val guiElementPulldownIri = "http://www.knora.org/ontology/salsah-gui#Pulldown"
  private val guiElementRadioIri    = "http://www.knora.org/ontology/salsah-gui#Radio"
  private val guiElementSliderIri   = "http://www.knora.org/ontology/salsah-gui#Slider"

  private val guiAttributeInvalidString            = "invalid"
  private val guiAttributeUnknownString            = "unknown=10"
  private val guiAttributeSizeString               = "size=80"
  private val guiAttributeSizeStringKey            = "size"
  private val guiAttributeSizeStringValue          = "80"
  private val guiAttributeSizeStringWithWhitespace = "size    =  80 "
  private val guiAttributeHlistString              = "hlist=http://rdfh.ch/lists/082F/PbRLUy66TsK10qNP1mBwzA"
  private val guiAttributeMinString                = "min=1"
  private val guiAttributeMaxString                = "max=10"

  private val guiAttributeSize  = Schema.GuiAttribute.make(guiAttributeSizeString).fold(e => throw e.head, v => v)
  private val guiAttributeHlist = Schema.GuiAttribute.make(guiAttributeHlistString).fold(e => throw e.head, v => v)
  private val guiAttributeMin   = Schema.GuiAttribute.make(guiAttributeMinString).fold(e => throw e.head, v => v)
  private val guiAttributeMax   = Schema.GuiAttribute.make(guiAttributeMaxString).fold(e => throw e.head, v => v)

  private val guiElementList     = Schema.GuiElement.make(guiElementListIri).fold(e => throw e.head, v => v)
  private val guiElementPulldown = Schema.GuiElement.make(guiElementPulldownIri).fold(e => throw e.head, v => v)
  private val guiElementRadio    = Schema.GuiElement.make(guiElementRadioIri).fold(e => throw e.head, v => v)
  private val guiElementSlider   = Schema.GuiElement.make(guiElementSliderIri).fold(e => throw e.head, v => v)

  def spec =
    (guiAttributeTest + guiElementTest + guiObjectTest + guiObjectListTest + guiObjectRadioTest + guiObjectPulldownTest + guiObjectSliderTest)

  private val guiAttributeTest = suite("gui attribute")(
    test("pass an empty value and return an error") {
      assertTrue(
        Schema.GuiAttribute.make("") == Validation.fail(BadRequestException(SchemaErrorMessages.GuiAttributeMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        Schema.GuiAttribute.make(guiAttributeInvalidString) == Validation.fail(
          BadRequestException(SchemaErrorMessages.GuiAttributeUnknown)
        )
      )
    },
    test("pass an unknown value and return an error") {
      assertTrue(
        Schema.GuiAttribute.make(guiAttributeUnknownString) == Validation.fail(
          BadRequestException(SchemaErrorMessages.GuiAttributeUnknown)
        )
      )
    },
    test("pass a valid value with whitespace and successfully create value object") {
      assertTrue(
        Schema.GuiAttribute
          .make(guiAttributeSizeStringWithWhitespace)
          .toOption
          .get
          .k == guiAttributeSizeStringKey
      ) &&
      assertTrue(
        Schema.GuiAttribute
          .make(guiAttributeSizeStringWithWhitespace)
          .toOption
          .get
          .v == guiAttributeSizeStringValue
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Schema.GuiAttribute.make(guiAttributeSizeString).toOption.get.k == guiAttributeSizeStringKey) &&
      assertTrue(Schema.GuiAttribute.make(guiAttributeSizeString).toOption.get.v == guiAttributeSizeStringValue)
    }
  )

  private val guiElementTest = suite("gui element")(
    test("pass an empty value and return an error") {
      assertTrue(
        Schema.GuiElement.make("") == Validation.fail(BadRequestException(SchemaErrorMessages.GuiElementMissing))
      )
    },
    test("pass an unknown value and return an error") {
      assertTrue(
        Schema.GuiElement.make(guiElementUnknownIri) == Validation.fail(
          BadRequestException(SchemaErrorMessages.GuiElementUnknown)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Schema.GuiElement.make(guiElementListIri).toOption.get.value == guiElementListIri)
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
          BadRequestException(
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
      (for {
        guiObject <- Schema.GuiObject
                       .make(
                         scala.collection.immutable.List(guiAttributeHlist),
                         Some(guiElementList)
                       )
      } yield assertTrue(
        guiObject.guiAttributes == scala.collection.immutable.List(guiAttributeHlist)
      ) &&
        assertTrue(
          guiObject.guiElement == Some(guiElementList)
        )).toZIO
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
          BadRequestException(
            "gui attributes cannot be empty."
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
          BadRequestException(
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
          BadRequestException(
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
      (for {
        guiObject <- Schema.GuiObject
                       .make(
                         scala.collection.immutable.List(guiAttributeHlist),
                         Some(guiElementRadio)
                       )
      } yield assertTrue(
        guiObject.guiAttributes == scala.collection.immutable.List(guiAttributeHlist)
      ) &&
        assertTrue(
          guiObject.guiElement == Some(guiElementRadio)
        )).toZIO
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
          BadRequestException(
            "gui attributes cannot be empty."
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
          BadRequestException(
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
          BadRequestException(
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
      (for {
        guiObject <- Schema.GuiObject
                       .make(
                         scala.collection.immutable.List(guiAttributeHlist),
                         Some(guiElementPulldown)
                       )
      } yield assertTrue(
        guiObject.guiAttributes == scala.collection.immutable.List(guiAttributeHlist)
      ) &&
        assertTrue(
          guiObject.guiElement == Some(guiElementPulldown)
        )).toZIO
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
          BadRequestException(
            "gui attributes cannot be empty."
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
          BadRequestException(
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
          BadRequestException(
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
      (for {
        guiObject <- Schema.GuiObject
                       .make(
                         scala.collection.immutable.List(guiAttributeMin, guiAttributeMax),
                         Some(guiElementSlider)
                       )
      } yield assertTrue(
        guiObject.guiAttributes == scala.collection.immutable.List(guiAttributeMin, guiAttributeMax)
      ) &&
        assertTrue(
          guiObject.guiElement == Some(guiElementSlider)
        )).toZIO
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
          BadRequestException(
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
          BadRequestException(
            "Wrong number of gui attributes. salsah-gui:guiElement GuiElement(http://www.knora.org/ontology/salsah-gui#Slider) needs 2 salsah-gui:guiAttribute 'min' and 'max', but found 1: List(GuiAttribute(size,80))."
          )
        )
      )
    }
  )
}
