/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import zio.test.*

import org.knora.bagit.domain.BagInfo

object BagInfoWriterSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] = suite("BagInfoWriterSpec")(
    test("writing duplicate labels in additionalFields produces separate lines for each") {
      val bagInfo = BagInfo(
        sourceOrganization = Some("DaSCH"),
        additionalFields = List(
          ("Contact-Name", "Alice"),
          ("Contact-Name", "Bob"),
          ("Custom-Field", "value"),
        ),
      )
      val output = BagInfoWriter.write(bagInfo)
      val lines  = output.split("\n").toList
      assertTrue(
        lines.contains("Source-Organization: DaSCH"),
        lines.contains("Contact-Name: Alice"),
        lines.contains("Contact-Name: Bob"),
        lines.contains("Custom-Field: value"),
        lines.count(_.startsWith("Contact-Name:")) == 2,
      )
    },
    test("additionalFields are written in insertion order, not sorted") {
      val bagInfo = BagInfo(
        additionalFields = List(
          ("Z-Field", "last"),
          ("A-Field", "first"),
          ("M-Field", "middle"),
        ),
      )
      val output = BagInfoWriter.write(bagInfo)
      val lines  = output.split("\n").toList
      assertTrue(
        lines == List(
          "Z-Field: last",
          "A-Field: first",
          "M-Field: middle",
        ),
      )
    },
  )
}
