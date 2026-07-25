/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.impl

import org.junit.runner.RunWith
import sttp.client4.httpclient.zio.HttpClientZioBackend
import zio.*
import zio.test.*

import java.nio.charset.StandardCharsets

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.config.SparqlPassthroughConfig
import org.knora.webapi.config.Triplestore
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.RawSparqlRequest
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.RawSparqlResponse
import org.knora.webapi.store.triplestore.errors.SparqlResponseTooLargeException
import org.knora.webapi.store.triplestore.errors.SparqlStoreUnavailableException
import org.knora.webapi.store.triplestore.errors.SparqlUpstreamRejectedException

/**
 * `rawQuery` against a real Fuseki: the cases whose whole point is how the *store* behaves, and which the in-memory
 * double therefore cannot express -- the store's own error bodies, its timeout response, an unreachable store, and the
 * response ceiling tripping mid-read.
 *
 * Several cases need a guardrail set to a value no sane deployment would use, so they build their own
 * `TriplestoreServiceLive` against the same container with just that value changed. That is also why they construct it
 * directly rather than through its layer.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class TriplestoreServiceRawQueryIT extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject("test_data/project_data/anything-data.ttl", "http://www.knora.org/data/0001/anything"),
  )

  private val selectQuery = "SELECT ?s WHERE { ?s ?p ?o } LIMIT 5"

  /** Forces evaluation over the whole dataset while producing a single row, so a timeout is not raced by streaming. */
  private val expensiveQuery = "SELECT (COUNT(*) AS ?n) WHERE { ?a ?b ?c . ?d ?e ?f . ?g ?h ?i }"

  private def rawQuery(sparql: String, accept: Option[String] = None) =
    ZIO.serviceWithZIO[TriplestoreService](_.rawQuery(RawSparqlRequest(sparql, accept, Map.empty)))

  val e2eSpec: Spec[env, Any] = suite("TriplestoreServiceLive.rawQuery against a real store")(
    suite("content negotiation is left to the store")(
      test("a SELECT asked for TSV comes back as TSV") {
        rawQuery(selectQuery, Some("text/tab-separated-values")).map { response =>
          assertTrue(
            response.status.code == 200,
            response.contentType.exists(_.startsWith("text/tab-separated-values")),
            asString(response).startsWith("?s"),
          )
        }
      },
      test("a DESCRIBE is answered, although the typed query methods have no DESCRIBE case") {
        rawQuery("DESCRIBE <http://www.knora.org/ontology/knora-base#Resource>", Some("text/turtle")).map { response =>
          assertTrue(
            response.status.code == 200,
            response.contentType.exists(_.startsWith("text/turtle")),
          )
        }
      },
      test("an Accept the store will not honour is passed on, and the store's own choice is relayed") {
        // Measured against Fuseki 6.1.0: it does not refuse an Accept it cannot or will not satisfy -- neither an
        // unregistered media type nor Turtle asked of a SELECT, which cannot express a result set -- but falls back to
        // its default serialization. So the store's choice is what arrives, whatever it is, and this store gives the
        // relay path no 406 to carry. That the path would carry one is shown by the malformed-query case below, which
        // relays a store 4xx unchanged.
        for {
          unregistered <- rawQuery(selectQuery, Some("application/does-not-exist"))
          inapplicable <- rawQuery(selectQuery, Some("text/turtle"))
        } yield assertTrue(
          unregistered.status.code == 200,
          unregistered.contentType.isDefined,
          inapplicable.status.code == 200,
          inapplicable.contentType.isDefined,
        )
      },
    ),
    suite("store errors are data, not failures")(
      test("a malformed query yields the store's 4xx and its own message, never a 500 from this API") {
        rawQuery("SELECT ?s WHERE { this is not sparql }").map { response =>
          assertTrue(
            response.status.isClientError,
            asString(response).nonEmpty,
            !asString(response).contains("Internal server error"),
          )
        }
      },
      test("a query over the store's timeout is cancelled at the store, and its timeout response is relayed") {
        // The store cancels at its own engine because the timeout travels as its per-request parameter; what arrives is
        // therefore the store's answer, not an abandoned client-side wait. One second, so the case is quick.
        tunedStore(_.copy(timeout = Duration.fromSeconds(1)))
          .flatMap(store => store.rawQuery(RawSparqlRequest(expensiveQuery, None, Map.empty)))
          .map { response =>
            assertTrue(
              response.status.code >= 400,
              asString(response).toLowerCase.contains("timed out"),
            )
          }
      },
    ),
    suite("failures of this API's own are storeless")(
      test("an unreachable store yields the fixed unavailable error, with no host, port or URI in it") {
        // The message is compared for equality: anything appended -- a URI, a port, a cause -- would hand a caller a
        // detail about this deployment's internals, which is exactly what a dedicated error type exists to prevent.
        tunedStore(identity, cfg => cfg.copy(host = "127.0.0.1", fuseki = cfg.fuseki.copy(port = 1)))
          .flatMap(store => store.rawQuery(RawSparqlRequest(selectQuery, None, Map.empty)).exit)
          .map { exit =>
            val failure = exit.causeOption.flatMap(_.failureOption)
            assertTrue(
              failure.exists(_.isInstanceOf[SparqlStoreUnavailableException]),
              failure.map(_.getMessage).contains(SparqlStoreUnavailableException.make.message),
            )
          }
      },
      test("a response over the ceiling fails before a single byte is handed on") {
        tunedStore(_.copy(maxResponseBytes = 16))
          .flatMap(store => store.rawQuery(RawSparqlRequest(selectQuery, None, Map.empty)).exit)
          .map { exit =>
            val failure = exit.causeOption.flatMap(_.failureOption)
            assertTrue(
              failure.exists(_.isInstanceOf[SparqlResponseTooLargeException]),
              // The ceiling is stated so a caller can act on it; the query and the store stay out of the message.
              failure.exists(_.getMessage.contains("16 bytes")),
            )
          }
      },
      test("a store that refuses this API's own credentials yields a scrubbed failure, not the store's 401") {
        // A rotated database password looks exactly like this from the store's side. Relaying its 401 would present a
        // server misconfiguration as the caller's own authentication failure and invite credential-confusion retries,
        // so the failure handed back names neither the store nor its status.
        tunedStore(identity, cfg => cfg.copy(fuseki = cfg.fuseki.copy(password = "not-the-configured-password")))
          .flatMap(store => store.rawQuery(RawSparqlRequest(selectQuery, None, Map.empty)).exit)
          .map { exit =>
            val failure = exit.causeOption.flatMap(_.failureOption)
            assertTrue(
              failure.exists(_.isInstanceOf[SparqlUpstreamRejectedException]),
              failure.map(_.getMessage).contains(SparqlUpstreamRejectedException.make.message),
              // Nothing about the store leaks into what the caller is told.
              failure.forall(e => !e.getMessage.contains("401") && !e.getMessage.toLowerCase.contains("fuseki")),
            )
          }
      },
      test("a response within the ceiling is returned intact") {
        tunedStore(_.copy(maxResponseBytes = 1024 * 1024))
          .flatMap(store => store.rawQuery(RawSparqlRequest("ASK { ?s ?p ?o }", None, Map.empty)))
          .map(response => assertTrue(response.status.code == 200, asString(response).contains("true")))
      },
    ),
    suite("the store's own configuration")(
      test("a federated SERVICE call is refused, so a query cannot make the store issue outbound HTTP") {
        // The precondition the passthrough rests on, asserted against the image this build ships rather than against
        // the documentation: `modules/fuseki/dsp-repo.ttl` sets arq:httpServiceAllowed to false, and the container
        // here runs that file because `just test-it` loads the locally built fuseki image at the pinned tag before
        // the tests run. Without the setting, any SELECT could make the *store* fetch a URL the query author chose --
        // reach the caller does not otherwise have, bypassing the response ceiling and logging almost nothing.
        rawQuery("SELECT * WHERE { SERVICE <http://127.0.0.1:1/sparql> { ?s ?p ?o } }").map { response =>
          assertTrue(
            response.status.code == 422,
            asString(response).contains("SERVICE execution disabled"),
          )
        }
      },
    ),
  )

  private def asString(response: RawSparqlResponse) = new String(response.body, StandardCharsets.UTF_8)

  /** A second store client against the same container, with one guardrail (or the connection details) changed. */
  private def tunedStore(
    tuneGuardrails: SparqlPassthroughConfig => SparqlPassthroughConfig,
    tuneConnection: Triplestore => Triplestore = identity,
  ): ZIO[Triplestore & Scope, Throwable, TriplestoreService] =
    for {
      config <- ZIO.service[Triplestore]
      // Scoped, so the extra client is closed with the test rather than left to the garbage collector.
      backend <- HttpClientZioBackend.scoped()
    } yield TriplestoreServiceLive(
      tuneConnection(config).copy(sparqlPassthrough = tuneGuardrails(config.sparqlPassthrough)),
      backend,
    )
}
