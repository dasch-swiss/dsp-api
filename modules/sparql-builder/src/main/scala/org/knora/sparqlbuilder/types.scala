/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

/**
 * Typed values that can be safely interpolated into SPARQL fragments.
 *
 * This sealed hierarchy ensures that only safe types can be interpolated via `sparql"..."`.
 * Raw strings cannot be interpolated — use `Fragment.raw("...")` for that (explicit escape hatch).
 *
 * All constructors validate: an `Iri` can only hold characters legal inside a SPARQL
 * `IRIREF`, a `Variable` name is restricted to `VARNAME` characters, and language tags
 * must match the SPARQL `LANGTAG` production. There is no unvalidated path — `unsafeFrom`
 * throws instead of returning an `Either`, but never constructs an invalid value.
 */
sealed trait SparqlValue {
  def render: String
  def toFragment: Fragment = Fragment.fromParts(Vector(Fragment.ValuePart(render)))
}

/** A SPARQL IRI — rendered as `<uri>`. */
final case class Iri private (value: String) extends SparqlValue {
  def render: String = s"<$value>"
}

object Iri {

  /**
   * Characters that terminate or escape a SPARQL `IRIREF` and therefore must never occur
   * inside one: `< > " { } | ^ ` \` plus space and control characters (U+0000–U+0020).
   */
  private def invalidChar(c: Char): Boolean =
    c <= ' ' || "<>\"{}|^`\\".contains(c)

  /** Create an IRI, rejecting any value that could break out of the `<...>` wrapper. */
  def from(value: String): Either[String, Iri] =
    if (value.isEmpty) Left("IRI must not be empty")
    else
      value.find(invalidChar) match {
        case Some(c) =>
          Left(
            s"IRI contains character '$c' (U+${c.toInt.toHexString.toUpperCase}) not allowed in a SPARQL IRIREF: $value",
          )
        case None => Right(new Iri(value))
      }

  /** Create an IRI from a value known to be valid; throws [[IllegalArgumentException]] otherwise. */
  def unsafeFrom(value: String): Iri =
    from(value).fold(msg => throw new IllegalArgumentException(msg), identity)
}

/** A SPARQL variable — rendered as `?name`. */
final case class Variable private (name: String) extends SparqlValue {
  def render: String = s"?$name"
}

object Variable {

  private val ValidName = "[A-Za-z0-9_]+".r

  /**
   * Create a variable. Names are restricted to `[A-Za-z0-9_]+` (an ASCII subset of the
   * SPARQL `VARNAME` production); anything else throws [[IllegalArgumentException]].
   * Variable names are developer-written constants, so failing fast here is appropriate.
   */
  def apply(name: String): Variable =
    from(name).fold(msg => throw new IllegalArgumentException(msg), identity)

  def from(name: String): Either[String, Variable] =
    if (ValidName.matches(name)) Right(new Variable(name))
    else Left(s"Variable name must match [A-Za-z0-9_]+: $name")
}

/**
 * A SPARQL literal value — rendered with proper escaping.
 *
 * A `Literal` holds only its final rendered form and can only be obtained through the
 * factory methods on the companion, each of which escapes or validates its input. There
 * is no way to construct a `Literal` that renders unescaped content.
 */
final class Literal private (val render: String) extends SparqlValue {
  override def equals(other: Any): Boolean = other match {
    case that: Literal => render == that.render
    case _             => false
  }
  override def hashCode: Int    = render.hashCode
  override def toString: String = render
}

object Literal {

  private val XsdDecimal  = Iri.unsafeFrom("http://www.w3.org/2001/XMLSchema#decimal")
  private val XsdDouble   = Iri.unsafeFrom("http://www.w3.org/2001/XMLSchema#double")
  private val XsdDateTime = Iri.unsafeFrom("http://www.w3.org/2001/XMLSchema#dateTime")
  private val XsdAnyUri   = Iri.unsafeFrom("http://www.w3.org/2001/XMLSchema#anyURI")

  /** SPARQL `LANGTAG` production: `[a-zA-Z]+ ('-' [a-zA-Z0-9]+)*`. */
  private val ValidLangTag = "[A-Za-z]+(-[A-Za-z0-9]+)*".r

  def string(value: String): Literal = new Literal(s""""${escape(value)}"""")

  /** Create a language-tagged string; throws [[IllegalArgumentException]] on an invalid language tag. */
  def langString(value: String, lang: String): Literal =
    if (ValidLangTag.matches(lang)) new Literal(s""""${escape(value)}"@$lang""")
    else throw new IllegalArgumentException(s"Invalid SPARQL language tag: $lang")

  def typed(value: String, datatype: Iri): Literal = new Literal(s""""${escape(value)}"^^${datatype.render}""")
  def int(value: Int): Literal                     = new Literal(value.toString)
  def long(value: Long): Literal                   = new Literal(value.toString)
  def double(value: Double): Literal               = new Literal(renderDouble(value))
  def decimal(value: BigDecimal): Literal          = new Literal(s""""$value"^^${XsdDecimal.render}""")
  def bool(value: Boolean): Literal                = new Literal(value.toString)
  def dateTime(value: java.time.Instant): Literal  = typed(value.toString, XsdDateTime)
  def anyUri(value: String): Literal               = typed(value, XsdAnyUri)

  /**
   * Escape a string for use inside a double-quoted SPARQL string literal.
   *
   * The escape set is byte-for-byte identical to RDF4J's (`Rdf.literalOf(v).getQueryString`),
   * covering the full SPARQL `ECHAR` production: `\ " ' \t \b \n \r \f`. Parity is pinned
   * down by `Rdf4jEscapingSpec`, so migrated queries render identically to their RDF4J
   * SparqlBuilder predecessors.
   */
  private[sparqlbuilder] def escape(s: String): String =
    s.replace("\\", "\\\\")
      .replace("\t", "\\t")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\b", "\\b")
      .replace("\f", "\\f")
      .replace("\"", "\\\"")
      .replace("'", "\\'")

  /**
   * Finite doubles render as plain SPARQL numeric literals; NaN and the infinities are not
   * valid SPARQL number tokens, so they render as typed literals using the `xsd:double`
   * lexical forms `NaN`, `INF`, and `-INF`.
   */
  private def renderDouble(v: Double): String =
    if (java.lang.Double.isFinite(v)) v.toString
    else {
      val lexical = if (v.isNaN) "NaN" else if (v > 0) "INF" else "-INF"
      s""""$lexical"^^${XsdDouble.render}"""
    }
}
