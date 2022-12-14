package org.knora.webapi.routing

import zhttp.html._
import zhttp.http._

/**
 * Provides the '/' endpoint serving a small index page.
 */
object IndexApp {

  def apply(): HttpApp[Any, Nothing] =
    Http.collect[Request] { case Method.GET -> !! => Response.html(Html.fromString(indexPage)) }

  private val indexPage =
    """<html>
      |<title>DSP-API public routes</title>
      |<body>
      |<p>nothing yet</p>
      |</body
      |</html>""".stripMargin

}
