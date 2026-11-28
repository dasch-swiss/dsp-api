/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.junit.runner.RunWith
import sttp.capabilities.zio.ZioStreams
import sttp.model.StatusCode as SttpStatusCode
import sttp.tapir.CodecFormat
import sttp.tapir.Schema
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import zio.*
import zio.http.Server
import zio.http.Server.RequestStreaming
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.test.*

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Socket
import java.nio.charset.StandardCharsets

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.common.api.ClosesOnUnreadBody
import org.knora.webapi.slice.common.api.ClosesOnUnreadBodyRoute

/**
 * A real server, configured like the running one, answers an error while a large upload is still streaming in. The
 * client must get that answer with `Connection: close`, and the server must end the connection, within the deadline.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class EarlyErrorStreamingSpec extends ZIOSpecDefault {

  private def readsOneChunkThen(path: String, fails: IO[(SttpStatusCode, String), Unit]) =
    endpoint.post
      .in(path)
      .in(streamBody(ZioStreams)(Schema.binary, CodecFormat.OctetStream()))
      .out(stringBody)
      .errorOut(statusCode.and(stringBody))
      .attribute(ClosesOnUnreadBody.key, ClosesOnUnreadBodyRoute)
      .zServerLogic[Any](body => body.runHead.orDie *> fails.as("unreachable"))

  private val endpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    readsOneChunkThen("typed-error", ZIO.fail((SttpStatusCode.InternalServerError, "failed"))),
    readsOneChunkThen("defect", ZIO.die(new RuntimeException("died"))),
  )

  private val serverLayer: ZLayer[Any, Throwable, Server] = Server.defaultWith(
    _.binding("127.0.0.1", 0).requestStreaming(RequestStreaming.Hybrid(DspApiServer.maxAggregatedRequestBodySize)),
  )

  private val bodySize: Long = 20L * 1024 * 1024
  private val deadline       = 10.seconds

  /** The response head, if it arrived in time, and whether the server then ended the connection in time. */
  private final case class Answer(head: Option[(String, Map[String, String])], closedByServer: Boolean) {
    def status: Option[String]     = head.map(_._1)
    def connection: Option[String] = head.flatMap(_._2.get("connection"))
  }

  private def readHead(in: InputStream): (String, Map[String, String]) = {
    val buf  = new ByteArrayOutputStream()
    var last = List.empty[Int]
    while (last != List('\n', '\r', '\n', '\r')) {
      val b = in.read()
      if (b < 0) throw new java.io.EOFException("connection closed before the response head")
      buf.write(b)
      last = (b :: last).take(4)
    }
    val lines   = new String(buf.toByteArray, StandardCharsets.ISO_8859_1).split("\r\n").toList
    val headers = lines.tail.flatMap { l =>
      l.split(":", 2) match {
        case Array(k, v) => Some(k.trim.toLowerCase -> v.trim)
        case _           => None
      }
    }.toMap
    (lines.head, headers)
  }

  private def drainToEof(in: InputStream): Unit = {
    val chunk = new Array[Byte](64 * 1024)
    while (in.read(chunk) >= 0) ()
  }

  // A reset while draining counts as closed: the server ended the connection either way.
  private def upload(path: String): ZIO[Server & ContextStorage, Throwable, Answer] =
    for {
      ctxStore <- ZIO.service[ContextStorage]
      port     <- Server.install(ZioHttpInterpreter(DspApiServer.serverOptions(ctxStore)).toHttp(endpoints))
      answer   <- ZIO.scoped {
                  for {
                    socket <- ZIO.fromAutoCloseable(ZIO.attemptBlocking(new Socket("127.0.0.1", port)))
                    out     = socket.getOutputStream
                    _      <- ZIO.attemptBlocking {
                           out.write(
                             s"POST /$path HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/octet-stream\r\nContent-Length: $bodySize\r\n\r\n"
                               .getBytes(StandardCharsets.ISO_8859_1),
                           )
                           out.flush()
                         }
                    // The client keeps sending until the server stops it, as an upload client does.
                    _ <- ZIO.attemptBlockingCancelable {
                           val chunk = new Array[Byte](64 * 1024)
                           var sent  = 0L
                           while (sent < bodySize) { out.write(chunk); sent += chunk.length }
                           out.flush()
                         }(ZIO.succeed(socket.close())).ignore.forkScoped
                    in      = socket.getInputStream
                    head   <- ZIO.attemptBlockingCancelable(readHead(in))(ZIO.succeed(socket.close())).timeout(deadline)
                    closed <- ZIO
                                .attemptBlockingCancelable(drainToEof(in))(ZIO.succeed(socket.close()))
                                .timeout(deadline)
                                .map(_.isDefined)
                                .catchAll(_ => ZIO.succeed(true))
                                .when(head.isDefined)
                  } yield Answer(head, closed.contains(true))
                }
    } yield answer

  val spec: Spec[Any, Any] = suite("an error answered while a large upload still streams in")(
    test("from a typed failure (the endpoint's error output) reaches the client at once with Connection: close") {
      upload("typed-error").map(a =>
        assertTrue(a.status.exists(_.contains(" 500 ")), a.connection.contains("close"), a.closedByServer),
      )
    },
    test("from a defect (the exception handler) reaches the client at once with Connection: close") {
      upload("defect").map(a =>
        assertTrue(a.status.exists(_.contains(" 500 ")), a.connection.contains("close"), a.closedByServer),
      )
    },
  ).provide(serverLayer, OpenTelemetry.contextZIO) @@ TestAspect.withLiveClock @@ TestAspect.sequential @@ TestAspect
    .timeout(60.seconds)
}
