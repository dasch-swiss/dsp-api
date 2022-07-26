/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import com.google.gwt.safehtml.shared.UriUtils.encodeAllowEscapes
import dsp.constants.SalsahGui
import dsp.errors.ValidationException
import dsp.valueobjects.{List => ListGuiElement}
import zio.prelude.ZValidation.Failure
import zio.prelude.ZValidation.Success
import zio.prelude._

import scala.collection.immutable.List

object Schema {

  /**
   * A list of all known gui elements
   */
  val guiElements: List[SalsahGui.IRI] = List(
    SalsahGui.SimpleText,
    SalsahGui.Textarea,
    SalsahGui.Pulldown,
    SalsahGui.Slider,
    SalsahGui.Spinbox,
    SalsahGui.Searchbox,
    SalsahGui.Date,
    SalsahGui.Geometry,
    SalsahGui.Colorpicker,
    SalsahGui.List,
    SalsahGui.Radio,
    SalsahGui.Checkbox,
    SalsahGui.Richtext,
    SalsahGui.Interval,
    SalsahGui.TimeStamp,
    SalsahGui.Geonames,
    SalsahGui.Fileupload
  )

  /**
   * A set that contains all gui elements that point to a list
   */
  private val guiElementsPointingToList: Set[SalsahGui.IRI] = Set(
    SalsahGui.List,
    SalsahGui.Radio,
    SalsahGui.Pulldown
  )

  /**
   * A set that contains all gui elements that do not have a gui attribute
   */
  private val guiElementsWithoutGuiAttribute: Set[SalsahGui.IRI] = Set(
    SalsahGui.Checkbox,
    SalsahGui.Fileupload,
    SalsahGui.Richtext,
    SalsahGui.TimeStamp,
    SalsahGui.Interval,
    SalsahGui.Geonames,
    SalsahGui.Geometry,
    SalsahGui.Date
  )

  /**
   * A map that defines the gui attribute type for each gui attribute
   */
  val guiAttributeToType = Map(
    (SalsahGui.Ncolors, SalsahGui.SalsahGuiAttributeType.Integer),
    (SalsahGui.Hlist, SalsahGui.SalsahGuiAttributeType.Iri),
    (SalsahGui.Numprops, SalsahGui.SalsahGuiAttributeType.Integer),
    (SalsahGui.Size, SalsahGui.SalsahGuiAttributeType.Integer),
    (SalsahGui.Maxlength, SalsahGui.SalsahGuiAttributeType.Integer),
    (SalsahGui.Min, SalsahGui.SalsahGuiAttributeType.Decimal),
    (SalsahGui.Max, SalsahGui.SalsahGuiAttributeType.Decimal),
    (SalsahGui.Cols, SalsahGui.SalsahGuiAttributeType.Integer),
    (SalsahGui.Rows, SalsahGui.SalsahGuiAttributeType.Integer),
    (SalsahGui.Width, SalsahGui.SalsahGuiAttributeType.Percent),
    (SalsahGui.Wrap, SalsahGui.SalsahGuiAttributeType.Str)
  )

  /**
   * GuiObject value object. Consists of a list of gui attributes and an optional gui element. If no gui attributes are used,
   * the list is empty.
   *
   * @param guiAttributes a list of gui attributes
   * @param guiElement optional gui element
   *
   * @return the GuiObject value object
   */
  sealed abstract case class GuiObject private (guiAttributes: List[GuiAttribute], guiElement: Option[GuiElement])
  object GuiObject {

    def makeFromStrings(
      guiAttributes: List[String],
      guiElement: Option[String]
    ): Validation[ValidationException, GuiObject] = {

      // create GuiElement value object from raw inputs
      val validatedGuiElement: Validation[ValidationException, Option[GuiElement]] =
        guiElement match {
          case Some(guiElement) => GuiElement.make(guiElement).map(Some(_))
          case None             => Validation.succeed(None)
        }

      // create a list of GuiAttribute value objects from raw inputs
      val validatedGuiAttributes: Validation[ValidationException, List[GuiAttribute]] =
        // with forEach, multiple errors are returned if multiple errors occur
        guiAttributes.forEach { case guiAttribute => GuiAttribute.make(guiAttribute) }

      val validatedGuiAttributesAndGuiElement =
        Validation.validateWith(
          validatedGuiAttributes,
          validatedGuiElement
        )((ga, ge) => (ga, ge))

      // if there were errors in creating gui attributes or gui element, all of the errors are returned
      validatedGuiAttributesAndGuiElement match {
        case Failure(log, errors) => Validation.failNonEmptyChunk(errors)
        case Success(log, value)  => GuiObject.make(value._1, value._2)
      }

    }

