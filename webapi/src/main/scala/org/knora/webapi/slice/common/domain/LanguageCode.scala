/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.domain

import sttp.tapir.Schema
import zio.json.JsonCodec

import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue

enum LanguageCode(val value: String) extends StringValue {
  case DE extends LanguageCode("de")
  case EN extends LanguageCode("en")
  case FR extends LanguageCode("fr")
  case IT extends LanguageCode("it")
  case RM extends LanguageCode("rm")
}

object LanguageCode extends StringValueCompanion[LanguageCode] {

  given JsonCodec[LanguageCode] = JsonCodec[String].transformOrFail(LanguageCode.from, _.value)
  given Schema[LanguageCode]    = Schema.string

  val Default: LanguageCode = LanguageCode.EN

  def from(str: String): Either[String, LanguageCode] = values
    .find(_.value == str)
    .toRight(s"Unsupported language code: $str, supported codes are: ${values.mkString(", ")}")

  def isSupported(value: String): Boolean = from(value).isRight

  def isNotSupported(value: String): Boolean = !isSupported(value)
}
