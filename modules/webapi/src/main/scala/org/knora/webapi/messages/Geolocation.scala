/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages

import scala.util.Try

import org.knora.webapi.IRI

/**
 * The coordinate reference systems a geolocation value may be tagged with, identified by OGC
 * definition IRI. The list is closed on purpose: with a known CRS, coordinates can be bounds-checked,
 * and every entry declares its ordinates in the same order — the first is always X (longitude for
 * geographic systems, easting for projected ones), the second always Y (latitude or northing).
 *
 * `EPSG/0/4326` is deliberately absent: it formally declares latitude first, so admitting it would put
 * two axis orders in one field. WGS84 is identified as `OGC/1.3/CRS84`, which is longitude-first and is
 * also the default for an untagged literal.
 *
 * Bounds are inclusive. They are mirrored in dsp-app's geolocation component for inline feedback; this
 * table is the authoritative one.
 */
enum Crs(
  val iri: IRI,
  val label: String,
  val xName: String,
  val yName: String,
  val xMin: BigDecimal,
  val xMax: BigDecimal,
  val yMin: BigDecimal,
  val yMax: BigDecimal,
) {
  case Crs84
      extends Crs(
        "http://www.opengis.net/def/crs/OGC/1.3/CRS84",
        "WGS84 (CRS84)",
        "longitude",
        "latitude",
        BigDecimal("-180"),
        BigDecimal("180"),
        BigDecimal("-90"),
        BigDecimal("90"),
      )

  case Lv95
      extends Crs(
        "http://www.opengis.net/def/crs/EPSG/0/2056",
        "Swiss LV95",
        "easting",
        "northing",
        BigDecimal("2484273.3"),
        BigDecimal("2837939.88"),
        BigDecimal("1073150.16"),
        BigDecimal("1299970.97"),
      )

  case Lv03
      extends Crs(
        "http://www.opengis.net/def/crs/EPSG/0/21781",
        "Swiss LV03",
        "easting",
        "northing",
        BigDecimal("484273.3"),
        BigDecimal("837939.88"),
        BigDecimal("73150.16"),
        BigDecimal("299970.97"),
      )
}

object Crs {

  /** The CRS an untagged literal is understood to be in, per GeoSPARQL 1.1 §10.8. */
  val Default: Crs = Crs.Crs84

  /** WGS84 with latitude-first axis order. Rejected in favour of [[Crs.Crs84]]. */
  val RejectedEpsg4326: IRI = "http://www.opengis.net/def/crs/EPSG/0/4326"

  private val byIri: Map[IRI, Crs] = Crs.values.toSeq.map(crs => crs.iri -> crs).toMap

  def fromIri(iri: IRI): Option[Crs] = byIri.get(iri)

  def allIris: Seq[IRI] = Crs.values.toSeq.map(_.iri)
}

/**
 * A validated geographic location: a point in a known CRS. The ordinates are kept as the lexical forms
 * that were submitted and are never re-serialised through a numeric type, so submitted decimal
 * precision survives storage and retrieval (`8.550` stays `8.550`).
 */
final case class Geolocation(crs: Crs, x: String, y: String) {

  /**
   * The literal as it is stored: always CRS-tagged, so no stored value is ambiguous, with the
   * ordinates verbatim.
   */
  def toStoredLiteral: String = s"<${crs.iri}> POINT($x $y)"

  /** The bare space-separated coordinates, used as `valueHasString`. */
  def coordinates: String = s"$x $y"
}

/**
 * The decomposed parts of a stored literal, as exposed to clients so that they need not parse the
 * literal themselves. Produced by a deliberately lenient parse: reading must never fail on a geometry
 * the write path rejects, because the storage form accommodates lines, areas and elevation even though
 * this release does not accept them.
 */
final case class GeolocationParts(crs: IRI, shape: String, coordinates: String)

object Geolocation {

  private val CrsPrefix = """^<([^<>\s]*)>(\s+)(.*)$""".r
  private val Geometry  = """^([A-Za-z]+(?:\s+[A-Za-z]+)*)\s*(?:\((.*)\)|(EMPTY))$""".r

  private val canonicalShapes: Map[String, String] = Map(
    "POINT"              -> "Point",
    "LINESTRING"         -> "LineString",
    "POLYGON"            -> "Polygon",
    "MULTIPOINT"         -> "MultiPoint",
    "MULTILINESTRING"    -> "MultiLineString",
    "MULTIPOLYGON"       -> "MultiPolygon",
    "GEOMETRYCOLLECTION" -> "GeometryCollection",
  )

  /**
   * Validates a submitted `wktLiteral`: an optional CRS definition IRI in angle brackets, linear
   * whitespace, then a WKT geometry. Only a 2D `POINT` in an allowlisted CRS is accepted.
   *
   * Returns a message naming the problem rather than an empty [[Option]], because a coordinate can be
   * wrong in ways the author needs told apart — an unsupported CRS, a swapped axis surfacing as an
   * out-of-range ordinate, or a geometry not yet accepted.
   */
  def parse(literal: String): Either[String, Geolocation] =
    for {
      (crs, geometry) <- splitCrs(literal.trim)
      (x, y)          <- parsePoint(geometry)
      _               <- checkBounds(crs, x, y)
    } yield Geolocation(crs, x, y)

