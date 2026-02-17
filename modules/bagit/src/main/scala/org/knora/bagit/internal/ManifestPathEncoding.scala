/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

/**
 * Percent-encoding and decoding for manifest file paths per RFC 8493 Section 2.1.3.
 *
 * Only LF (0x0A), CR (0x0D), and percent (0x25) are encoded, as required by the specification.
 */
object ManifestPathEncoding {

  def encode(path: String): String = {
    val sb = new StringBuilder(path.length)
    var i  = 0
    while (i < path.length) {
      path.charAt(i) match {
        case '%'  => sb.append("%25")
        case '\n' => sb.append("%0A")
        case '\r' => sb.append("%0D")
        case c    => sb.append(c)
      }
      i += 1
    }
    sb.toString
  }

  def decode(path: String): String = {
    val sb = new StringBuilder(path.length)
    var i  = 0
    while (i < path.length) {
      if (path.charAt(i) == '%' && i + 2 < path.length) {
        val hex = path.substring(i + 1, i + 3).toUpperCase
        hex match {
          case "0A" =>
            sb.append('\n')
            i += 3
          case "0D" =>
            sb.append('\r')
            i += 3
          case "25" =>
            sb.append('%')
            i += 3
          case _ =>
            sb.append('%')
            i += 1
        }
      } else {
        sb.append(path.charAt(i))
        i += 1
      }
    }
    sb.toString
  }
}
