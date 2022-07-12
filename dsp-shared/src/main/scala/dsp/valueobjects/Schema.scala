/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation
import dsp.errors.BadRequestException
import dsp.constants.SalsahGuiConstants._
import zio.prelude.Subtype
import dsp.constants.SalsahGuiConstants

object Schema {

  /**
   * GuiObject value object.
   */
  sealed abstract case class GuiObject private (guiAttributes: List[GuiAttribute], guiElement: Option[GuiElement])
  object GuiObject {
    def make(guiAttributes: List[GuiAttribute], guiElement: Option[GuiElement]): Validation[Throwable, GuiObject] = {
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

      val guiElementsPointingToList: Set[IRI] = Set(guiElementList, guiElementRadio, guiElementPulldown)

      if (needsGuiAttribute) {
        guiElement match {
          // GUI element is a list, radio or pulldown, so it needs a GUI attribute that points to a list
          case Some(guiElement) if guiElementsPointingToList.contains(guiElement.value) =>
            guiAttributes.map { guiAttribute: GuiAttribute =>
              if (!guiAttribute.value.startsWith("hlist=")) {
                return Validation.fail(
                  BadRequestException(
                    s"salsah-gui:guiAttribute for salsah-gui:guiElement $guiElement has to be a list reference of the form 'hlist=<LIST_IRI>' but found $guiAttribute"
                  )
                )
              }
            }
          // GUI element is a slider, so it needs two GUI attributes min and max
          case Some(guiElement) if guiElement.value == guiElementSlider =>
            if (guiAttributes.size != 2) {
              return Validation.fail(
                BadRequestException(
                  s"Wrong number of GUI attributes. salsah-gui:guiElement $guiElement needs two salsah-gui:guiAttribute 'min' and 'max', but ${guiAttributes.size} were provided."
                )
              )
            }
            guiAttributes.map { guiAttribute: GuiAttribute =>
              if (!guiAttribute.value.startsWith("max=") && !guiAttribute.value.startsWith("min=")) {
                return Validation.fail(
                  BadRequestException(
                    s"Incorrect GUI attributes. salsah-gui:guiElement $guiElement needs two salsah-gui:guiAttribute 'min' and 'max', but found $guiAttribute"
                  )
                )
              }
            }
            val guiAttrs: List[String] = guiAttributes.map { guiAttribute: GuiAttribute =>
              guiAttribute.value.split("=").head
            }
            if (guiAttributes.toSet.size != 2) {
              return Validation.fail(
                BadRequestException(
                  s"Duplicate GUI attributes. salsah-gui:guiElement $guiElement needs two salsah-gui:guiAttribute 'min' and 'max'."
                )
              )
            }

          case _ =>
            return Validation.fail(
              BadRequestException(
                s"Unknown GUI element (salsah-gui:guiElement) provided"
              )
            )
        }
      }

      Validation.succeed(new GuiObject(guiAttributes, guiElement) {})
    }
  }

  /**
   * GuiAttribute value object.
   */
  sealed abstract case class GuiAttribute private (value: String)
  object GuiAttribute {
    def make(value: String): Validation[Throwable, GuiAttribute] = {
      val guiAttribute      = value.split("=").head.trim()
      val guiAttributeValue = value.split("=").last.trim()
      if (value.isEmpty) {
        Validation.fail(BadRequestException(SchemaErrorMessages.GuiAttributeMissing))
      } else if (!SalsahGuiConstants.SalsahGui.SalsahGuiAttribute.valueMap.contains(guiAttribute)) {
        Validation.fail(BadRequestException(SchemaErrorMessages.GuiAttributeUnknown))
      } else {
        Validation.succeed(new GuiAttribute(value.replace(" ", "")) {})
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
      } else if (!SalsahGuiConstants.SalsahGui.SalsahGuiElement.valueMap.contains(value)) {
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
  val GuiAttributeUnknown =
    s"GUI attribute is unknown. Needs to be one of ${SalsahGuiConstants.SalsahGui.SalsahGuiAttribute.valueMap}"
  val GuiElementMissing = "GUI element cannot be empty."
  val GuiElementInvalid = "GUI element is invalid."
  val GuiElementUnknown =
    s"GUI element is unknown. Needs to be one of ${SalsahGuiConstants.SalsahGui.SalsahGuiElement.valueMap}"
  val GuiObjectMissing     = "GUI object cannot be empty."
  val GuiAttributesMissing = "GUI attributes cannot be empty."
}
