/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.constants.SalsahGui
import dsp.errors.ValidationException
import zio.prelude.ZValidation.Failure
import zio.prelude.ZValidation.Success
import zio.prelude._

import scala.collection.immutable

object Schema {

  /**
   * GuiObject value object.
   */
  sealed abstract case class GuiObject private (guiAttributes: List[GuiAttribute], guiElement: Option[GuiElement])
  object GuiObject {

    def makeFromStrings(
      guiAttributes: List[String],
      guiElement: Option[String]
    ): Validation[ValidationException, GuiObject] = {

      // create value objects from raw inputs
      val validatedGuiElement: Validation[ValidationException, Option[GuiElement]] =
        guiElement match {
          case Some(guiElement) => GuiElement.make(guiElement).map(Some(_))
          case None             => Validation.succeed(None)
        }

      val validatedGuiAttributes: Validation[ValidationException, List[GuiAttribute]] =
        // with forEach, multiple errors are returned if multiple errors occur
        guiAttributes.forEach { case guiAttribute => GuiAttribute.make(guiAttribute) }

      val validatedGuiAttributesAndGuiElement =
        Validation.validateWith(
          validatedGuiAttributes,
          validatedGuiElement
        )((ga, ge) => (ga, ge))

      // if there were errors in creating gui attributes or gui element, all of them are returned
      // otherwise, the gui object is created
      validatedGuiAttributesAndGuiElement match {
        case Failure(log, errors) => Validation.failNonEmptyChunk(errors)
        case Success(log, value)  => GuiObject.make(value._1, value._2)
      }

      // the following code block would short-circuit after an error in one of the lines
      // for {
      //   guiAttributes <- validatedGuiAttributes
      //   guiElement    <- validatedGuiElement
      //   guiObject     <- make(guiAttributes, guiElement)
      // } yield guiObject
    }

    def make(
      guiAttributes: List[GuiAttribute],
      guiElement: Option[GuiElement]
    ): Validation[ValidationException, GuiObject] = {

      // Check if the correct gui attributes are provided for a given gui element
      val validatedGuiAttributes: Validation[ValidationException, List[GuiAttribute]] =
        guiElement match {
          // the following gui elements are not allowed to have a gui attribute (Checkbox, Fileupload, Richtext, Timestamp, Interval, Geonames, Geometry, Date)
          case Some(guiElement) if SalsahGui.guiElementsWithoutGuiAttribute.contains(guiElement.value) =>
            if (!guiAttributes.isEmpty) {
              Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributeNotEmpty))
            } else {
              Validation.succeed(guiAttributes)
            }

          // all other gui elements have optional gui attributes
          case Some(guiElement) =>
            validateGuiAttributeKey(guiElement, guiAttributes)

          // if there is no gui element, an empty list is returned
          case None => Validation.succeed(scala.collection.immutable.List())
        }

