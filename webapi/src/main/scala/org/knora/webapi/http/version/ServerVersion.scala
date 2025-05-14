/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.version

import org.apache.pekko

import pekko.http.scaladsl.model.headers.Server
import pekko.http.scaladsl.server.Directives.respondWithHeader
import pekko.http.scaladsl.server.Route

/**
 * This object provides methods that can be used to add the [[Server]] header
 * to an [[pekko.http.scaladsl.model.HttpResponse]].
 */
object ServerVersion {

  private val ApiNameAndVersion = s"${BuildInfo.name}/${BuildInfo.version}"

  def serverVersionHeader: Server = Server(products = ApiNameAndVersion)

  def addServerHeader(route: Route): Route = respondWithHeader(serverVersionHeader) {
    route
  }
}
