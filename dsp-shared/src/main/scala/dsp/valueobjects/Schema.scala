/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.constants.SalsahGui
import dsp.errors.ValidationException
import zio.prelude.Subtype
import zio.prelude.Validation
import scala.collection.immutable
import zio.prelude.ZValidation
import zio.prelude._

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
      val validatedGuiElement: Validation[ValidationException, Option[GuiElement]] = guiElement match {
        case Some(guiElement) => GuiElement.make(guiElement).map(Some(_))
        case None             => Validation.succeed(None)
      }

      val validatedGuiAttributes: Validation[ValidationException, List[GuiAttribute]] =
        guiAttributes.forEach { case guiAttribute => GuiAttribute.make(guiAttribute) }
      // Validation.validateAll(
      //   guiAttributes.map(guiAttribute => GuiAttribute.make(guiAttribute))
      // )

      for {
        guiAttributes <- validatedGuiAttributes
        guiElement    <- validatedGuiElement
        guiObject     <- make(guiAttributes, guiElement)
      } yield guiObject
    }

    def make(
      guiAttributes: List[GuiAttribute],
      guiElement: Option[GuiElement]
    ): Validation[ValidationException, GuiObject] = {

      // check that there are no duplicated gui attributes
      // val guiAttributeKeys: List[String] = guiAttributes.map { guiAttribute: GuiAttribute => guiAttribute.k }
      // if (guiAttributeKeys.toSet.size < guiAttributes.size) {
      //   return Validation.fail(
      //     ValidationException(
      //       s"Duplicate gui attributes for salsah-gui:guiElement $guiElement."
      //     )
      //   )
      // }

      // If the gui element is a list, radio, pulldown or slider, check if a gui attribute (which is mandatory in these cases) is provided
      val guiElementsPointingToList: Set[SalsahGui.IRI] = Set(SalsahGui.List, SalsahGui.Radio, SalsahGui.Pulldown)

      val validatedGuiAttributes: Validation[ValidationException, List[GuiAttribute]] = guiElement match {
        // gui element is a list, radio or pulldown, so it needs a gui attribute that points to a list
        case Some(guiElement) if guiElementsPointingToList.contains(guiElement.value) =>
          validateGuiObjectsPointingToList(guiElement, guiAttributes)

        // gui element is a slider, so it needs two gui attributes min and max
        case Some(guiElement) if guiElement.value == SalsahGui.Slider =>
          validateGuiObjectSlider(guiElement, guiAttributes)

        // case Some(guiElement) if guiElement.value == SalsahGui.Checkbox =>
        //   validateGuiObjectCheckbox(guiElement, guiAttributes)

        case _ =>
          Validation.fail(
            ValidationException(
              s"Unable to validate gui attributes. Unknown value for salsah-gui:guiElement: $guiElement."
            )
          )
      }

      // check that there are no duplicate gui attributes
      val checkedGuiAttributes = for {
        checkedGuiAttributes <- checkGuiAttributesForDuplicates(guiAttributes)
      } yield checkedGuiAttributes

      val allChecked = Validation.validate(checkedGuiAttributes, validatedGuiAttributes)

      Validation.validateWith(
        allChecked,
        Validation.succeed(guiElement)
      )((a, b) => new GuiObject(a._1, b) {})

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
    def make(value: String): Validation[ValidationException, GuiElement] =
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
    val expectedGuiAttributes = scala.collection.immutable.List("hlist")
    for {
      _ <- checkGuiAttributesEmpty(guiAttributes)
      _ <- checkGuiElementHasCorrectNumberOfGuiAttributes(guiElement, guiAttributes.size)
      result <- {
        if (guiAttributes.map(_.k) != scala.collection.immutable.List("hlist")) {
          Validation.fail(
            ValidationException(
              SchemaErrorMessages.GuiAttributeWrong(guiElement.value, guiAttributes.map(_.k), expectedGuiAttributes)
            )
          )
        } else {
          Validation.succeed(guiAttributes)
        }
      }
    } yield result
  }

  // gui attribute needs to point to a list

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
    val expectedGuiAttributes = scala.collection.immutable.List("min", "max")
    for {
      _ <- checkGuiAttributesEmpty(guiAttributes)
      _ <- checkGuiElementHasCorrectNumberOfGuiAttributes(guiElement, guiAttributes.size)
      result <- {
        if (guiAttributes.map(_.k) != scala.collection.immutable.List("min", "max")) {
          Validation.fail(
            ValidationException(
              SchemaErrorMessages.GuiAttributeWrong(guiElement.value, guiAttributes.map(_.k), expectedGuiAttributes)
            )
          )
        } else {
          Validation.succeed(guiAttributes)
        }
      }
    } yield result

    // gui element needs two gui attributes
    // val expectedNumberOfGuiAttributes: Int = 2
    // val numberOfGuiAttributes: Int         = guiAttributes.size
    // if (guiAttributes.size != 2) {
    //   return Validation.fail(
    //     ValidationException(
    //       SchemaErrorMessages.GuiAttributeWrongNumber(
    //         guiElement.value,
    //         numberOfGuiAttributes,
    //         expectedNumberOfGuiAttributes
    //       )
    //     )
    //   )
    // }
    // gui element needs to have gui attributes 'min' and 'max'
    // val validGuiAttributes = scala.collection.immutable.List("min", "max")
    // guiAttributes.map { guiAttribute: GuiAttribute =>
    //   if (!validGuiAttributes.contains(guiAttribute.k)) {
    //     return Validation.fail(
    //       ValidationException(
    //         s"Incorrect gui attributes. salsah-gui:guiElement $guiElement needs two salsah-gui:guiAttribute 'min' and 'max', but found $guiAttributes."
    //       )
    //     )
    //   }
    // }
    // return Validation.succeed(guiAttributes)
  }

  /**
   * Validates a list of gui attributes for emptyness (i.e. validation fails if list is empty)
   *
   * @param guiAttributes list of gui attributes to be checked
   *
   * @return either the list of gui attributes or a [[dsp.errors.ValidationException]] in case it is empty
   */
  private[valueobjects] def checkGuiAttributesEmpty(
    guiAttributes: List[GuiAttribute]
  ): Validation[ValidationException, List[GuiAttribute]] =
    if (guiAttributes.isEmpty) {
      Validation.fail(ValidationException(SchemaErrorMessages.GuiAttributesMissing))
    } else {
      Validation.succeed(guiAttributes)
    }

  /**
   * Validates if the number of gui attributes is correct in respect to a given gui element
   *
   * @param guiElement             the gui element against which the validation is performed
   * @param numberOfGuiAttributes  number of gui attributes
   *
   * @return either the validated gui element or a [[dsp.errors.ValidationException]] in case of wrong number
   */
  private[valueobjects] def checkGuiElementHasCorrectNumberOfGuiAttributes(
    guiElement: GuiElement,
    numberOfGuiAttributes: Int
  ): Validation[ValidationException, GuiElement] = {
    val guiElementsToNumberOfGuiAttributes = Map(
      (SalsahGui.List, 1),
      (SalsahGui.Pulldown, 1),
      (SalsahGui.Radio, 1),
      (SalsahGui.Slider, 2)
    )
    val expectedNumberOfGuiAttributes: Int = guiElementsToNumberOfGuiAttributes.getOrElse(guiElement.value, 0)
    if (numberOfGuiAttributes != expectedNumberOfGuiAttributes) {
      Validation.fail(
        ValidationException(
          SchemaErrorMessages.GuiAttributeWrongNumber(
            guiElement.value,
            numberOfGuiAttributes,
            expectedNumberOfGuiAttributes
          )
        )
      )
    } else {
      Validation.succeed(guiElement)
    }
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

  private[valueobjects] def checkGuiAttributesForDuplicates(
    guiAttributes: List[GuiAttribute]
  ): Validation[ValidationException, List[GuiAttribute]] = {
    val guiAttributeKeys: List[String] = guiAttributes.map { guiAttribute: GuiAttribute => guiAttribute.k }
    if (guiAttributeKeys.toSet.size < guiAttributes.size) {
      Validation.fail(
        ValidationException(SchemaErrorMessages.GuiAttributeDuplicates(guiAttributeKeys))
      )
    } else Validation.succeed(guiAttributes)
  }

}

