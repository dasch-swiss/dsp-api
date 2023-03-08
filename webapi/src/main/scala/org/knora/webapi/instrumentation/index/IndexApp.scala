/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.instrumentation.index

import zio._
import zio.http._
import zio.http.html.Html
import zio.http.model._

/**
 * Provides the '/' endpoint serving a small index page.
 */
final case class IndexApp() {

  val route: HttpApp[Any, Nothing] =
    Http.collect[Request] { case Method.GET -> !! => Response.html(Html.fromString(indexPage)) }

  private val indexPage =
    """<html>
      |<title>Simple Server</title>
      |<body>
      |<p><a href="/metrics">Prometheus Metrics</a></p>
      |</body
      |</html>""".stripMargin
}
object IndexApp {
  val layer: ULayer[IndexApp] =
    ZLayer.succeed(IndexApp())
}
