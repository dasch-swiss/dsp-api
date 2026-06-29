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
 */
sealed trait SparqlValue {
  def render: String
  def toFragment: Fragment = Fragment.fromParts(Vector(Fragment.ValuePart(render)))
}

/** A SPARQL IRI — rendered as `<uri>`. */
final case class Iri(value: String) extends SparqlValue {
  def render: String = s"<$value>"
}

object Iri {

  /** Create an IRI from a trusted source (no validation). */
  def trusted(value: String): Iri = Iri(value)
}

/** A SPARQL variable — rendered as `?name`. */
final case class Variable(name: String) extends SparqlValue {
  def render: String = s"?$name"

  def desc: OrderBy = OrderBy.Desc(this)
  def asc: OrderBy  = OrderBy.Asc(this)
}

/** Order by clause direction. */
enum OrderBy {
  case Asc(variable: Variable)
  case Desc(variable: Variable)

  def render: String = this match {
    case Asc(v)  => v.render
    case Desc(v) => s"DESC(${v.render})"
  }
}

/** A SPARQL literal value — rendered with proper escaping. */
enum Literal extends SparqlValue {
  case StringLit(value: String)
  case LangString(value: String, lang: String)
  case TypedLit(value: String, datatype: Iri)
  case IntLit(value: Int)
  case LongLit(value: Long)
  case DoubleLit(value: Double)
  case DecimalLit(value: BigDecimal)
  case BoolLit(value: Boolean)

  def render: String = this match {
    case StringLit(v)     => s""""${escapeSparqlString(v)}""""
    case LangString(v, l) => s""""${escapeSparqlString(v)}"@$l"""
    case TypedLit(v, dt)  => s""""${escapeSparqlString(v)}"^^${dt.render}"""
    case IntLit(v)        => v.toString
    case LongLit(v)       => v.toString
    case DoubleLit(v)     => v.toString
    case DecimalLit(v)    => s""""$v"^^<http://www.w3.org/2001/XMLSchema#decimal>"""
    case BoolLit(v)       => v.toString
  }

  private def escapeSparqlString(s: String): String =
    s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
}

object Literal {
  def string(value: String): Literal                   = Literal.StringLit(value)
  def langString(value: String, lang: String): Literal = Literal.LangString(value, lang)
  def typed(value: String, datatype: Iri): Literal     = Literal.TypedLit(value, datatype)
  def int(value: Int): Literal                         = Literal.IntLit(value)
  def long(value: Long): Literal                       = Literal.LongLit(value)
  def double(value: Double): Literal                   = Literal.DoubleLit(value)
  def decimal(value: BigDecimal): Literal              = Literal.DecimalLit(value)
  def bool(value: Boolean): Literal                    = Literal.BoolLit(value)
  def dateTime(value: java.time.Instant): Literal      =
    Literal.TypedLit(value.toString, Iri.trusted("http://www.w3.org/2001/XMLSchema#dateTime"))
  def anyUri(value: String): Literal =
    Literal.TypedLit(value, Iri.trusted("http://www.w3.org/2001/XMLSchema#anyURI"))
}
