/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

/**
 * Constants for language codes.
 */
object LanguageCodes {
  val DE: String = "de"
  val EN: String = "en"
  val FR: String = "fr"
  val IT: String = "it"
  val RM: String = "rm"

  val SupportedLanguageCodes: Set[String] = Set(
    DE,
    EN,
    FR,
    IT,
    RM
  )
}