  /**
   * Decomposes a stored literal into its CRS, shape and coordinates. Never fails: an unrecognised CRS
   * or geometry is passed through as it was stored.
   */
  def decompose(literal: String): GeolocationParts = {
    val (crsIri, geometry) = literal.trim match {
      case CrsPrefix(iri, _, rest) => (iri, rest.trim)
      case rest                    => (Crs.Default.iri, rest)
    }
    geometry match {
      case Geometry(keyword, body, empty) =>
        val words = keyword.trim.split("""\s+""").toSeq
        val shape = canonicalShapes.getOrElse(words.head.toUpperCase, words.head) +
          words.tail.map(_.toUpperCase).mkString
        GeolocationParts(crsIri, shape, if (empty != null || body == null) "" else body.trim)
      case other => GeolocationParts(crsIri, "", other)
    }
  }

  private def splitCrs(literal: String): Either[String, (Crs, String)] =
    literal match {
      case CrsPrefix(iri, _, _) if iri == Crs.RejectedEpsg4326 =>
        Left(
          s"Unsupported coordinate reference system <$iri>: it declares latitude before longitude. " +
            s"Use <${Crs.Crs84.iri}> instead, which is WGS84 with longitude first, and give the " +
            s"coordinates as longitude then latitude.",
        )
      case CrsPrefix(iri, _, rest) =>
        Crs
          .fromIri(iri)
          .map(crs => (crs, rest.trim))
          .toRight(
            s"Unsupported coordinate reference system <$iri>. Supported are: " +
              Crs.allIris.map(i => s"<$i>").mkString(", ") + ".",
          )
      case _ if literal.startsWith("<") =>
        Left(
          "Malformed geolocation: a coordinate reference system must be given as a definition IRI in " +
            "angle brackets followed by whitespace, e.g. " +
            s"'<${Crs.Crs84.iri}> POINT(8.55 47.37)'.",
        )
      case rest =>
        // An untagged literal is CRS84 by GeoSPARQL's default; it is stored with the prefix made explicit.
        Right((Crs.Default, rest))
    }

  private def parsePoint(geometry: String): Either[String, (String, String)] =
    geometry match {
      case Geometry(keyword, body, empty) =>
        val words = keyword.trim.split("""\s+""").toSeq
        if (!words.head.equalsIgnoreCase("POINT")) {
          Left(
            s"Unsupported geometry '${words.head.toUpperCase}': only POINT is accepted. " +
              "Lines and areas are not yet supported.",
          )
        } else if (words.sizeIs > 1) {
          Left(
            s"Unsupported geometry '${words.map(_.toUpperCase).mkString(" ")}': only a two-dimensional " +
              "POINT is accepted. An elevation is not yet supported.",
          )
        } else if (empty != null || body == null || body.trim.isEmpty) {
          Left("Malformed geolocation: POINT carries no coordinate pair.")
        } else {
          ordinatesOf(body.trim)
        }
      case _ =>
        Left(
          "Malformed geolocation: expected an optional coordinate reference system IRI in angle " +
            s"brackets followed by a WKT geometry, e.g. '<${Crs.Crs84.iri}> POINT(8.55 47.37)'.",
        )
    }

  private def ordinatesOf(body: String): Either[String, (String, String)] = {
    val ordinates = body.split("""\s+""").toSeq
    if (body.contains(",")) {
      Left("Malformed geolocation: a POINT carries a single coordinate pair, separated by whitespace.")
    } else if (ordinates.sizeIs > 2) {
      Left(
        s"Unsupported geometry: POINT carries ${ordinates.size} ordinates. Only a two-dimensional " +
          "POINT is accepted; an elevation is not yet supported.",
      )
    } else if (ordinates.sizeIs < 2) {
      Left("Malformed geolocation: POINT carries no coordinate pair.")
    } else {
      val x = ordinates.head
      val y = ordinates(1)
      for {
        _ <- asNumber(x, "first")
        _ <- asNumber(y, "second")
      } yield (x, y)
    }
  }

  // The numeric parse, not a regex, is the authority on what is a number, so that a malformed ordinate
  // is a rejection rather than an escaping NumberFormatException.
  private def asNumber(ordinate: String, position: String): Either[String, BigDecimal] =
    Try(BigDecimal(ordinate)).toEither.left.map(_ =>
      s"Malformed geolocation: the $position ordinate '$ordinate' is not a number.",
    )

  private def checkBounds(crs: Crs, x: String, y: String): Either[String, Unit] =
    for {
      xNum <- asNumber(x, "first")
      yNum <- asNumber(y, "second")
      _    <- inRange(xNum, crs.xMin, crs.xMax, crs.xName, crs)
      _    <- inRange(yNum, crs.yMin, crs.yMax, crs.yName, crs)
    } yield ()

  // BigDecimal comparison ignores scale and has no signed zero, so -0.0 checks as 0.0.
  private def inRange(
    value: BigDecimal,
    min: BigDecimal,
    max: BigDecimal,
    name: String,
    crs: Crs,
  ): Either[String, Unit] =
    if (value >= min && value <= max) { Right(()) }
    else { Left(s"The $name $value is outside the valid range for ${crs.label}: $min to $max inclusive.") }
}
