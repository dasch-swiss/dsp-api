/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import org.junit.runner.RunWith
import sttp.client4.UriContext
import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.model.StatusCode
import zio.Task
import zio.ZIO
import zio.json.ast.Json
import zio.test.*

import java.io.ByteArrayOutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.config.KnoraApi
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

  /** The same path as [[endpoint]], spelled out because the raw request below is assembled as text. */
  private val endpointPath = "/admin/sparql/query"

  private val selectQuery = "SELECT ?s WHERE { ?s ?p ?o } LIMIT 1"

  /** Comfortably past both the request cap and the 1 MiB point at which the server stops aggregating bodies. */
  private val oversized = "# " + ("x" * (1024 * 1024 + 1024)) + "\n" + selectQuery

  /**
   * What a raw socket read back after a request head was sent: the response text, and whether the server then hung
   * up. `serverClosed` is `false` when the read ran out its own timeout instead of reaching EOF, so a server that
   * answered but kept the connection open fails as a readable assertion rather than as an exception.
   */
  private final case class RawExchange(text: String, serverClosed: Boolean) {

    def statusLine: String = text.linesIterator.nextOption().getOrElse("").trim

    def header(name: String): Option[String] =
      text.linesIterator
        .drop(1)
        .takeWhile(_.trim.nonEmpty)
        .collectFirst {
          case line if line.toLowerCase.startsWith(s"${name.toLowerCase}:") => line.split(":", 2)(1).trim
        }
  }

  /** Long enough that a slow answer is not mistaken for a connection left open, short enough to fail a suite fast. */
  private val rawReadTimeoutMillis = 10_000

  /**
   * Sends only the head of a `POST` declaring `contentLength` bytes of `application/sparql-query`, then reads the
   * connection to EOF without ever sending that body.
   *
   * Hand-rolled and blocking on purpose. An HTTP client owns the decision of when to write a request body relative
   * to reading the response, and on this route that decision is the thing under test: the server answers before the
   * body arrives, and nothing else here can hold the body back to show it.
   */
  private def headOnlyPost(host: String, port: Int, contentLength: Int, bearer: String): Task[RawExchange] = {
    val head =
      s"POST $endpointPath HTTP/1.1\r\n" +
        s"Host: $host:$port\r\n" +
        s"Authorization: Bearer $bearer\r\n" +
        "Content-Type: application/sparql-query\r\n" +
        s"Content-Length: $contentLength\r\n" +
        "\r\n"
    ZIO.scoped {
      ZIO
        .acquireRelease(ZIO.attemptBlocking(new Socket(connectHost(host), port)))(s => ZIO.attempt(s.close()).ignore)
        .flatMap { socket =>
          ZIO.attemptBlocking {
            socket.setSoTimeout(rawReadTimeoutMillis)
            val out = socket.getOutputStream
            out.write(head.getBytes(StandardCharsets.US_ASCII))
            out.flush()
            val in         = socket.getInputStream
            val received   = new ByteArrayOutputStream()
            val chunk      = new Array[Byte](4096)
            var reachedEof = false
            try {
              var read = in.read(chunk)
              while (read >= 0) {
                received.write(chunk, 0, read)
                read = in.read(chunk)
              }
              reachedEof = true
            } catch { case _: SocketTimeoutException => () }
            RawExchange(received.toString(StandardCharsets.UTF_8), reachedEof)
          }
        }
    }
  }

  /** `0.0.0.0` is the configured *bind* address; as a connect target loopback says the same thing unambiguously. */
  private def connectHost(host: String): String = if (host == "0.0.0.0") "127.0.0.1" else host

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
          .postString(endpoint, selectQuery, MediaType.TextPlain, rootUser)
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
        //
        // Driven over a raw socket rather than the HTTP client, and that is what makes it deterministic. Sending the
        // whole body and leaving the interleaving to the client is a race: the server answers early, marks the
        // response as the last on its connection and closes while the client is still writing, and the JDK client
        // loses the response to the reset often enough to fail about one run in five. That race is real for
        // production callers too -- see the endpoint documentation's known limitations -- but it is not what this
        // case is about. Here the body is never sent at all: only the head, declaring an over-cap `Content-Length`.
        // That removes the write a reset could land on, and proves the claim more directly than the old spelling
        // did, since a 403 arriving with zero body bytes on the wire cannot have been produced after reading one.
        for {
          api      <- ZIO.service[KnoraApi]
          jwt      <- ZIO.serviceWithZIO[TestApiClient](_.jwtFor(normalUser))
          rejected <- headOnlyPost(api.externalHost, api.externalPort, oversized.length, jwt)
        } yield assertTrue(
          rejected.statusLine == "HTTP/1.1 403 Forbidden",
          rejected.header(HeaderNames.Connection).exists(_.equalsIgnoreCase("close")),
          // Reading to EOF rather than to a declared length is the other half of what that header announces, and
          // the only thing that makes sending it worth anything.
          rejected.serverClosed,
        )
      },
    ),
  )
}
