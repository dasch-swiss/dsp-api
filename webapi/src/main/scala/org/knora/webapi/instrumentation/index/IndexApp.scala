package org.knora.webapi.instrumentation.index

import zhttp.html._
import zhttp.http._
import zhttp.service.Server

object IndexApp {
  private val indexPage =
    """<html>
      |<title>Simple Server</title>
      |<body>
      |<p><a href="/metrics">Prometheus Metrics</a></p>
      |</body
      |</html>""".stripMargin

  private val static =
    Http.collect[Request] { case Method.GET -> !! => Response.html(Html.fromString(indexPage)) }

  val make = Server.app(static)
}
