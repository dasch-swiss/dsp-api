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
  }

  /**
   * PropertyDescription value object.
   */
  sealed abstract case class PropertyDescription private (value: Seq[V2.StringLiteralV2])
  object PropertyDescription { self =>
    def make(value: Seq[V2.StringLiteralV2]): Validation[Throwable, PropertyDescription] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(SchemaErrorMessages.PropertyDescriptionMissing))
      } else {
        val validatedDescription = Validation(value.map { description =>
          val validatedDescription =
            V2IriValidation.toSparqlEncodedString(
              description.value,
              throw BadRequestException(SchemaErrorMessages.PropertyDescriptionInvalid)
            )
          V2.StringLiteralV2(value = validatedDescription, language = description.language)
        })
        validatedDescription.map(new PropertyDescription(_) {})
      }
  }

}

object SchemaErrorMessages {
  val PropertyLabelMissing       = "Property label cannot be empty."
  val PropertyLabelInvalid       = "Property label is invalid."
  val PropertyDescriptionMissing = "Property description cannot be empty."
  val PropertyDescriptionInvalid = "Property description is invalid."
}
