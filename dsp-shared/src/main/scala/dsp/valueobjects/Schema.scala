/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.constants.SalsahGui
import dsp.errors.BadRequestException
import zio.prelude.Subtype
import zio.prelude.Validation

object Schema {

  /**
   * GuiObject value object.
   */
  sealed abstract case class GuiObject private (guiAttributes: List[GuiAttribute], guiElement: Option[GuiElement])
  object GuiObject {
    def make(guiAttributes: List[GuiAttribute], guiElement: Option[GuiElement]): Validation[Throwable, GuiObject] = {

      // check that there are no duplicated gui attributes
      val guiAttributeKeys: List[String] = guiAttributes.map { guiAttribute: GuiAttribute => guiAttribute.k }
      if (guiAttributeKeys.toSet.size < guiAttributes.size) {
        return Validation.fail(
          BadRequestException(
            s"Duplicate GUI attributes for salsah-gui:guiElement $guiElement."
          )
        )
      }

      // If the GUI element is a list, radio, pulldown or slider, check if a GUI attribute (which is mandatory in these cases) is provided
      val guiElementList     = SalsahGui.List
      val guiElementRadio    = SalsahGui.Radio
      val guiElementPulldown = SalsahGui.Pulldown
      val guiElementSlider   = SalsahGui.Slider

      val needsGuiAttribute: Boolean = guiElement match {
        case None => false
        case Some(guiElement) =>
          guiElement.value == guiElementList ||
            guiElement.value == guiElementRadio ||
            guiElement.value == guiElementPulldown ||
            guiElement.value == guiElementSlider
      }

      if (
        needsGuiAttribute &&
        guiAttributes.isEmpty
      ) {
        return Validation.fail(BadRequestException(SchemaErrorMessages.GuiAttributesMissing))
      }

      val guiElementsPointingToList: Set[SalsahGui.IRI] = Set(guiElementList, guiElementRadio, guiElementPulldown)

      if (needsGuiAttribute) {
        guiElement match {
          // GUI element is a list, radio or pulldown, so it needs a GUI attribute that points to a list
          case Some(guiElement) if guiElementsPointingToList.contains(guiElement.value) =>
            if (guiAttributes.size != 1) {
              return Validation.fail(
                BadRequestException(
                  s"Too many GUI attributes. salsah-gui:guiElement $guiElement needs a salsah-gui:guiAttribute referencing a list of the form 'hlist=<LIST_IRI>', but found $guiAttributes."
                )
              )
            } else {
              guiAttributes.map { guiAttribute: GuiAttribute =>
                if (guiAttribute.k != ("hlist")) {
                  return Validation.fail(
                    BadRequestException(
                      s"salsah-gui:guiAttribute for salsah-gui:guiElement $guiElement has to be a list reference of the form 'hlist=<LIST_IRI>', but found $guiAttribute."
                    )
                  )
                }
              }
            }
          // GUI element is a slider, so it needs two GUI attributes min and max
          case Some(guiElement) if guiElement.value == guiElementSlider =>
            if (guiAttributes.size != 2) {
              return Validation.fail(
                BadRequestException(
                  s"Wrong number of GUI attributes. salsah-gui:guiElement $guiElement needs 2 salsah-gui:guiAttribute 'min' and 'max', but found ${guiAttributes.size}: $guiAttributes."
                )
              )
            }
            guiAttributes.map { guiAttribute: GuiAttribute =>
              if (guiAttribute.k != ("max") && guiAttribute.k != ("min")) {
                return Validation.fail(
                  BadRequestException(
                    s"Incorrect GUI attributes. salsah-gui:guiElement $guiElement needs two salsah-gui:guiAttribute 'min' and 'max', but found $guiAttributes."
                  )
                )
              }
            }

          case _ =>
            return Validation.fail(
              BadRequestException(
                s"Unknown value for salsah-gui:guiElement: $guiElement."
              )
            )
        }
      }

      Validation.succeed(new GuiObject(guiAttributes, guiElement) {})
    }
  }

  /**
   * GuiAttribute value object.
   *
   * @param k the key parameter of the gui attribute ('min', 'hlist', 'size' etc.)
   * @param v the value parameter of the gui attribute (an int, a list's IRI etc.)
   */
  sealed abstract case class GuiAttribute private (k: String, v: String) {
    val value = k + "=" + v
  }

  object GuiAttribute {
    def make(keyValue: String): Validation[Throwable, GuiAttribute] = {
      val k: String = keyValue.split("=").head.trim()
      val v: String =
        keyValue.split("=").last.trim() // TODO also check the type of the value (integer, string etc.)

      if (keyValue.isEmpty) {
        Validation.fail(BadRequestException(SchemaErrorMessages.GuiAttributeMissing))
      } else if (!SalsahGui.GuiAttributes.contains(k)) {
        Validation.fail(BadRequestException(SchemaErrorMessages.GuiAttributeUnknown))
      } else {
        Validation.succeed(new GuiAttribute(k, v) {})
      }
    }

  }

  /**
   * GuiElement value object.
   */
  sealed abstract case class GuiElement private (value: String)
  object GuiElement {
    def make(value: String): Validation[Throwable, GuiElement] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(SchemaErrorMessages.GuiElementMissing))
      } else if (!SalsahGui.GuiElements.contains(value)) {
        Validation.fail(BadRequestException(SchemaErrorMessages.GuiElementUnknown))
      } else {
        Validation.succeed(new GuiElement(value) {})
      }
  }

}

object SchemaErrorMessages {
  val PropertyLabelMissing       = "Property label cannot be empty."
  val PropertyLabelInvalid       = "Property label is invalid."
  val PropertyDescriptionMissing = "Property description cannot be empty."
  val PropertyDescriptionInvalid = "Property description is invalid."
  val ClassLabelMissing          = "Class label cannot be empty."
  val ClassLabelInvalid          = "Class label is invalid."
  val ClassDescriptionMissing    = "Class description cannot be empty."
  val ClassDescriptionInvalid    = "Class description is invalid."
  val GuiAttributeMissing        = "GUI attribute cannot be empty."
  val GuiAttributeUnknown        = s"GUI attribute is unknown. Needs to be one of ${SalsahGui.GuiAttributes}"
  val GuiElementMissing          = "GUI element cannot be empty."
  val GuiElementInvalid          = "GUI element is invalid."
  val GuiElementUnknown          = s"GUI element is unknown. Needs to be one of ${SalsahGui.GuiElements}"
  val GuiObjectMissing           = "GUI object cannot be empty."
  val GuiAttributesMissing       = "GUI attributes cannot be empty."
}
