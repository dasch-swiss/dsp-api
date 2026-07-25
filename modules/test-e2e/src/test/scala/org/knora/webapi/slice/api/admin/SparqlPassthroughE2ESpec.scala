/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import org.junit.runner.RunWith
import sttp.client4.UriContext
import sttp.model.HeaderNames
import sttp.model.StatusCode
import zio.ZIO
import zio.json.ast.Json
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.testservices.TestApiClient

/**
 * Wire-level behaviour of `POST /admin/sparql/query`.
 *
 * These cases have to run over HTTP rather than against the endpoint in memory, because what most of them assert is a
 * property of encoding and decoding: that a relayed `Content-Type` survives, that a form-encoded body is accepted, that
 * no `WWW-Authenticate: Basic` challenge is emitted, and that an over-cap request body leaves the connection usable.
 *
 * The route exists here only because `modules/test-e2e/src/test/resources/application.conf` enables
 * `allow-sparql-passthrough`; without that every case below would see a `404`.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class SparqlPassthroughE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject("test_data/project_data/anything-data.ttl", "http://www.knora.org/data/0001/anything"),
  )

  private val endpoint = uri"/admin/sparql/query"

  private val selectQuery = "SELECT ?s WHERE { ?s ?p ?o } LIMIT 1"

  /** Comfortably past both the request cap and the 1 MiB point at which the server stops aggregating bodies. */
  private val oversized = "# " + ("x" * (1024 * 1024 + 1024)) + "\n" + selectQuery

  val e2eSpec: Spec[env, Any] = suite("POST /admin/sparql/query")(
    suite("authentication and authorization")(
      test("returns 401 when no credentials are provided") {
        TestApiClient
          .postSparql(endpoint, selectQuery)
          .map(response => assertTrue(response.code == StatusCode.Unauthorized))
      },
      test("returns 403 when the authenticated user is not a SystemAdmin") {
        TestApiClient
          .postSparql(endpoint, selectQuery, normalUser)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
      test("returns 200 for a SystemAdmin") {
        TestApiClient
          .postSparql(endpoint, selectQuery, rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("rejects HTTP basic credentials that work on other admin routes") {
        // Bearer-only is a tested invariant, not a convention: basic would run a bcrypt verification upstream of every
        // bound this surface establishes, since security logic runs before server logic.
        for {
          onThisRoute    <- TestApiClient.postSparql(endpoint, selectQuery, None, _.auth.basic(rootUser.email, "test"))
          onAnotherRoute <- TestApiClient.getJson[Json](uri"/admin/projects", _.auth.basic(rootUser.email, "test"))
        } yield assertTrue(
          onThisRoute.code == StatusCode.Unauthorized,
          // Proves the credentials themselves are good, so the 401 above is about the mechanism, not the password.
          onAnotherRoute.code == StatusCode.Ok,
        )
      },
      test("rejects a session cookie, so the route cannot be ridden from another origin") {
        // CORS here is reflected-origin with credentials allowed, so a cookie credential on this route would make it
        // CSRF-able from anywhere. The absence of a cookie security input is what prevents that.
        for {
          token      <- TestApiClient.getRootToken
          cookieName <- ZIO.serviceWith[Authenticator](_.calculateCookieName())
          response   <-
            TestApiClient.postSparql(endpoint, selectQuery, None, _.header(HeaderNames.Cookie, s"$cookieName=$token"))
        } yield assertTrue(response.code == StatusCode.Unauthorized)
      },
      test("does not offer a Basic challenge on an unauthenticated request") {
        TestApiClient
          .postSparql(endpoint, selectQuery)
          .map { response =>
            val challenges =
              response.headers.filter(_.name.equalsIgnoreCase(HeaderNames.WwwAuthenticate)).map(_.value.toLowerCase)
            assertTrue(
              response.code == StatusCode.Unauthorized,
              !challenges.exists(_.contains("basic")),
            )
          }
      },
    ),
    suite("request forms")(
      test("accepts the query as an application/sparql-query body") {
        TestApiClient
          .postSparql(endpoint, selectQuery, rootUser)
          .map(response => assertTrue(response.code == StatusCode.Ok))
      },
      test("accepts the query as a form-encoded query field, which off-the-shelf clients default to") {
        TestApiClient
          .postForm(endpoint, Seq("query" -> selectQuery), rootUser)
          .map(response =>
            assertTrue(
              response.code == StatusCode.Ok,
              response.body.merge.contains("\"s\""),
            ),
          )
      },
      test("rejects an unsupported request Content-Type with 415") {
        // Neither request-body variant can claim text/plain, so the framework rejects it before the endpoint runs.
        // The same happens for a request carrying no Content-Type at all.
        TestApiClient
          .postBodyAs(endpoint, selectQuery, "text/plain", rootUser)
          .map(response => assertTrue(response.code == StatusCode.UnsupportedMediaType))
      },
      test("rejects the statement in the query string with 400, rather than leaking it into logs unnoticed") {
        TestApiClient
          .postSparql(endpoint.addParam("query", selectQuery), selectQuery, rootUser)
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
    ),
    suite("verbatim relay")(
      test("relays the store's default serialization when the request carries no Accept header") {
        // REQ-1.3. Fuseki's default for a SELECT is SPARQL-Results XML, and that is what must arrive -- notably not
        // the body output's own codec media type, which is a wildcard and would be illegal to emit.
        TestApiClient
          .postSparql(endpoint, selectQuery, rootUser)
          .map { response =>
            val contentType = response.header(HeaderNames.ContentType).getOrElse("")
            assertTrue(
              response.code == StatusCode.Ok,
              contentType.startsWith("application/sparql-results+xml"),
              !contentType.contains("octet-stream"),
              !contentType.contains("*/*"),
            )
          }
      },
      test("relays a Content-Type that differs from the store's default when one is requested") {
        // Together with the case above this is what proves the header is genuinely relayed rather than fixed: the same
        // endpoint returns two different Content-Types depending only on what the store negotiated.
        for {
          jwt      <- ZIO.serviceWithZIO[TestApiClient](_.jwtFor(rootUser))
          response <- TestApiClient.postSparql(
                        endpoint,
                        selectQuery,
                        None,
                        r => r.auth.bearer(jwt).header(HeaderNames.Accept, "application/sparql-results+json"),
                      )
        } yield assertTrue(
          response.code == StatusCode.Ok,
          response.header(HeaderNames.ContentType).exists(_.startsWith("application/sparql-results+json")),
          response.body.merge.contains("\"bindings\""),
        )
      },
      test("honours an Accept header of text/csv for a SELECT") {
        for {
          jwt      <- ZIO.serviceWithZIO[TestApiClient](_.jwtFor(rootUser))
          response <- TestApiClient.postSparql(
                        endpoint,
                        selectQuery,
                        None,
                        r => r.auth.bearer(jwt).header(HeaderNames.Accept, "text/csv"),
                      )
        } yield assertTrue(
          response.code == StatusCode.Ok,
          response.header(HeaderNames.ContentType).exists(_.startsWith("text/csv")),
          response.body.merge.linesIterator.next().trim == "s",
        )
      },
      test("returns Turtle for a CONSTRUCT when asked for it") {
        for {
          jwt      <- ZIO.serviceWithZIO[TestApiClient](_.jwtFor(rootUser))
          response <- TestApiClient.postSparql(
                        endpoint,
                        "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 1",
                        None,
                        r => r.auth.bearer(jwt).header(HeaderNames.Accept, "text/turtle"),
                      )
        } yield assertTrue(
          response.code == StatusCode.Ok,
          response.header(HeaderNames.ContentType).exists(_.startsWith("text/turtle")),
        )
      },
      test("relays the store's own error for a malformed query, not this API's error envelope") {
        // The regression this exists for: routing the passthrough through the standard response handling would turn a
        // user's bad SPARQL into a dsp-api 500 with {"message":"Internal server error"}.
        TestApiClient
          .postSparql(endpoint, "SELECT ?s WHERE { this is not sparql }", rootUser)
          .map { response =>
            val body = response.body.merge
            assertTrue(
              response.code.isClientError,
              !body.contains("Internal server error"),
              body.nonEmpty,
            )
          }
      },
    ),
    suite("request-body cap")(
      test("rejects an over-cap body with 413 and leaves the connection usable for the next request") {
        // The body must exceed 1 MiB: at or below that size the server aggregates it, and the case would prove nothing
        // about the streamed path. The framework refuses this while reading, so it never buffers the whole body, and
        // answers with Connection: close because the unread remainder makes the connection unusable.
        for {
          rejected <- TestApiClient.postSparql(endpoint, oversized, rootUser)
          // Same client, hence the same connection pool: if the rejected request had left a connection in the pool in
          // an unreadable state, this second request would hang until its timeout instead of answering.
          following <- TestApiClient.postSparql(endpoint, selectQuery, rootUser)
        } yield assertTrue(
          rejected.code == StatusCode.PayloadTooLarge,
          following.code == StatusCode.Ok,
        )
      },
      test("refuses an over-cap body from a non-SystemAdmin with 403, before any of it is read") {
        // The regression this exists for: with the authorization check in the server logic, the framework had already
        // materialised the body by the time the caller was told 403 -- so any holder of a valid token could make the
        // server buffer an arbitrary request. A 403 here rather than a 413 is what shows the check now runs first,
        // since the body cap is only consulted during decoding, which the rejection precedes.
        for {
          rejected  <- TestApiClient.postSparql(endpoint, oversized, normalUser)
          following <- TestApiClient.postSparql(endpoint, selectQuery, rootUser)
        } yield assertTrue(
          rejected.code == StatusCode.Forbidden,
          following.code == StatusCode.Ok,
        )
      },
    ),
  )
}
