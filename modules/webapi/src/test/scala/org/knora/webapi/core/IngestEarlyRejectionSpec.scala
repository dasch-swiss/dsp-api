/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.junit.runner.RunWith
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import zio.*
import zio.http.*
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.ingest.api.IngestAuthenticator
import org.knora.webapi.slice.ingest.api.IngestBaseEndpoints
import org.knora.webapi.slice.ingest.api.IngestProjectsEndpoints
import org.knora.webapi.slice.ingest.testutil.SpecConfigurations

/** An ingest upload refused before its streamed body is read must close the connection, through the server's hooks. */
@RunWith(classOf[DspZTestJUnitRunner])
class IngestEarlyRejectionSpec extends ZIOSpecDefault {

  private val routes: URIO[IngestBaseEndpoints & ContextStorage, Routes[Any, Response]] =
    for {
      base                                    <- ZIO.service[IngestBaseEndpoints]
      ctxStore                                <- ZIO.service[ContextStorage]
      upload: ZServerEndpoint[Any, ZioStreams] =
        IngestProjectsEndpoints(base).postAssetIngest.serverLogic(_ => _ => ZIO.never)
    } yield ZioHttpInterpreter(DspApiServer.serverOptions(ctxStore)).toHttp(List(upload))

  private def upload(declaredLength: Long, token: Option[String]) = {
    val request = Request
      .post(URL.decode("/ingest/projects/0001/assets/ingest/file.txt").toOption.get, Body.fromString("abc"))
      .addHeader(Header.ContentLength(declaredLength))
      .addHeader(Header.ContentType(MediaType.application.`octet-stream`))
    ZIO.scoped(routes.flatMap(_.runZIO(token.fold(request)(t => request.addHeader(Header.Authorization.Bearer(t))))))
  }

  private def connection(response: Response) =
    response.headers.collectFirst { case h if h.headerName.equalsIgnoreCase("Connection") => h.renderedValue }

  private val aboveThreshold = DspApiServer.maxAggregatedRequestBodySize.toLong + 1

  val spec = suite("an ingest upload rejected by its security logic")(
    test("closes the connection when the declared body is above the aggregation threshold") {
      for {
        anonymous <- upload(aboveThreshold, None)
        invalid   <- upload(aboveThreshold, Some("invalid"))
      } yield assertTrue(
        anonymous.status == Status.Unauthorized,
        connection(anonymous).contains("close"),
        invalid.status == Status.Unauthorized,
        connection(invalid).contains("close"),
      )
    },
    test("keeps the connection when the body was small enough to be read") {
      upload(3, None).map(response => assertTrue(response.status == Status.Unauthorized, connection(response).isEmpty))
    },
  ).provide(
    IngestAuthenticator.layer,
    IngestBaseEndpoints.layer,
    OpenTelemetry.contextZIO,
    SpecConfigurations.jwtConfigLayer,
  )
}
