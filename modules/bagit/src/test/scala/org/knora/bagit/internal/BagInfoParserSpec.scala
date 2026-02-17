/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import zio.test.*

object BagInfoParserSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] = suite("BagInfoParserSpec")(
    test("two Contact-Name entries are both parsed into additionalFields") {
      val lines = List(
        "Source-Organization: DaSCH",
        "Contact-Name: Alice",
        "Contact-Name: Bob",
      )
      val result = BagInfoParser.parse(lines)
      assertTrue(
        result.isRight,
        result.toOption.get.sourceOrganization.contains("DaSCH"),
        result.toOption.get.additionalFields.count(_._1 == "Contact-Name") == 2,
        result.toOption.get.additionalFields.contains(("Contact-Name", "Alice")),
        result.toOption.get.additionalFields.contains(("Contact-Name", "Bob")),
      )
    },
    test("field ordering is preserved — fields appear in the same order as input") {
      val lines = List(
        "Source-Organization: DaSCH",
        "Custom-Field-B: second",
        "Custom-Field-A: first",
        "Custom-Field-C: third",
      )
      val result = BagInfoParser.parse(lines)
      val fields = result.toOption.get.additionalFields
      assertTrue(
        fields == List(
          ("Custom-Field-B", "second"),
          ("Custom-Field-A", "first"),
          ("Custom-Field-C", "third"),
        ),
      )
    },
    test("repeated dedicated field: second Source-Organization goes to additionalFields") {
      val lines = List(
        "Source-Organization: First Org",
        "Source-Organization: Second Org",
      )
      val result  = BagInfoParser.parse(lines)
      val bagInfo = result.toOption.get
      assertTrue(
        bagInfo.sourceOrganization.contains("First Org"),
        bagInfo.additionalFields == List(("Source-Organization", "Second Org")),
      )
    },
    test("Contact-Name is preserved in additionalFields") {
      val lines  = List("Contact-Name: Alice")
      val result = BagInfoParser.parse(lines)
      assertTrue(
        result.toOption.get.additionalFields.contains(("Contact-Name", "Alice")),
      )
    },
    test("Bag-Group-Identifier is preserved in additionalFields") {
      val lines  = List("Bag-Group-Identifier: group-1")
      val result = BagInfoParser.parse(lines)
      assertTrue(
        result.toOption.get.additionalFields.contains(("Bag-Group-Identifier", "group-1")),
      )
    },
    test("Bag-Count is preserved in additionalFields") {
      val lines  = List("Bag-Count: 1 of 3")
      val result = BagInfoParser.parse(lines)
      assertTrue(
        result.toOption.get.additionalFields.contains(("Bag-Count", "1 of 3")),
      )
    },
    test("Organization-Address is preserved in additionalFields") {
      val lines  = List("Organization-Address: 123 Main St")
      val result = BagInfoParser.parse(lines)
      assertTrue(
        result.toOption.get.additionalFields.contains(("Organization-Address", "123 Main St")),
      )
    },
    test("Bag-Size is preserved in additionalFields") {
      val lines  = List("Bag-Size: 42.6 GB")
      val result = BagInfoParser.parse(lines)
      assertTrue(
        result.toOption.get.additionalFields.contains(("Bag-Size", "42.6 GB")),
      )
    },
    test("round-trip with all reserved fields preserves everything") {
      val lines = List(
        "Source-Organization: DaSCH",
        "External-Description: Test",
        "External-Identifier: id-1",
        "Bagging-Date: 2026-01-15",
        "Payload-Oxum: 1024.3",
        "Contact-Name: Alice",
        "Contact-Phone: +41 12 345 67 89",
        "Contact-Email: alice@example.com",
        "Bag-Group-Identifier: group-1",
        "Bag-Count: 1 of 3",
        "Bag-Size: 42.6 GB",
        "Internal-Sender-Identifier: internal-1",
        "Internal-Sender-Description: A test bag",
        "Organization-Address: 123 Main St",
      )
      val firstParse  = BagInfoParser.parse(lines).toOption.get
      val written     = BagInfoWriter.write(firstParse)
      val reLines     = written.split("\n").toList
      val secondParse = BagInfoParser.parse(reLines).toOption.get
      assertTrue(
        firstParse.sourceOrganization == secondParse.sourceOrganization,
        firstParse.externalDescription == secondParse.externalDescription,
        firstParse.externalIdentifier == secondParse.externalIdentifier,
        firstParse.baggingDate == secondParse.baggingDate,
        firstParse.payloadOxum == secondParse.payloadOxum,
        firstParse.additionalFields == secondParse.additionalFields,
      )
    },
    test("round-trip: parse repeated fields, write, parse again — values and order identical") {
      val lines = List(
        "Source-Organization: DaSCH",
        "Contact-Name: Alice",
        "External-Description: A test bag",
        "Contact-Name: Bob",
        "Custom-Field: value1",
        "Custom-Field: value2",
      )
      val firstParse  = BagInfoParser.parse(lines).toOption.get
      val written     = BagInfoWriter.write(firstParse)
      val reLines     = written.split("\n").toList
      val secondParse = BagInfoParser.parse(reLines).toOption.get
      assertTrue(
        firstParse.sourceOrganization == secondParse.sourceOrganization,
        firstParse.externalDescription == secondParse.externalDescription,
        firstParse.additionalFields == secondParse.additionalFields,
      )
    },
  )
}
