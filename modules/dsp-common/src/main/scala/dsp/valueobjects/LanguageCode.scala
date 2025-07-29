/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

/**
 * Constants for language codes.
 */
enum LanguageCode(val code: String) {
  case DE extends LanguageCode("de")
  case EN extends LanguageCode("en")
  case FR extends LanguageCode("fr")
  case IT extends LanguageCode("it")
  case RM extends LanguageCode("rm")
}

object LanguageCode {
  def from(value: String): Option[LanguageCode] = values.find(_.code == value)
  def isSupported(value: String): Boolean       = from(value).isDefined
  def isNotSupported(value: String): Boolean    = !isSupported(value)
}