object SchemaErrorMessages {
  val GuiAttributeMissing =
    "gui attribute cannot be empty."

  def GuiAttributeUnknown(guiAttribute: String): String =
    s"gui attribute '$guiAttribute' is unknown. Needs to be one of: ${SalsahGui.GuiAttributes.keys.mkString(", ")}"

  def GuiAttributeHasWrongType(key: String, value: String): String =
    s"Value '$value' of gui attribute '$key' has the wrong attribute type."

  def GuiAttributeDuplicates(guiAttributeKeys: List[String]): String =
    s"Found duplicate gui attributes: ${guiAttributeKeys.mkString(", ")}."

  def GuiAttributeWrongNumber(
    guiElement: String,
    numberOfGuiAttributes: Int,
    expectedNumber: Int
  ): String =
    s"Wrong number of gui attributes. salsah-gui:guiElement '$guiElement' needs $expectedNumber salsah-gui:guiAttribute(s) but found: $numberOfGuiAttributes"

  val GuiElementMissing =
    "gui element cannot be empty."

  def GuiElementInvalid(guiElement: String): String =
    s"gui element '$guiElement' is invalid."

  val GuiElementUnknown =
    s"gui element is unknown. Needs to be one of: ${SalsahGui.GuiElements.mkString(", ")}"

  val GuiObjectMissing =
    "gui object cannot be empty."

  val GuiAttributesMissing =
    "gui attributes cannot be empty."

  def GuiAttributeWrong(guiElement: String, guiAttributes: List[String], expectedGuiAttributes: List[String]) =
    s"Expected salsah-gui:guiAttribute '${expectedGuiAttributes.mkString(", ")}' for salsah-gui:guiElement '$guiElement', but found '${guiAttributes
      .mkString(", ")}'."
}
