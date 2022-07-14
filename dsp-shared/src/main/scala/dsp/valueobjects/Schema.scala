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
            s"Duplicate gui attributes for salsah-gui:guiElement $guiElement."
          )
        )
      }

      // If the gui element is a list, radio, pulldown or slider, check if a gui attribute (which is mandatory in these cases) is provided
      val guiElementList     = SalsahGui.List
      val guiElementRadio    = SalsahGui.Radio
      val guiElementPulldown = SalsahGui.Pulldown
      val guiElementSlider   = SalsahGui.Slider

      val guiElementsPointingToList: Set[SalsahGui.IRI] = Set(guiElementList, guiElementRadio, guiElementPulldown)

      val needsGuiAttribute: Boolean = guiElement match {
        case None => false
        case Some(guiElement) =>
          guiElement.value == guiElementSlider ||
            guiElementsPointingToList.contains(guiElement.value)
      }

      if (needsGuiAttribute) {
        if (guiAttributes.isEmpty) {
          return Validation.fail(BadRequestException(SchemaErrorMessages.GuiAttributesMissing))
        }
        val validatedGuiAttributes: Validation[Throwable, List[GuiAttribute]] = guiElement match {
          // gui element is a list, radio or pulldown, so it needs a gui attribute that points to a list
          case Some(guiElement) if guiElementsPointingToList.contains(guiElement.value) =>
            validateGuiObjectsPointingToList(guiElement, guiAttributes).fold(
              e => Validation.fail(e.head),
              v => Validation.succeed(v)
            )

          // gui element is a slider, so it needs two gui attributes min and max
          case Some(guiElement) if guiElement.value == guiElementSlider =>
            validateGuiObjectSlider(guiElement, guiAttributes).fold(
              e => Validation.fail(e.head),
              v => Validation.succeed(v)
            )

          case _ =>
            Validation.fail(
              BadRequestException(
                s"Unable to validate gui attributes. Unknown value for salsah-gui:guiElement: $guiElement."
              )
            )
        }

        return validatedGuiAttributes.fold(
          e => Validation.fail(e.head),
          v => Validation.succeed(new GuiObject(v, guiElement) {})
        )

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

  /**
   * Validates if gui elements that require pointing to a list (List, Radio, Pulldown) actually point to a list
   *
   * @param guiElement the gui element that needs to be validated
   * @param guiAttributes the gui attributes that need to be validated
   *
   * @return either the validated list of gui attributes or a [[dsp.errors.BadRequestException]]
   */
  private[valueobjects] def validateGuiObjectsPointingToList(
    guiElement: GuiElement,
    guiAttributes: List[GuiAttribute]
  ): Validation[BadRequestException, List[GuiAttribute]] = {
    // gui element can have only one gui attribute
    if (guiAttributes.size > 1) {
      return Validation.fail(
        BadRequestException(
          s"Wrong number of gui attributes. salsah-gui:guiElement $guiElement needs a salsah-gui:guiAttribute referencing a list of the form 'hlist=<LIST_IRI>', but found $guiAttributes."
        )
      )
    }
    // gui attribute needs to point to a list
    if (guiAttributes.head.k != ("hlist")) {
      return Validation.fail(
        BadRequestException(
          s"salsah-gui:guiAttribute for salsah-gui:guiElement $guiElement has to be a list reference of the form 'hlist=<LIST_IRI>', but found ${guiAttributes.head}."
        )
      )
    } else {
      return Validation.succeed(guiAttributes)
    }
  }

  /**
   * Validates if gui element Slider has the correct gui attributes
   *
   * @param guiElement the gui element that needs to be validated
   * @param guiAttributes the gui attributes that need to be validated
   *
   * @return either the validated list of gui attributes or a [[dsp.errors.BadRequestException]]
   */
  private[valueobjects] def validateGuiObjectSlider(
    guiElement: GuiElement,
    guiAttributes: List[GuiAttribute]
  ): Validation[BadRequestException, List[GuiAttribute]] = {
    // gui element needs two gui attributes
    if (guiAttributes.size != 2) {
      return Validation.fail(
        BadRequestException(
          s"Wrong number of gui attributes. salsah-gui:guiElement $guiElement needs 2 salsah-gui:guiAttribute 'min' and 'max', but found ${guiAttributes.size}: $guiAttributes."
        )
      )
    }
    // gui element needs to have gui attributes 'min' and 'max'
    val validGuiAttributes = scala.collection.immutable.List("min", "max")
    guiAttributes.map { guiAttribute: GuiAttribute =>
      if (!validGuiAttributes.contains(guiAttribute.k)) {
        return Validation.fail(
          BadRequestException(
            s"Incorrect gui attributes. salsah-gui:guiElement $guiElement needs two salsah-gui:guiAttribute 'min' and 'max', but found $guiAttributes."
          )
        )
      }
    }
    return Validation.succeed(guiAttributes)
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
  val GuiAttributeMissing        = "gui attribute cannot be empty."
  val GuiAttributeUnknown        = s"gui attribute is unknown. Needs to be one of ${SalsahGui.GuiAttributes}"
  val GuiElementMissing          = "gui element cannot be empty."
  val GuiElementInvalid          = "gui element is invalid."
  val GuiElementUnknown          = s"gui element is unknown. Needs to be one of ${SalsahGui.GuiElements}"
  val GuiObjectMissing           = "gui object cannot be empty."
  val GuiAttributesMissing       = "gui attributes cannot be empty."
}
