/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.junit.runner.RunWith
import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import sttp.tapir.PublicEndpoint
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import zio.*
import zio.http.Header
import zio.http.Method as HttpMethod
import zio.http.Request
import zio.http.Status
import zio.http.URL
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.common.api.NegotiatesOwnAccept
import org.knora.webapi.slice.common.api.OwnAcceptNegotiation
import org.knora.webapi.slice.common.api.RequiresMethod

/** Runs tiny endpoints through [[DspApiServer.serverOptions]], the interceptor chain the running server installs. */
@RunWith(classOf[DspZTestJUnitRunner])
class NegotiatesOwnAcceptInterceptorSpec extends ZIOSpecDefault {

  private val html = stringBodyUtf8AnyFormat(Codec.string.format(CodecFormat.TextHtml()))

  private val plain: ZServerEndpoint[Any, Any] =
    endpoint.get.in("plain").out(html).zServerLogic[Any](_ => ZIO.succeed("<p>plain</p>"))

  private val marked: ZServerEndpoint[Any, Any] =
    endpoint.get
      .in("marked")
      .out(html)
      .attribute(NegotiatesOwnAccept.key, OwnAcceptNegotiation)
      .zServerLogic[Any](_ => ZIO.succeed("<p>marked</p>"))

  private val headEndpoint: ZServerEndpoint[Any, Any] =
    RequiresMethod[PublicEndpoint[Unit, Unit, Unit, Any]](endpoint.in("head"), (e, m) => e.method(m)).head
      .zServerLogic[Any](_ => ZIO.unit)

  private def run(method: HttpMethod, path: String, accept: String): ZIO[Any, Throwable, Status] =
    ZIO
      .scoped(for {
        ctxStore <- ZIO.service[ContextStorage]
        app       = ZioHttpInterpreter(DspApiServer.serverOptions(ctxStore)).toHttp(List(plain, marked, headEndpoint))
        response <- app.runZIO(
                      Request(method = method, url = URL.decode(path).toOption.get)
                        .addHeader(Header.Custom("Accept", accept)),
                    )
      } yield response.status)
      .provide(OpenTelemetry.contextZIO)

  val spec: Spec[Any, Any] = suite("an endpoint marked NegotiatesOwnAccept")(
    test("an unmarked endpoint answers 406 to an Accept it does not declare") {
      run(HttpMethod.GET, "/plain", "application/ld+json").map(s => assertTrue(s == Status.NotAcceptable))
    },
    test("a marked endpoint answers 200 to an Accept it does not declare") {
      run(HttpMethod.GET, "/marked", "application/ld+json").map(s => assertTrue(s == Status.Ok))
    },
    test("both answer 200 to the declared Accept") {
      for {
        a <- run(HttpMethod.GET, "/plain", "text/html")
        b <- run(HttpMethod.GET, "/marked", "text/html")
      } yield assertTrue(a == Status.Ok, b == Status.Ok)
    },
    test("an endpoint built with RequiresMethod.head answers a HEAD request") {
      run(HttpMethod.HEAD, "/head", "*/*").map(s => assertTrue(s == Status.Ok))
    },
  ) @@ TestAspect.sequential
}
