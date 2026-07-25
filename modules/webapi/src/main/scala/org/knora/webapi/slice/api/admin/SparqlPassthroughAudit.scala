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
 * Three emitters produce it -- the rest service's per-call report, and the server's security-failure and
 * decode-failure hooks -- and a log query over `outcome=` is the operational control for the surface, so their field
 * sets must not drift apart. An unknown value renders as `-` rather than being dropped, so every field is always
 * present for a parser.
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
      s"sparql=${or(statement.map(SparqlPassthroughAudit.bounded))}"
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
   * Truncates the statement to the audit bound and replaces control characters.
   *
   * The replacement is not cosmetic: a newline inside the statement would otherwise let a caller emit text that reads
   * as a second, forged log entry on a surface whose log *is* the control.
   */
  private def bounded(statement: String): String =
    statement.take(maxStatementChars).map(c => if (c.isControl) ' ' else c)
}
