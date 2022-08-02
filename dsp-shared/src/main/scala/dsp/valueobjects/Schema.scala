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
  private[valueobjects] val guiElements: Set[SalsahGui.IRI] = Set(
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
   * A map that defines the (sometimes optional) gui attributes for each gui element
   */
  private val guiElementToGuiAttributes: Map[SalsahGui.IRI, Set[String]] = Map(
    (SalsahGui.List, Set(SalsahGui.Hlist)),
    (SalsahGui.Radio, Set(SalsahGui.Hlist)),
    (SalsahGui.Pulldown, Set(SalsahGui.Hlist)),
    (SalsahGui.Slider, Set(SalsahGui.Min, SalsahGui.Max)),
    (SalsahGui.SimpleText, Set(SalsahGui.Size, SalsahGui.Maxlength)),
    (SalsahGui.Textarea, Set(SalsahGui.Cols, SalsahGui.Rows, SalsahGui.Width, SalsahGui.Wrap)),
    (SalsahGui.Spinbox, Set(SalsahGui.Min, SalsahGui.Max)),
    (SalsahGui.Searchbox, Set(SalsahGui.Numprops)),
    (SalsahGui.Colorpicker, Set(SalsahGui.Ncolors))
  )

  /**
   * A set that contains all gui elements that do not have a gui attribute
   */
  private val guiElementsWithoutGuiAttribute: Set[SalsahGui.IRI] =
    guiElements.toSet diff guiElementToGuiAttributes.keySet

  /**
   * A set that contains all gui elements that point to a list
   */
  private val guiElementsPointingToList: Set[SalsahGui.IRI] =
    guiElementToGuiAttributes.filter(_._2 == Set(SalsahGui.Hlist)).keySet

  /**
   * A map that defines the gui attribute type for each gui attribute
   */
  private[valueobjects] val guiAttributeToType = Map(
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
   * GuiObject value object. Consists of a list of gui attributes and an optional gui element.
   * If no gui attributes are used, the list is empty.
   *
   * @param guiAttributes a list of gui attributes
   * @param guiElement optional gui element
   *
   * @return the GuiObject value object
   */
  sealed abstract case class GuiObject private (guiAttributes: Set[GuiAttribute], guiElement: Option[GuiElement])
  object GuiObject {

    def makeFromStrings(
      guiAttributes: Set[String],
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
        guiAttributes.toList.forEach { case guiAttribute => GuiAttribute.make(guiAttribute) }

      val validatedGuiAttributesAndGuiElement
        : ZValidation[Nothing, ValidationException, (List[GuiAttribute], Option[GuiElement])] =
        Validation.validate(
          validatedGuiAttributes,
          validatedGuiElement
        )

      // if there were errors in creating gui attributes or gui element, all of the errors are returned
      validatedGuiAttributesAndGuiElement match {
        case Failure(log, errors) => Validation.failNonEmptyChunk(errors)
        case Success(log, value)  => GuiObject.make(value._1.toSet, value._2)
      }

    }

    def make(
      guiAttributes: Set[GuiAttribute],
      guiElement: Option[GuiElement]
    ): Validation[ValidationException, GuiObject] = {

      // Check if the correct gui attributes are provided for a given gui element
      val validatedGuiAttributes: Validation[ValidationException, Set[GuiAttribute]] =
        guiElement match {
          // the following gui elements are not allowed to have a gui attribute (Checkbox, Fileupload, Richtext, Timestamp, Interval, Geonames, Geometry, Date)
          case Some(guiElement) if guiElementsWithoutGuiAttribute.contains(guiElement.value) =>
            if (guiAttributes.isEmpty) {
              Validation.succeed(guiAttributes)
            } else {
              Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributeNotEmpty))
            }

          // all other gui elements have optional gui attributes
          case Some(guiElement) =>
            validateGuiAttributes(guiElement, guiAttributes)

          // if there is no gui element, an empty list is returned
          case None =>
            if (guiAttributes.isEmpty) {
              Validation.succeed(Set())
            } else {
              Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributeNotEmpty))
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
    guiAttributes: Set[GuiAttribute]
  ): Validation[ValidationException, Set[GuiAttribute]] = {

    val expectedGuiAttributes: Set[String] = guiElementToGuiAttributes.getOrElse(guiElement.value, Set())

    val isGuiAttributeRequired: Boolean =
      guiElementsPointingToList.contains(guiElement.value) ||
        SalsahGui.Slider == guiElement.value

    val guiAttributeKeys: Set[String] = guiAttributes.map(_.k)

    if (
      isGuiAttributeRequired &&
      (guiAttributeKeys != expectedGuiAttributes)
    ) {
      Validation.fail(
        ValidationException(
          SchemaErrorMessages.GuiAttributeWrong(guiElement.value, guiAttributeKeys, expectedGuiAttributes)
        )
      )
    } else if (
      !isGuiAttributeRequired &&
      (!guiAttributeKeys.forall(expectedGuiAttributes.contains))
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
   * Checks if the value of a gui attribute has correct value type (integer, decimal, string etc.)
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
        iriWithoutBrackets.map { valueWithoutBrackets: String =>
          if (Iri.urlValidator.isValid(encodeAllowEscapes(valueWithoutBrackets))) value else None
        }
      }
      case _ => None
    }

    parseResult match {
      case None         => Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributeWrongValueType(key, value)))
      case Some(result) => Validation.succeed(result.toString())
    }
  }

}

object SchemaErrorMessages {
  val GuiAttributeMissing  = "Gui attribute cannot be empty."
  val GuiAttributeNotEmpty = "No gui attributes allowed."
  val GuiElementMissing    = "Gui element cannot be empty."
  val GuiAttributeUnknown = (guiAttribute: String) =>
    s"Gui attribute '$guiAttribute' is unknown. Needs to be one of: ${Schema.guiAttributeToType.keys.mkString(", ")}"
  val GuiAttributeWrongValueType = (key: String, value: String) =>
    s"Value '$value' of gui attribute '$key' has the wrong attribute type."
  val GuiAttributeWrong = (guiElement: String, guiAttributes: Set[String], expectedGuiAttributes: Set[String]) =>
    s"Expected salsah-gui:guiAttribute '${expectedGuiAttributes.mkString(", ")}' for salsah-gui:guiElement '$guiElement', but found '${guiAttributes
        .mkString(", ")}'."
  val GuiElementUnknown = (guiElement: String) =>
    s"Gui element '$guiElement' is unknown. Needs to be one of: ${Schema.guiElements.mkString(", ")}"

}
