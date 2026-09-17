/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages

import org.junit.runner.RunWith
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.testrunner.DspZTestJUnitRunner

/**
 * Tests [[Geolocation]] — the parser, the bounds table, and the lenient read-path decomposition.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class GeolocationSpec extends ZIOSpecDefault {

  private val crs84 = Crs.Crs84.iri
  private val lv95  = Crs.Lv95.iri
  private val lv03  = Crs.Lv03.iri

  private def parse(literal: String) = Geolocation.parse(literal)

  private def rejection(literal: String): String =
    parse(literal).swap.getOrElse(throw new AssertionError(s"expected '$literal' to be rejected"))

  private def accepted(literal: String): Geolocation =
    parse(literal).getOrElse(throw new AssertionError(s"expected '$literal' to be accepted"))

  val spec: Spec[Any, Nothing] = suite("Geolocation")(
    suite("accepts")(
      test("a CRS84-tagged point") {
        val actual = accepted(s"<$crs84> POINT(8.55 47.37)")
        assertTrue(
          actual == Geolocation(Crs.Crs84, "8.55", "47.37"),
          actual.toStoredLiteral == s"<$crs84> POINT(8.55 47.37)",
        )
      },
      test("an LV95-tagged point") {
        assertTrue(accepted(s"<$lv95> POINT(2600000 1200000)") == Geolocation(Crs.Lv95, "2600000", "1200000"))
      },
      test("an LV03-tagged point") {
        assertTrue(accepted(s"<$lv03> POINT(600000 200000)") == Geolocation(Crs.Lv03, "600000", "200000"))
      },
      test("an untagged literal, storing it with an explicit CRS84 prefix") {
        val actual = accepted("POINT(8.55 47.37)")
        assertTrue(actual.crs == Crs.Crs84, actual.toStoredLiteral == s"<$crs84> POINT(8.55 47.37)")
      },
      test("coordinates with their submitted decimal precision, trailing zeros included") {
        val actual = accepted(s"<$crs84> POINT(8.550 47.3700)")
        assertTrue(
          actual.x == "8.550",
          actual.y == "47.3700",
          actual.toStoredLiteral == s"<$crs84> POINT(8.550 47.3700)",
        )
      },
      test("the geometry keyword case-insensitively, and leading and trailing whitespace") {
        assertTrue(
          accepted(s"  <$crs84> point(8.55 47.37)  ") == Geolocation(Crs.Crs84, "8.55", "47.37"),
          accepted(s"<$crs84> PoInT (8.55 47.37)") == Geolocation(Crs.Crs84, "8.55", "47.37"),
        )
      },
      test("LWSP as the separator: tabs and newlines, not just a single space") {
        assertTrue(accepted(s"<$crs84>\t\nPOINT(8.55 47.37)") == Geolocation(Crs.Crs84, "8.55", "47.37"))
      },
      test("the WGS84 bounds inclusively") {
        assertTrue(
          parse(s"<$crs84> POINT(180 90)").isRight,
          parse(s"<$crs84> POINT(-180 -90)").isRight,
          parse(s"<$crs84> POINT(180.000001 90)").isLeft,
          parse(s"<$crs84> POINT(180 90.000001)").isLeft,
        )
      },
      test("the Swiss bounds inclusively, at both ends") {
        assertTrue(
          parse(s"<$lv95> POINT(2484273.3 1073150.16)").isRight,
          parse(s"<$lv95> POINT(2837939.88 1299970.97)").isRight,
          parse(s"<$lv03> POINT(484273.3 73150.16)").isRight,
          parse(s"<$lv03> POINT(837939.88 299970.97)").isRight,
        )
      },
      test("-0.0 as equal to 0.0") {
        assertTrue(parse(s"<$crs84> POINT(-0.0 -0.0)").isRight, parse(s"<$crs84> POINT(-0 0)").isRight)
      },
      test("a negative and an exponent-notated ordinate") {
        assertTrue(parse(s"<$crs84> POINT(-8.55 -47.37)").isRight, parse(s"<$crs84> POINT(8.55E1 47.37)").isRight)
      },
    ),
    suite("rejects")(
      test("EPSG:4326, naming CRS84 as the replacement") {
        val message = rejection(s"<${Crs.RejectedEpsg4326}> POINT(8.55 47.37)")
        assertTrue(message.contains(crs84), message.contains("latitude"))
      },
      test("a CRS outside the allowlist, listing what is supported") {
        val message = rejection("<http://www.opengis.net/def/crs/EPSG/0/3857> POINT(8.55 47.37)")
        assertTrue(message.contains(crs84), message.contains(lv95), message.contains(lv03))
      },
      test("a literal that is not a CRS IRI in angle brackets followed by a WKT geometry") {
        assertTrue(
          parse("8.55 47.37").isLeft,
          parse("").isLeft,
          parse(s"<$crs84 POINT(8.55 47.37)").isLeft,
          parse(s"<$crs84>POINT(8.55 47.37)").isLeft, // the LWSP separator is not optional
        )
      },
      test("an out-of-range coordinate, naming the violated ordinate and the CRS") {
        val message = rejection(s"<$crs84> POINT(8.55 947.37)")
        assertTrue(message.contains("latitude"), message.contains(Crs.Crs84.label))
      },
      test("LV95 coordinates submitted as CRS84, and the reverse") {
        assertTrue(parse(s"<$crs84> POINT(2600000 1200000)").isLeft, parse(s"<$lv95> POINT(8.55 47.37)").isLeft)
      },
      test("transposed Swiss coordinates, whose ranges do not overlap") {
        val message = rejection(s"<$lv95> POINT(1200000 2600000)")
        assertTrue(message.contains("easting"), message.contains(Crs.Lv95.label))
      },
      test("any geometry other than a POINT") {
        assertTrue(
          rejection(s"<$crs84> LINESTRING(8.55 47.37, 8.56 47.38)").contains("LINESTRING"),
          rejection(s"<$crs84> POLYGON((8.55 47.37, 8.56 47.38, 8.57 47.39, 8.55 47.37))").contains("POLYGON"),
          parse(s"<$crs84> MULTIPOINT((8.55 47.37))").isLeft,
        )
      },
      test("a third ordinate, whether tagged POINT Z or merely present") {
        assertTrue(
          rejection(s"<$crs84> POINT Z (8.55 47.37 400)").contains("POINT Z"),
          rejection(s"<$crs84> POINT(8.55 47.37 400)").contains("ordinates"),
        )
      },
      test("a POINT carrying no coordinate pair") {
        assertTrue(
          parse(s"<$crs84> POINT EMPTY").isLeft,
          parse(s"<$crs84> POINT()").isLeft,
          parse(s"<$crs84> POINT(8.55)").isLeft,
        )
      },
      test("an ordinate that is not a number, rather than letting the numeric parse escape") {
        val message = rejection(s"<$crs84> POINT(eight 47.37)")
        assertTrue(message.contains("not a number"))
      },
      test("a comma-separated pair, which would be a multi-point geometry") {
        assertTrue(parse(s"<$crs84> POINT(8.55, 47.37)").isLeft)
      },
    ),
    suite("the accepted gap")(
      // A CRS84 pair transposed within ±90 validates: 47.37 is a legal longitude and 8.55 a legal
      // latitude, so the value is stored as a point off Somalia. Closing this needs either a
      // project-extent hint or a map, both out of scope. The test records the gap so that a later
      // reader does not mistake its absence for an oversight.
      test("does not detect a CRS84 pair transposed within ±90") {
        assertTrue(accepted(s"<$crs84> POINT(47.37 8.55)") == Geolocation(Crs.Crs84, "47.37", "8.55"))
      },
    ),
    suite("valueHasString")(
      test("is the bare space-separated coordinates, without CRS prefix or geometry wrapper") {
        assertTrue(accepted(s"<$crs84> POINT(8.550 47.37)").coordinates == "8.550 47.37")
      },
    ),
    suite("decompose (the lenient read path)")(
      test("splits a stored point into CRS, shape and coordinates") {
        val parts = Geolocation.decompose(s"<$lv95> POINT(2600000 1200000)")
        assertTrue(parts.crs == lv95, parts.shape == "Point", parts.coordinates == "2600000 1200000")
      },
      test("never fails on a line or an area, which the write path rejects") {
        val line = Geolocation.decompose(s"<$crs84> LINESTRING(8.55 47.37, 8.56 47.38)")
        val area = Geolocation.decompose(s"<$crs84> POLYGON((8.55 47.37, 8.56 47.38, 8.57 47.39, 8.55 47.37))")
        assertTrue(
          line.shape == "LineString",
          line.coordinates == "8.55 47.37, 8.56 47.38",
          area.shape == "Polygon",
        )
      },
      test("never fails on an elevation") {
        val parts = Geolocation.decompose(s"<$crs84> POINT Z (8.55 47.37 400)")
        assertTrue(parts.shape == "PointZ", parts.coordinates == "8.55 47.37 400")
      },
      test("reports the GeoSPARQL default CRS for an untagged literal") {
        assertTrue(Geolocation.decompose("POINT(8.55 47.37)").crs == crs84)
      },
      test("passes an unrecognised CRS and an empty geometry through as stored") {
        val unknown = Geolocation.decompose("<http://example.org/crs/1> POINT(1 2)")
        val empty   = Geolocation.decompose(s"<$crs84> POINT EMPTY")
        assertTrue(unknown.crs == "http://example.org/crs/1", empty.shape == "Point", empty.coordinates.isEmpty)
      },
    ),
  )
}
