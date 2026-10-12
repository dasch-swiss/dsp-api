/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.infrastructure

import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import zio.*
import zio.cache.Cache
import zio.cache.Lookup
import zio.telemetry.opentelemetry.tracing.Tracing

import scala.concurrent.duration.FiniteDuration

import org.knora.webapi.config.FairConfig
import org.knora.webapi.slice.infrastructure.TracingHttpClient

/** dsp-app's index.html, used as the page shell. */
trait AppShell {

  /** dsp-app's index.html, or None when it is unavailable. Never fails. */
  def get: UIO[Option[String]]
}

final class AppShellLive private (cache: Cache[Unit, Nothing, Option[String]]) extends AppShell {
  override def get: UIO[Option[String]] = cache.get(())
}

object AppShellLive {

  private[infrastructure] val okTtl: Duration             = 10.seconds
  private[infrastructure] val failTtl: Duration           = 2.seconds
  private[infrastructure] val readTimeout: FiniteDuration = FiniteDuration(500, "millis")
  private[infrastructure] val fetchTimeout: Duration      = 1.second
  private[infrastructure] val maxLength: Int              = 1024 * 1024

  /**
   * Builds the single-entry cache around `fetch`: a success is kept for [[okTtl]], an unavailable shell for [[failTtl]].
   */
  private[infrastructure] def cached(fetch: UIO[Option[String]]): UIO[AppShell] =
    Cache
      .makeWith[Unit, Any, Nothing, Option[String]](1, Lookup((_: Unit) => fetch)) {
        case Exit.Success(Some(_)) => okTtl
        case _                     => failTtl
      }
      .map(new AppShellLive(_))

  /** Only a 200 response of at most 1 MiB whose body contains `</head>` is a usable shell. */
  private[infrastructure] def accept(status: Int, body: String): Option[String] =
    Option.when(status == 200 && body.length <= maxLength && body.contains("</head>"))(body)

  private def fetch(config: FairConfig, backend: StreamBackend[Task, ZioStreams]): UIO[Option[String]] =
    basicRequest
      .get(uri"${config.appShellUrl}")
      .followRedirects(false)
      .header("Accept-Encoding", "identity")
      .readTimeout(readTimeout)
      .response(asString)
      .send(backend)
      .map(response => accept(response.code.code, response.body.fold(identity, identity)))
      .timeout(fetchTimeout)
      .map(_.flatten)
      .tap(shell => ZIO.logWarning("The app shell is unavailable").when(shell.isEmpty))
      .catchAll(e => ZIO.logWarning(s"Could not fetch the app shell: ${e.getMessage}").as(None))

  val layer: URLayer[FairConfig & Tracing, AppShell] =
    TracingHttpClient.layer(readTimeout) >>> ZLayer.fromZIO {
      for {
        config  <- ZIO.service[FairConfig]
        backend <- ZIO.service[StreamBackend[Task, ZioStreams]]
        shell   <- cached(fetch(config, backend))
      } yield shell
    }
}
