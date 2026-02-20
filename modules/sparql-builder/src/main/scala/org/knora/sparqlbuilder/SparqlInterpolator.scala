/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

/** Type of values that can be interpolated into `sparql"..."` fragments.
 * This includes all `SparqlValue` subtypes (Iri, Variable, Literal) and `Fragment` itself.
 */
type Interpolatable = SparqlValue | Fragment

/** Provides the `sparql"..."` string interpolator for building safe SPARQL fragments.
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
 */
extension (sc: StringContext)
  def sparql(args: Interpolatable*): Fragment = {
    val parts   = sc.parts.iterator
    val values  = args.iterator
    val builder = Vector.newBuilder[Fragment.Part]

    builder += Fragment.RawPart(StringContext.processEscapes(parts.next()))

    while (parts.hasNext) {
      val value = values.next()
      value match {
        case sv: SparqlValue =>
          builder += Fragment.ValuePart(sv.render)
        case frag: Fragment =>
          builder ++= frag.parts
      }
      builder += Fragment.RawPart(StringContext.processEscapes(parts.next()))
    }

    Fragment.fromParts(builder.result())
  }
