/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain.writers

import zio.json.*
import zio.json.ast.Json

/** JSON text safe to splice into an HTML script element. */
object ScriptSafeJson {

  /**
   * Compact JSON in which `<`, `>`, `&`, U+2028 and U+2029 are written as their six-character escapes. Outside a
   * string literal these characters cannot occur in JSON, so a parser reads back the same value.
   */
  def render(json: Json): String = {
    val compact = json.toJson
    val out     = new java.lang.StringBuilder(compact.length)
    compact.foreach {
      case '<'      => out.append("\\u003c")
      case '>'      => out.append("\\u003e")
      case '&'      => out.append("\\u0026")
      case '\u2028' => out.append("\\u2028")
      case '\u2029' => out.append("\\u2029")
      case other    => out.append(other)
    }
    out.toString
  }
}