    def make(
      guiAttributes: List[GuiAttribute],
      guiElement: Option[GuiElement]
    ): Validation[ValidationException, GuiObject] = {

      // Check if the correct gui attributes are provided for a given gui element
      val validatedGuiAttributes: Validation[ValidationException, List[GuiAttribute]] =
        guiElement match {
          // the following gui elements are not allowed to have a gui attribute (Checkbox, Fileupload, Richtext, Timestamp, Interval, Geonames, Geometry, Date)
          case Some(guiElement) if guiElementsWithoutGuiAttribute.contains(guiElement.value) =>
            if (!guiAttributes.isEmpty) {
              Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributeNotEmpty))
            } else {
              Validation.succeed(guiAttributes)
            }

          // all other gui elements have optional gui attributes
          case Some(guiElement) =>
            validateGuiAttributes(guiElement, guiAttributes)

          // if there is no gui element, an empty list is returned
          case None =>
            if (!guiAttributes.isEmpty) {
              Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributeNotEmpty))
            } else {
              Validation.succeed(List())
            }

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
      } else if (!guiAttributeToType.contains(k)) {
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
      } else if (!guiElements.contains(value)) {
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
  private def validateGuiAttributes(
    guiElement: GuiElement,
    guiAttributes: List[GuiAttribute]
  ): Validation[ValidationException, List[GuiAttribute]] = {

    val expectedGuiAttributes = guiElement.value match {
      case SalsahGui.List       => List(SalsahGui.Hlist)
      case SalsahGui.Radio      => List(SalsahGui.Hlist)
      case SalsahGui.Pulldown   => List(SalsahGui.Hlist)
      case SalsahGui.Slider     => List(SalsahGui.Min, SalsahGui.Max)
      case SalsahGui.SimpleText => List(SalsahGui.Size, SalsahGui.Maxlength)
      case SalsahGui.Textarea =>
        List(SalsahGui.Cols, SalsahGui.Rows, SalsahGui.Width, SalsahGui.Wrap)
      case SalsahGui.Spinbox     => List(SalsahGui.Min, SalsahGui.Max)
      case SalsahGui.Searchbox   => List(SalsahGui.Numprops)
      case SalsahGui.Colorpicker => List(SalsahGui.Ncolors)
    }

    val guiAttributeIsRequired: Boolean =
      guiElementsPointingToList.contains(guiElement.value) ||
        SalsahGui.Slider == guiElement.value

    val guiAttributeKeys: List[String] = guiAttributes.map(_.k)

    val hasDuplicateAttributes: Boolean = guiAttributeKeys.toSet.size < guiAttributes.size

    if (
      guiAttributeIsRequired &&
      (guiAttributeKeys != expectedGuiAttributes)
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
  private def validateGuiAttributeValue(
    key: String,
    value: String
  ): Validation[ValidationException, String] = {

    val expectedValueType = guiAttributeToType.get(key)

    // try to parse the given value according to the expected value type
    val parseResult: Option[Any] = expectedValueType match {
      case Some(valueType) if valueType.toString() == "integer" => value.toIntOption
      case Some(valueType) if valueType.toString() == "percent" => value.split("%").head.trim.toIntOption
      case Some(valueType) if valueType.toString() == "decimal" => value.toDoubleOption
      case Some(valueType) if valueType.toString() == "string" =>
        if (value.trim() == "soft" || value.trim() == "hard") Some(value.trim()) else None
      case Some(valueType) if valueType.toString() == "iri" => {
        val iriWithoutBrackets: Option[String] =
          if (value.startsWith("<") && value.endsWith(">")) Some(value.substring(1, value.length - 1)) else None
        iriWithoutBrackets.map { value: String =>
          if (Iri.urlValidator.isValid(encodeAllowEscapes(value))) value else None
        }
      }
      case _ => None
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
    s"Gui attribute '$guiAttribute' is unknown. Needs to be one of: ${Schema.guiAttributeToType.keys.mkString(", ")}"

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
    s"Gui element '$guiElement' is unknown. Needs to be one of: ${Schema.guiElements.mkString(", ")}"

}
