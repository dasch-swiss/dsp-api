/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.constants.SalsahGui
import dsp.errors.ValidationException
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
          ValidationException(
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
          return Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributesMissing))
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
              ValidationException(
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
      val v: String = keyValue.split("=").last.trim()

      if (keyValue.isEmpty) {
        Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributeMissing))
      } else if (!SalsahGui.GuiAttributes.contains(k)) {
        Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributeUnknown(k)))
      } else {
        validateGuiAttributeValueType(k, v).fold(
          e => Validation.fail(e.head),
          validValue => Validation.succeed(new GuiAttribute(k, validValue) {})
        )
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
        Validation.fail(ValidationException(SchemaErrorMessages.GuiElementMissing))
      } else if (!SalsahGui.GuiElements.contains(value)) {
        Validation.fail(ValidationException(SchemaErrorMessages.GuiElementUnknown))
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
   * @return either the validated list of gui attributes or a [[dsp.errors.ValidationException]]
   */
  private[valueobjects] def validateGuiObjectsPointingToList(
    guiElement: GuiElement,
    guiAttributes: List[GuiAttribute]
  ): Validation[ValidationException, List[GuiAttribute]] = {
    // gui element can have only one gui attribute
    if (guiAttributes.size > 1) {
      return Validation.fail(
        ValidationException(
          s"Wrong number of gui attributes. salsah-gui:guiElement $guiElement needs a salsah-gui:guiAttribute referencing a list of the form 'hlist=<LIST_IRI>', but found $guiAttributes."
        )
      )
    }
    // gui attribute needs to point to a list
    if (guiAttributes.head.k != ("hlist")) {
      return Validation.fail(
        ValidationException(
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
   * @return either the validated list of gui attributes or a [[dsp.errors.ValidationException]]
   */
  private[valueobjects] def validateGuiObjectSlider(
    guiElement: GuiElement,
    guiAttributes: List[GuiAttribute]
  ): Validation[ValidationException, List[GuiAttribute]] = {
    // gui element needs two gui attributes
    if (guiAttributes.size != 2) {
      return Validation.fail(
        ValidationException(
          s"Wrong number of gui attributes. salsah-gui:guiElement $guiElement needs 2 salsah-gui:guiAttribute 'min' and 'max', but found ${guiAttributes.size}: $guiAttributes."
        )
      )
    }
    // gui element needs to have gui attributes 'min' and 'max'
    val validGuiAttributes = scala.collection.immutable.List("min", "max")
    guiAttributes.map { guiAttribute: GuiAttribute =>
      if (!validGuiAttributes.contains(guiAttribute.k)) {
        return Validation.fail(
          ValidationException(
            s"Incorrect gui attributes. salsah-gui:guiElement $guiElement needs two salsah-gui:guiAttribute 'min' and 'max', but found $guiAttributes."
          )
        )
      }
    }
    return Validation.succeed(guiAttributes)
  }

  /**
   * Validates if the value of a gui attribute is of the correct value type (integer, decimal, string etc.)
   *
   * @param key     the gui attribute key
   * @param value   the gui attribute value
   *
   * @return either the validated value of the gui attribute or a [[dsp.errors.ValidationException]]
   */
  private[valueobjects] def validateGuiAttributeValueType(
    key: String,
    value: String
  ): Validation[ValidationException, String] = {
    val expectedValueType = SalsahGui.GuiAttributes.get(key)

    // try to parse the given value according to the expected value type
    val parseResult = expectedValueType match {
      case Some(valueType) if valueType.toString() == "integer" => value.toIntOption
      case Some(valueType) if valueType.toString() == "percent" => value.toDoubleOption
      case Some(valueType) if valueType.toString() == "decimal" => value.toDoubleOption
      case Some(valueType) if valueType.toString() == "string"  => Some(value)
      case Some(valueType) if valueType.toString() == "iri"     => Some(value)
      case _                                                    => None
    }

    parseResult match {
      case None         => Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributeHasWrongType(key, value)))
      case Some(result) => Validation.succeed(result.toString())
    }
  }

}

object SchemaErrorMessages {
  val GuiAttributeMissing = "gui attribute cannot be empty."
  def GuiAttributeUnknown(guiAttribute: String): String =
    s"gui attribute '$guiAttribute' is unknown. Needs to be one of: ${SalsahGui.GuiAttributes.mkString(", ")}"
  def GuiAttributeHasWrongType(key: String, value: String): String =
    s"Value '$value' of gui attribute '$key' has the wrong attribute type."
  val GuiElementMissing                             = "gui element cannot be empty."
  def GuiElementInvalid(guiElement: String): String = s"gui element '$guiElement' is invalid."
  val GuiElementUnknown                             = s"gui element is unknown. Needs to be one of: ${SalsahGui.GuiElements.mkString(", ")}"
  val GuiObjectMissing                              = "gui object cannot be empty."
  val GuiAttributesMissing                          = "gui attributes cannot be empty."
}