      Validation.validateWith(
        validatedGuiAttributes,
        Validation.succeed(guiElement)
      )((a, b) => new GuiObject(a, b) {})

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
    def make(keyValue: String): Validation[ValidationException, GuiAttribute] = {
      val k: String = keyValue.split("=").head.trim()
      val v: String = keyValue.split("=").last.trim()

      if (keyValue.isEmpty) {
        Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributeMissing))
      } else if (!SalsahGui.GuiAttributes.contains(k)) {
        Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributeUnknown(k)))
      } else {
        validateGuiAttributeValue(k, v).fold(
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
    def make(value: String): Validation[ValidationException, GuiElement] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(SchemaErrorMessages.GuiElementMissing))
      } else if (!SalsahGui.GuiElements.contains(value)) {
        Validation.fail(ValidationException(SchemaErrorMessages.GuiElementUnknown(value)))
      } else {
        Validation.succeed(new GuiElement(value) {})
      }
  }

  /**
   * Checks if a list of gui attributes is correct in respect to a given gui element
   *
   * @param guiElement    gui element the list of gui attributes is validated against
   * @param guiAttributes list of gui attributes to be checked
   *
   * @return either the validated list of gui attributes or a [[dsp.errors.ValidationException]]
   */
  private[valueobjects] def validateGuiAttributeKey(
    guiElement: GuiElement,
    guiAttributes: List[GuiAttribute]
  ): Validation[ValidationException, List[GuiAttribute]] = {

    val expectedGuiAttributes = guiElement.value match {
      case SalsahGui.List        => scala.collection.immutable.List("hlist")
      case SalsahGui.Radio       => scala.collection.immutable.List("hlist")
      case SalsahGui.Pulldown    => scala.collection.immutable.List("hlist")
      case SalsahGui.Slider      => scala.collection.immutable.List("min", "max")
      case SalsahGui.SimpleText  => scala.collection.immutable.List("size", "maxlength")
      case SalsahGui.Textarea    => scala.collection.immutable.List("cols", "rows", "width", "wrap")
      case SalsahGui.Spinbox     => scala.collection.immutable.List("min", "max")
      case SalsahGui.Searchbox   => scala.collection.immutable.List("numprops")
      case SalsahGui.Colorpicker => scala.collection.immutable.List("ncolors")
    }

    val guiAttributeIsRequired: Boolean =
      SalsahGui.guiElementsPointingToList.contains(guiElement.value) ||
        SalsahGui.Slider == guiElement.value

    val guiAttributeKeys: List[String] = guiAttributes.map(_.k)

    val hasDuplicateAttributes: Boolean = guiAttributeKeys.toSet.size < guiAttributes.size

    if (
      guiAttributeIsRequired &&
      (guiAttributeKeys != expectedGuiAttributes || hasDuplicateAttributes)
    ) {
      Validation.fail(
        ValidationException(
          SchemaErrorMessages.GuiAttributeWrong(guiElement.value, guiAttributeKeys, expectedGuiAttributes)
        )
      )
    } else if (
      !guiAttributeIsRequired &&
      (!guiAttributeKeys.forall(expectedGuiAttributes.contains) || hasDuplicateAttributes)
    ) {
      Validation.fail(
        ValidationException(
          SchemaErrorMessages.GuiAttributeWrong(guiElement.value, guiAttributeKeys, expectedGuiAttributes)
        )
      )
    } else {
      Validation.succeed(guiAttributes)
    }

  }

  /**
   * Checks if the value of a gui attribute is of the correct value type (integer, decimal, string etc.)
   *
   * @param key     the gui attribute key
   * @param value   the gui attribute value
   *
   * @return either the validated value of the gui attribute or a [[dsp.errors.ValidationException]]
   */
  private[valueobjects] def validateGuiAttributeValue(
    key: String,
    value: String
  ): Validation[ValidationException, String] = {
    val expectedValueType = SalsahGui.GuiAttributes.get(key)

    // try to parse the given value according to the expected value type
    val parseResult = expectedValueType match {
      case Some(valueType) if valueType.toString() == "integer" => value.toIntOption
      case Some(valueType) if valueType.toString() == "percent" => value.split("%").head.trim.toIntOption
      case Some(valueType) if valueType.toString() == "decimal" => value.toDoubleOption
      case Some(valueType) if valueType.toString() == "string" =>
        if (value.trim() == "soft" || value.trim() == "hard") Some(value.trim()) else None
      case Some(valueType) if valueType.toString() == "iri" => Some(value)
      case _                                                => None
    }

    parseResult match {
      case None => Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributeWrongValueType(key, value)))
      case Some(result) =>
        Validation.succeed(result.toString())
    }
  }

}

object SchemaErrorMessages {
  val GuiAttributeMissing =
    "Gui attribute cannot be empty."

  val GuiAttributeNotEmpty =
    "No gui attributes allowed."

  def GuiAttributeUnknown(guiAttribute: String): String =
    s"Gui attribute '$guiAttribute' is unknown. Needs to be one of: ${SalsahGui.GuiAttributes.keys.mkString(", ")}"

  def GuiAttributeWrongValueType(key: String, value: String): String =
    s"Value '$value' of gui attribute '$key' has the wrong attribute type."

  def GuiAttributeDuplicates(guiAttributeKeys: List[String]): String =
    s"Found duplicate gui attributes: ${guiAttributeKeys.mkString(", ")}."

  def GuiAttributeWrong(guiElement: String, guiAttributes: List[String], expectedGuiAttributes: List[String]) =
    s"Expected salsah-gui:guiAttribute '${expectedGuiAttributes.mkString(", ")}' for salsah-gui:guiElement '$guiElement', but found '${guiAttributes
      .mkString(", ")}'."

  val GuiElementMissing =
    "Gui element cannot be empty."

  def GuiElementUnknown(guiElement: String) =
    s"Gui element '$guiElement' is unknown. Needs to be one of: ${SalsahGui.GuiElements.mkString(", ")}"

}
