/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api

import zio.Task
import zio.ZIO

import java.io.ByteArrayOutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

import org.knora.webapi.slice.`export`.domain.ProjectDataImportService

/**
 * What a raw socket read back after a request head was sent: the response text, and whether the server then hung up.
 * `serverClosed` is `false` when the read ran out its own timeout instead of reaching EOF, so a server that answered
 * but kept the connection open fails as a readable assertion rather than as an exception.
 */
final case class RawExchange(text: String, serverClosed: Boolean) {

  def statusLine: String = text.linesIterator.nextOption().getOrElse("").trim

  def header(name: String): Option[String] =
    text.linesIterator
      .drop(1)
      .takeWhile(_.trim.nonEmpty)
      .collectFirst {
        case line if line.toLowerCase.startsWith(s"${name.toLowerCase}:") => line.split(":", 2)(1).trim
      }

  /** The text after the blank line that ends the response head. */
  def body: String = text.split("\r\n\r\n", 2).lift(1).getOrElse("")
}

/** A declared length one byte above the bulk data import limit. */
object DataImportLimit {
  val overLimitBytes: Long = ProjectDataImportService.maxPayloadBytes + 1
}

object RawHeadOnlyPost {

  /** Long enough that a slow answer is not mistaken for a connection left open, short enough to fail a suite fast. */
  private val rawReadTimeoutMillis = 10_000

  /**
   * Sends only the head of a `POST` declaring `contentLength` bytes of `contentType`, then reads the connection to EOF
   * without ever sending that body.
   *
   * Hand-rolled and blocking on purpose. An HTTP client owns the decision of when to write a request body relative to
   * reading the response, and for the routes that answer before the body arrives that decision is the thing under test:
   * nothing else can hold the body back to show it.
   *
   * @param bearer the JWT to send, or `None` for a request without credentials.
   */
  def apply(
    host: String,
    port: Int,
    path: String,
    contentType: String,
    contentLength: Long,
    bearer: Option[String],
  ): Task[RawExchange] = {
    val head =
      s"POST $path HTTP/1.1\r\n" +
        s"Host: $host:$port\r\n" +
        bearer.fold("")(token => s"Authorization: Bearer $token\r\n") +
        s"Content-Type: $contentType\r\n" +
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
}
