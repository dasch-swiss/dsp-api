/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

/**
 * A SPARQL text fragment that composes safely. Values are escaped at interpolation time
 * (since SPARQL has no parameterized query protocol). Fragments compose via `++` (monoid).
 *
 * Inspired by Doobie's `Fragment` type.
 */
final case class Fragment private (parts: Vector[Fragment.Part]) { self =>

  /** Concatenate two fragments. */
  def ++(other: Fragment): Fragment = Fragment(self.parts ++ other.parts)

  /** Render this fragment to a SPARQL string. */
  def render: String = parts.map(_.render).mkString

  /** Render with a trailing newline for readability. */
  def renderLn: String = render + "\n"

  override def toString: String = render
}

object Fragment {

  /** A single part of a fragment — either raw SPARQL text or an interpolated value. */
  sealed trait Part {
    def render: String
  }

  /** Raw SPARQL text (trusted). */
  private[sparqlbuilder] final case class RawPart(text: String) extends Part {
    def render: String = text
  }

  /** An interpolated value (already escaped at construction time). */
  private[sparqlbuilder] final case class ValuePart(escaped: String) extends Part {
    def render: String = escaped
  }

  /** The empty fragment — identity element for `++`. */
  val empty: Fragment = Fragment(Vector.empty)

  /**
   * Create a fragment from raw SPARQL text. Use with caution — no escaping is performed.
   * This is the only injection-risk surface in the library.
   */
  def raw(sparql: String): Fragment = Fragment(Vector(RawPart(sparql)))

  /** Create a fragment from a single interpolated value. */
  private[sparqlbuilder] def fromParts(parts: Vector[Part]): Fragment = Fragment(parts)

  /** Combine multiple optional fragments, discarding `None` values. */
  def combine(fragments: Option[Fragment]*): Fragment =
    fragments.flatten.foldLeft(Fragment.empty)(_ ++ _)

  /** Combine a sequence of fragments with a separator. */
  def join(fragments: Iterable[Fragment], separator: Fragment = Fragment.raw(" ")): Fragment =
    if (fragments.isEmpty) Fragment.empty
    else fragments.reduce(_ ++ separator ++ _)

  /** Extension to combine a collection of fragments via monoid. */
  extension (fragments: Iterable[Fragment]) def combineAll: Fragment = fragments.foldLeft(Fragment.empty)(_ ++ _)
}
