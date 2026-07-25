/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

import org.junit.runner.RunWith
import sttp.model.QueryParams
import sttp.model.StatusCode
import zio.*
import zio.test.*

import java.nio.charset.StandardCharsets
import scala.annotation.tailrec

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo.builtIn.SystemProject
import org.knora.webapi.slice.api.admin.SparqlPassthroughAudit
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.RawSparqlResponse
import org.knora.webapi.store.triplestore.errors.SparqlPassthroughException
import org.knora.webapi.store.triplestore.errors.SparqlPassthroughTimedOutException
import org.knora.webapi.store.triplestore.errors.SparqlResponseTooLargeException
import org.knora.webapi.store.triplestore.errors.SparqlStoreUnavailableException
import org.knora.webapi.store.triplestore.errors.SparqlUpstreamRejectedException

/**
 * The audit entry is the operational control for this surface, so it is tested as one: every outcome the service can
 * reach produces exactly one entry carrying that outcome, and the caller-supplied text in it is bounded.
 *
 * Driving the outcomes needs a store that can be told what to do, because most of them -- an unreachable store, a
 * rejected credential, the deadline, an abandoned call -- are precisely the ones a working store does not produce.
 *
 * Three outcomes are not covered here because the service never sees them: `unauthenticated` and the `forbidden`
 * raised by the endpoint's security logic, and `malformed-request` / `request-cap-exceeded` from the server's
 * decode-failure hook. Those are asserted -- as entries, not just as status codes -- by
 * `org.knora.webapi.core.SparqlPassthroughInterceptorSpec`, which runs requests through the real interceptor chain.
 * The `forbidden` asserted below is the service's own backstop check.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class SparqlPassthroughAuditSpec extends ZIOSpecDefault {

  private val normalUser =
    User(
      "http://rdfh.ch/users/sparql-passthrough-audit",
      "username",
      "email@example.com",
      "given name",
      "family name",
      status = true,
      "lang",
    )

  private val systemAdmin = normalUser.copy(permissions =
    PermissionsDataADM(Map(SystemProject.id.value -> List(KnoraGroupRepo.builtIn.SystemAdmin.id.value))),
  )

  private val selectQuery = "SELECT ?s WHERE { ?s ?p ?o } LIMIT 1"

  private def utf8(s: String) = s.getBytes(StandardCharsets.UTF_8)

  private def ok(body: String) =
    RawSparqlResponse(StatusCode.Ok, Some("application/sparql-results+json"), utf8(body))

  private def query(
    user: User = systemAdmin,
    sparql: String = selectQuery,
    params: QueryParams = QueryParams.fromSeq(Seq.empty),
  ) = ZIO.serviceWithZIO[SparqlPassthroughRestService](_.query(user)(sparql, None, params))

  /** The passthrough's own entries, which is what a log query on this surface selects. */
  private val auditEntries: UIO[Chunk[String]] =
    ZTestLogger.logOutput.map(_.map(_.message()).filter(_.startsWith("SPARQL passthrough:")))

  private def outcomeOf(entry: String): String = field(entry, "outcome")

  /**
   * Reads one field the way a correct log query has to read it: fields are space-separated `k=v`, but a value that
   * opens with `"` runs to the next unescaped `"`. That matters because the quoted `sparql` field is the only place
   * in the entry where bytes a caller chose can appear, and a naive splitter would happily read `outcome=ok` out of
   * the middle of one.
   */
  private def field(entry: String, name: String): String = {
    val at = entry.indexOf(s" $name=")
    if (at < 0) ""
    else {
      val value = entry.substring(at + name.length + 2)
      if (value.startsWith("\"")) unquote(value.tail.toList, new StringBuilder)
      else value.takeWhile(_ != ' ')
    }
  }

  @tailrec
  private def unquote(rest: List[Char], acc: StringBuilder): String = rest match {
    case '\\' :: escaped :: tail => unquote(tail, acc.append(escaped))
    case '"' :: _                => acc.toString
    case c :: tail               => unquote(tail, acc.append(c))
    case Nil                     => acc.toString
  }

  private def storeAnswering(answer: IO[SparqlPassthroughException, RawSparqlResponse]): ULayer[TriplestoreService] =
    SparqlPassthroughTestEnv.stubStore(_ => answer)

  /** Runs `effect` against a programmed store and returns the audit entries the call produced. */
  private def entriesFor(
    store: ULayer[TriplestoreService],
    effect: ZIO[SparqlPassthroughRestService, Any, Any] = query(),
    overrides: (String, Any)*,
  ): UIO[Chunk[String]] =
    (effect.exit *> auditEntries).provide(SparqlPassthroughTestEnv.layerWithStore(store, overrides*))

  private def hasOneEntry(entries: Chunk[String], outcome: String) =
    assertTrue(entries.size == 1, outcomeOf(entries.head) == outcome)

  val spec: Spec[Any, Any] = suite("the SPARQL passthrough audit entry")(
    suite("exactly one entry per call, carrying the call's outcome")(
      test("ok") {
        entriesFor(storeAnswering(ZIO.succeed(ok("{}")))).map(hasOneEntry(_, "ok"))
      },
      test("store-error, because a store 4xx is relayed as a success value and would otherwise go unrecorded") {
        entriesFor(storeAnswering(ZIO.succeed(RawSparqlResponse(StatusCode.BadRequest, None, utf8("nope")))))
          .map(hasOneEntry(_, "store-error"))
      },
      test("store-unavailable") {
        entriesFor(storeAnswering(ZIO.fail(SparqlStoreUnavailableException.make)))
          .map(hasOneEntry(_, "store-unavailable"))
      },
      test("upstream-rejected") {
        entriesFor(storeAnswering(ZIO.fail(SparqlUpstreamRejectedException.make)))
          .map(hasOneEntry(_, "upstream-rejected"))
      },
      test("response-cap-exceeded") {
        entriesFor(storeAnswering(ZIO.fail(SparqlResponseTooLargeException.make(16))))
          .map(hasOneEntry(_, "response-cap-exceeded"))
      },
      test("timed-out") {
        entriesFor(storeAnswering(ZIO.fail(SparqlPassthroughTimedOutException.make(120))))
          .map(hasOneEntry(_, "timed-out"))
      },
      test("forbidden, from the service's own authorization backstop") {
        entriesFor(storeAnswering(ZIO.succeed(ok("{}"))), query(user = normalUser)).map(hasOneEntry(_, "forbidden"))
      },
      test("bad-request") {
        entriesFor(
          storeAnswering(ZIO.succeed(ok("{}"))),
          query(params = QueryParams.fromSeq(Seq("query" -> selectQuery))),
        ).map(hasOneEntry(_, "bad-request"))
      },
      test("request-cap-exceeded") {
        entriesFor(
          storeAnswering(ZIO.succeed(ok("{}"))),
          query(sparql = "SELECT ?s WHERE { ?s ?p ?o }"),
          "app.triplestore.sparql-passthrough.max-request-body-bytes" -> 10,
        ).map(hasOneEntry(_, "request-cap-exceeded"))
      },
      test("defect, so a bug behind the seam is still attributed rather than silently dropped") {
        entriesFor(storeAnswering(ZIO.die(new IllegalStateException("boom")))).map(hasOneEntry(_, "defect"))
      },
      test("interrupted, which tapBoth dropped entirely -- an abandoned call is what an audit trail most needs") {
        for {
          reached <- Promise.make[Nothing, Unit]
          entries <- (for {
                       fiber    <- query().fork
                       _        <- reached.await
                       _        <- fiber.interrupt
                       recorded <- auditEntries
                     } yield recorded)
                       .provide(
                         SparqlPassthroughTestEnv.layerWithStore(
                           SparqlPassthroughTestEnv.stubStore(_ => reached.succeed(()) *> ZIO.never),
                         ),
                       )
        } yield hasOneEntry(entries, "interrupted")
      },
      test("overloaded") {
        for {
          occupied <- Promise.make[Nothing, Unit]
          release  <- Promise.make[Nothing, Unit]
          entries  <- (for {
                       holder   <- query().fork
                       _        <- occupied.await
                       _        <- query().exit
                       _        <- release.succeed(())
                       _        <- holder.join
                       recorded <- auditEntries
                     } yield recorded)
                       .provide(
                         SparqlPassthroughTestEnv.layerWithStore(
                           SparqlPassthroughTestEnv
                             .stubStore(_ => occupied.succeed(()) *> release.await.as(ok("{}"))),
                           "app.triplestore.sparql-passthrough.max-concurrent-calls" -> 1,
                         ),
                       )
        } yield assertTrue(entries.size == 2, entries.map(outcomeOf).toSet == Set("ok", "overloaded"))
      },
    ),
    suite("what the entry may carry")(
      test("the statement is truncated to the audit bound, and the truncation is flagged") {
        val oversized = "# " + ("x" * (SparqlPassthroughAudit.maxStatementChars * 2)) + "\n" + selectQuery
        entriesFor(storeAnswering(ZIO.succeed(ok("{}"))), query(sparql = oversized)).map { entries =>
          val entry = entries.head
          assertTrue(
            entries.size == 1,
            field(entry, "sparql_truncated") == "true",
            // Everything before `sparql=` is fixed-size, so bounding the whole line bounds the statement.
            entry.length < SparqlPassthroughAudit.maxStatementChars + 512,
            field(entry, "request_bytes") == utf8(oversized).length.toString,
          )
        }
      },
      test("a statement refused before the store was reached is not echoed at all, only measured") {
        // The finding this pins: an over-cap or unauthorized body was logged verbatim, so a caller who never got past
        // the guardrails could still push arbitrary text into the log.
        val padded = "SELECT ?s WHERE { ?s ?p ?o }#" + ("PADDING-SENTINEL" * 64)
        entriesFor(
          storeAnswering(ZIO.succeed(ok("{}"))),
          query(sparql = padded),
          "app.triplestore.sparql-passthrough.max-request-body-bytes" -> 10,
        ).map { entries =>
          val entry = entries.head
          assertTrue(
            !entry.contains("PADDING-SENTINEL"),
            field(entry, "sparql") == "-",
            field(entry, "request_bytes") == utf8(padded).length.toString,
          )
        }
      },
      test("a newline in the statement cannot forge a second entry") {
        val forged = "ASK{}\nSPARQL passthrough: operation=query outcome=ok user_iri=root"
        entriesFor(storeAnswering(ZIO.succeed(ok("{}"))), query(sparql = forged))
          .map(entries => assertTrue(entries.size == 1, !entries.head.contains("\n")))
      },
      test("a line separator or a bidi format character cannot forge one either") {
        // U+2028/U+2029 are line terminators to several log pipelines and viewers but are not `Char.isControl`, and
        // the Cf bidi overrides can make the rendered text read in an order its bytes do not have. Both would have
        // survived the control-character strip on their own.
        val forged = "ASK{}\u2028SPARQL passthrough: outcome=ok\u2029x\u202Ereversed"
        entriesFor(storeAnswering(ZIO.succeed(ok("{}"))), query(sparql = forged)).map { entries =>
          val entry = entries.head
          assertTrue(
            entries.size == 1,
            !entry.contains("\u2028"),
            !entry.contains("\u2029"),
            !entry.contains("\u202E"),
          )
        }
      },
      test("a quote in the statement cannot forge a second field, so the entry still attributes the real caller") {
        // The finding this pins: the entry is space-separated `k=v` and `sparql=` was written bare, so a SystemAdmin
        // could embed `outcome=ok user_iri=<someone else>` in the statement and have it read as the entry's own
        // fields -- forging an attribution away from themselves on the one control that attributes them.
        val forged = """ASK{} # " outcome=ok user_iri=http://rdfh.ch/users/someone-else"""
        entriesFor(storeAnswering(ZIO.fail(SparqlStoreUnavailableException.make)), query(sparql = forged)).map {
          entries =>
            val entry = entries.head
            assertTrue(
              entries.size == 1,
              // Parsed as the quoting requires, the outcome and the identity are the real ones.
              outcomeOf(entry) == "store-unavailable",
              field(entry, "user_iri") == systemAdmin.id,
              // The forged text is not censored -- it is contained. It round-trips out of the quoted value intact.
              field(entry, "sparql") == forged,
              // And the quote that would have closed the value early is escaped instead.
              entry.contains("""\" outcome=ok"""),
            )
        }
      },
      test("a call that was admitted and then abandoned or crashed still records its statement") {
        // The counterpart of the refusal case above, and the reason `interrupted` and `defect` are not in
        // `refusedOutcomes`: both belong to a call this surface accepted and began running, so what was running is
        // exactly what the entry has to say. Pinned so the choice cannot drift back without a decision.
        for {
          onDefect    <- entriesFor(storeAnswering(ZIO.die(new IllegalStateException("boom"))))
          reached     <- Promise.make[Nothing, Unit]
          onInterrupt <- (for {
                           fiber    <- query().fork
                           _        <- reached.await
                           _        <- fiber.interrupt
                           recorded <- auditEntries
                         } yield recorded)
                           .provide(
                             SparqlPassthroughTestEnv.layerWithStore(
                               SparqlPassthroughTestEnv.stubStore(_ => reached.succeed(()) *> ZIO.never),
                             ),
                           )
        } yield assertTrue(
          field(onDefect.head, "sparql") == selectQuery,
          field(onInterrupt.head, "sparql") == selectQuery,
        )
      },
      test("the identity is recorded, which is what makes the entry an attribution") {
        entriesFor(storeAnswering(ZIO.succeed(ok("{}")))).map { entries =>
          assertTrue(
            field(entries.head, "user_iri") == systemAdmin.id,
            field(entries.head, "username") == systemAdmin.username,
          )
        }
      },
      test("request_bytes counts UTF-8 bytes, as the cap does, not UTF-16 code units") {
        val accented = "ASK{FILTER(?x=\"ééé\")}"
        entriesFor(storeAnswering(ZIO.succeed(ok("{}"))), query(sparql = accented)).map { entries =>
          assertTrue(
            utf8(accented).length != accented.length,
            field(entries.head, "request_bytes") == utf8(accented).length.toString,
          )
        }
      },
    ),
    suite("the span's attribute set")(
      test("is bounded: fixed-vocabulary strings and counters only, never the statement or the payload") {
        val attributes =
          SparqlPassthroughRestService.spanAttributes("ok", durationMs = 7, requestBytes = 42, Some(ok("{\"h\":{}}")))
        assertTrue(
          attributes.strings.toSet == Set(
            "sparql_passthrough.operation" -> "query",
            "sparql_passthrough.outcome"   -> "ok",
          ),
          attributes.longs.toSet == Set(
            "sparql_passthrough.duration_ms"    -> 7L,
            "sparql_passthrough.request_bytes"  -> 42L,
            "sparql_passthrough.response_bytes" -> 8L,
            "sparql_passthrough.store_status"   -> 200L,
          ),
        )
      },
      test("omits the response attributes for a call that produced no response") {
        val attributes =
          SparqlPassthroughRestService.spanAttributes("store-unavailable", durationMs = 3, requestBytes = 5, None)
        assertTrue(
          attributes.longs.toSet == Set(
            "sparql_passthrough.duration_ms"   -> 3L,
            "sparql_passthrough.request_bytes" -> 5L,
          ),
          attributes.strings.contains("sparql_passthrough.outcome" -> "store-unavailable"),
        )
      },
    ),
  ) @@ TestAspect.sequential
}
