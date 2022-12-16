/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import zhttp.html._
import zhttp.http._

import org.knora.webapi.http.version.BuildInfo

/**
 * Provides the '/' endpoint serving a small index page.
 */
object IndexApp {

  def apply(): HttpApp[Any, Nothing] =
    Http.collect[Request] { case Method.GET -> !! => Response.html(Html.fromString(indexPage)) }

  private val indexPage =
    s"""<html>
       |<title>dsp-api</title>
       |<body>
       |<p>version: ${BuildInfo.version}</p>
       |</body
       |</html>""".stripMargin

}
