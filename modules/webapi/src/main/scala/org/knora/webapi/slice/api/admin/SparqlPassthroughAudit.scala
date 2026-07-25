/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import zio.UIO
import zio.ZIO

import org.knora.webapi.slice.admin.domain.model.User

/**
 * The single audit entry this surface emits per call, and the only place its shape is defined.
 *
 * Three emitters produce it -- the rest service's per-call report, the endpoint's security logic, and the server's
 * decode-failure hook -- and a log query over `outcome=` is the operational control for the surface, so their field
 * sets must not drift apart. An unknown value renders as `-` rather than being dropped, so every field is always
 * present for a parser.
 *
 * Every field but `sparql` is machine-generated or a validated value type -- `outcome` comes from a fixed vocabulary,
 * `username` is constrained to `[a-zA-Z0-9._-]{3,50}`, the rest are numbers -- so only `sparql` can carry text a
 * caller chose. That one is therefore quoted and escaped rather than written bare; see `quoted` below for why that
 * matters and what a log query must do about it.
 *
 * `user.id` is used rather than `user.userIri.value`: the latter re-validates the IRI and throws if it does not
 * parse, which would let the log line fail the request it is only supposed to describe.
 */
final case class SparqlPassthroughAudit(
  outcome: String,
  user: Option[User] = None,
  durationMs: Option[Long] = None,
  storeStatus: Option[Int] = None,
  responseBytes: Option[Int] = None,
  requestBytes: Option[Int] = None,
  statement: Option[String] = None,
) {

  def render: String = {
    val truncated = statement.exists(_.length > SparqlPassthroughAudit.maxStatementChars)
    "SPARQL passthrough: " +
      s"operation=query outcome=$outcome " +
      s"user_iri=${or(user.map(_.id))} username=${or(user.map(_.username))} " +
      s"duration_ms=${or(durationMs)} store_status=${or(storeStatus)} response_bytes=${or(responseBytes)} " +
      s"request_bytes=${or(requestBytes)} sparql_truncated=$truncated " +
      s"sparql=${statement.fold("-")(SparqlPassthroughAudit.quoted)}"
  }

  def log: UIO[Unit] = ZIO.logInfo(render)

  private def or(value: Option[Any]): String = value.fold("-")(_.toString)
}

object SparqlPassthroughAudit {

  /**
   * The audit bound on the logged statement, in characters.
   *
   * The statement is caller-supplied and only loosely bounded on the way in, and it is written verbatim to stdout and
   * on to the log backend. The audit value is in seeing what was run, which a bounded prefix preserves; echoing a
   * multi-megabyte body would not add to it.
   */
  val maxStatementChars: Int = 4096

  /**
   * Renders the statement as the entry's one quoted field: bounded, stripped of line-breaking and invisible
   * characters, and wrapped in double quotes with `\` and `"` escaped.
   *
   * Neither half is cosmetic, and they close two different forgeries on a surface whose log *is* the control.
   * Stripping stops *line* forging: a newline would otherwise let the statement emit text that reads as a second
   * entry. Quoting stops *field* forging: the entry is space-separated `k=v`, so a bare statement containing
   * `outcome=ok user_iri=someone-else` would satisfy a naive field match and attribute the call to another
   * principal -- and the adversary here is precisely the party the entry exists to attribute.
   *
   * Quoting makes the boundary unambiguous rather than making the text disappear, so a log query must respect it.
   * What the entry's unforgeability rests on is *position* plus *parsing*, not the prefix: nothing here strips
   * letters, spaces, `:` or `=`, so a statement can contain `SPARQL passthrough: operation=query outcome=` verbatim.
   * `sparql` is deliberately the last field, so every field a reader needs precedes the only value that can carry
   * arbitrary text -- which means a logfmt-aware parse that honours the quoting reads the real values, and so does
   * taking the *first* match for a field. Taking any match anywhere in the line does not.
   *
   * A literal line filter (Loki's `|= "... outcome=forbidden"`) is the one query shape this does not protect: it can
   * match text a caller planted. That is a false-positive alert rather than a misattribution -- parsed as above, the
   * outcome and the identity are still the real ones -- but an alert built that way will fire on demand.
   */
  private def quoted(statement: String): String = {
    val safe = statement.take(maxStatementChars).map(c => if (isLogUnsafe(c)) ' ' else c)
    "\"" + safe.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
  }

  /**
   * Characters that must not survive into the entry because they break or disguise its one-line, one-entry shape.
   *
   * `isControl` covers C0/C1, which is where the newline forgery lives. It does not cover U+2028/U+2029, which some
   * log pipelines and viewers treat as line terminators, nor the Cf format characters -- the bidi overrides
   * (U+202A-202E, U+2066-2069) among them, which can make rendered text read in an order the bytes do not have.
   */
  private def isLogUnsafe(c: Char): Boolean =
    c.isControl || c == '\u2028' || c == '\u2029' || Character.getType(c) == Character.FORMAT
}
