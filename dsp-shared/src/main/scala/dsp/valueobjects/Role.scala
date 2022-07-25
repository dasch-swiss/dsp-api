/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.errors.BadRequestException
import zio.prelude.Validation

object Role {

  /**
   * LangString value object.
   *
   * @param value the value of the string
   * @param isoCode language ISO code pf the string
   */
  sealed abstract case class LangString private (value: String, isoCode: String)
  object LangString {
    def isIsoCodeSupported(isoCode: String): Boolean =
      V2.SupportedLanguageCodes.contains(isoCode.toLowerCase)

    def make(value: String, isoCode: String): Validation[Throwable, LangString] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException("Value cannot be empty."))
      } else if (isoCode.isEmpty) {
        Validation.fail(BadRequestException("Language ISO code cannot be empty."))
      } else if (isIsoCodeSupported(isoCode)) {
        Validation.fail(BadRequestException(s"Language ISO code $isoCode is not suporrted."))
      } else {
        // Validation.succeed(new LangString(value, isoCode) {})
        val validatedValue = Validation(
          V2IriValidation.toSparqlEncodedString(
            value,
            throw BadRequestException("String value is invalid.")
          )
        )

        validatedValue.map(new LangString(_, isoCode) {})
      }
  }
}

object Permission extends Enumeration {
  type Permission = Value

  val View   = Value("view")
  val Create = Value("create")
  val Modify = Value("modify")
  val Delete = Value("delete")
  val Admin  = Value("admin")
}
