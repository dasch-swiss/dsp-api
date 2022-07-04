/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation
import dsp.errors.BadRequestException

object Property {

  /**
   * PropertyLabel value object.
   */
  sealed abstract case class PropertyLabel private (value: String)
  object PropertyLabel { self =>
    def make(value: String): Validation[Throwable, PropertyLabel] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(SchemaErrorMessages.PropertyLabelMissing))
      } else {
        val validatedValue = Validation(
          V2IriValidation.toSparqlEncodedString(
            value,
            throw BadRequestException(SchemaErrorMessages.PropertyLabelInvalid)
          )
        )

        validatedValue.map(new PropertyLabel(_) {})
      }

    def make(value: Option[String]): Validation[Throwable, Option[PropertyLabel]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * PropertyDescriptions value object.
   */
  sealed abstract case class PropertyDescriptions private (value: Seq[V2.StringLiteralV2])
  object PropertyDescriptions { self =>
    def make(value: Seq[V2.StringLiteralV2]): Validation[Throwable, PropertyDescriptions] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(SchemaErrorMessages.PropertyDescriptionsMissing))
      } else {
        val validatedDescriptions = Validation(value.map { description =>
          val validatedDescription =
            V2IriValidation.toSparqlEncodedString(
              description.value,
              throw BadRequestException(SchemaErrorMessages.PropertyDescriptionsInvalid)
            )
          V2.StringLiteralV2(value = validatedDescription, language = description.language)
        })
        validatedDescriptions.map(new PropertyDescriptions(_) {})
      }

    def make(value: Option[Seq[V2.StringLiteralV2]]): Validation[Throwable, Option[PropertyDescriptions]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

}

object SchemaErrorMessages {
  val PropertyLabelMissing        = "Property label cannot be empty."
  val PropertyLabelInvalid        = "Property label is invalid."
  val PropertyDescriptionsMissing = "Property descriptions cannot be empty."
  val PropertyDescriptionsInvalid = "Property descriptions is invalid."
}
