/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain.writers

/** HTML escaping for the landing page's `<meta>` elements. */
object Html {

  /** Escapes `&` first so the entities written for the other characters are not escaped again. */
  def escapeAttribute(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

  def metaElement(tag: MetaTag): String =
    s"""<meta name="${escapeAttribute(tag.name)}" content="${escapeAttribute(tag.content)}">"""
}
