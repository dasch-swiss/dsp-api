/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

/**
 * Type of values that can be interpolated into `sparql"..."` fragments.
 * This includes all `SparqlValue` subtypes (Iri, Variable, Literal) and `Fragment` itself.
 */
type Interpolatable = SparqlValue | Fragment

/**
 * Provides the `sparql"..."` string interpolator for building safe SPARQL fragments.
 *
 * Usage:
 * {{{
 * import org.knora.sparqlbuilder.*
 *
 * val resource = Variable("resource")
 * val cls = Iri.trusted("http://example.org/MyClass")
 * val frag = sparql"$resource a $cls ."
 * // renders to: ?resource a <http://example.org/MyClass> .
 * }}}
 *
 * Interpolated values are type-checked and rendered safely. Only `Iri`, `Variable`,
 * `Literal`, and `Fragment` can be interpolated. Raw strings cannot be
 * interpolated directly — use `Fragment.raw("...")` for vendor-specific extensions.
 * Multiline templates must use a `|` margin on every line. A fragment in a standalone
 * hole inherits the hole's indentation; an empty fragment removes that complete line.
 */
extension (sc: StringContext)
  def sparql(args: Interpolatable*): Fragment = {
    val rawParts = sc.parts.toVector
    val parts    =
      if (rawParts.exists(_.contains('\n'))) SparqlInterpolator.stripAndValidateMargins(rawParts).toArray
      else rawParts.map(StringContext.processEscapes).toArray
    val values = args.toVector

    require(parts.length == values.length + 1, "SPARQL interpolation argument count does not match template holes")

    val omitted = Array.fill(values.length)(false)

    values.indices.foreach { index =>
      values(index) match {
        case fragment: Fragment
            if fragment.parts.isEmpty && SparqlInterpolator.isStandalone(parts(index), parts(index + 1)) =>
          val beforeLine = SparqlInterpolator.currentLine(parts(index))
          val afterLine  = SparqlInterpolator.nextLine(parts(index + 1))
          parts(index) = parts(index).dropRight(beforeLine.length)
          parts(index + 1) =
            if (parts(index + 1).length > afterLine.length) parts(index + 1).drop(afterLine.length + 1)
            else parts(index + 1).drop(afterLine.length)
          omitted(index) = true
        case _ => ()
      }
    }

    val builder = Vector.newBuilder[Fragment.Part]

    builder += Fragment.RawPart(parts.head)

    values.indices.foreach { index =>
      if (!omitted(index)) {
        values(index) match {
          case sv: SparqlValue =>
            builder += Fragment.ValuePart(sv.render)
          case fragment: Fragment =>
            val continuationIndent =
              if (SparqlInterpolator.isStandalone(parts(index), parts(index + 1)))
                SparqlInterpolator.currentLine(parts(index))
              else ""
            builder += Fragment.NestedPart(fragment, continuationIndent)
        }
      }
      builder += Fragment.RawPart(parts(index + 1))
    }

    Fragment.fromParts(builder.result())
  }

private object SparqlInterpolator {

  private def horizontalWhitespace(c: Char): Boolean = c == ' ' || c == '\t'

  def currentLine(text: String): String = {
    val newline = text.lastIndexOf('\n')
    text.substring(newline + 1)
  }

  def nextLine(text: String): String = {
    val newline = text.indexOf('\n')
    if (newline < 0) text else text.substring(0, newline)
  }

  def isStandalone(before: String, after: String): Boolean =
    currentLine(before).forall(horizontalWhitespace) && nextLine(after).forall(horizontalWhitespace)

  def stripAndValidateMargins(parts: Vector[String]): Vector[String] = {
    if (parts.headOption.forall(!_.startsWith("|")))
      throw new IllegalArgumentException("Multiline sparql templates must start with a '|' margin")

    var atLineStart = true

    parts.zipWithIndex.map { case (part, index) =>
      val stripped = new StringBuilder
      var offset   = 0

      while (offset < part.length) {
        if (atLineStart) {
          while (offset < part.length && horizontalWhitespace(part.charAt(offset))) offset += 1

          if (offset >= part.length || part.charAt(offset) != '|')
            throw new IllegalArgumentException("Every line in a multiline sparql template must have a '|' margin")
          atLineStart = false
          offset += 1
        } else {
          val current = part.charAt(offset)
          stripped += current
          atLineStart = current == '\n'
          offset += 1
        }
      }

      if (index < parts.length - 1 && atLineStart)
        throw new IllegalArgumentException("Every line in a multiline sparql template must have a '|' margin")

      StringContext.processEscapes(stripped.result())
    }
  }
}
