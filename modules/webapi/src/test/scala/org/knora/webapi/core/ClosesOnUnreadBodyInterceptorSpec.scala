/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.junit.runner.RunWith
import sttp.model.StatusCode as SttpStatusCode
import sttp.tapir.ztapir.*
import zio.*
import zio.http.Header
import zio.http.Method as HttpMethod
import zio.http.Request
import zio.http.URL
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.api.v3.projects.ClosesOnUnreadBody
import org.knora.webapi.slice.api.v3.projects.ClosesOnUnreadBodyRoute

/** Runs tiny endpoints through [[DspApiServer.serverOptions]], the interceptor chain the running server installs. */
@RunWith(classOf[DspZTestJUnitRunner])
class ClosesOnUnreadBodyInterceptorSpec extends ZIOSpecDefault {

  private def rejecting(path: String, marked: Boolean, status: SttpStatusCode): ZServerEndpoint[Any, Any] = {
    val base = endpoint.post.in(path).out(statusCode).out(stringBody)
    (if (marked) base.attribute(ClosesOnUnreadBody.key, ClosesOnUnreadBodyRoute) else base)
      .zServerLogic[Any](_ => ZIO.succeed((status, "x")))
  }

  private val endpoints = List(
    rejecting("marked-rejects", marked = true, SttpStatusCode.Conflict),
    rejecting("marked-accepts", marked = true, SttpStatusCode.Accepted),
    rejecting("unmarked-rejects", marked = false, SttpStatusCode.Conflict),
  )

  private val aboveThreshold = DspApiServer.maxAggregatedRequestBodySize + 1L

  private def connectionHeader(path: String, declaredLength: Long): ZIO[Any, Throwable, Option[String]] =
    ZIO
      .scoped(for {
        ctxStore <- ZIO.service[ContextStorage]
        app       = sttp.tapir.server.ziohttp.ZioHttpInterpreter(DspApiServer.serverOptions(ctxStore)).toHttp(endpoints)
        response <- app.runZIO(
                      Request(method = HttpMethod.POST, url = URL.decode(path).toOption.get)
                        .addHeader(Header.ContentLength(declaredLength)),
                    )
      } yield response.rawHeader("Connection"))
      .provide(OpenTelemetry.contextZIO)

  val spec: Spec[Any, Any] = suite("a ClosesOnUnreadBody route")(
    test("a rejection with a declared length above the aggregation threshold closes the connection") {
      connectionHeader("/marked-rejects", aboveThreshold).map(h => assertTrue(h.contains("close")))
    },
    test("a rejection with a declared length at the aggregation threshold keeps the connection") {
      connectionHeader("/marked-rejects", DspApiServer.maxAggregatedRequestBodySize.toLong)
        .map(h => assertTrue(h.isEmpty))
    },
    test("a success keeps the connection") {
      connectionHeader("/marked-accepts", aboveThreshold).map(h => assertTrue(h.isEmpty))
    },
    test("an unmarked route keeps the connection on a rejection") {
      connectionHeader("/unmarked-rejects", aboveThreshold).map(h => assertTrue(h.isEmpty))
    },
  ) @@ TestAspect.sequential
}
