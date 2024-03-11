/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.version

import org.apache.pekko.http.scaladsl.model.headers.Server
import org.apache.pekko.http.scaladsl.server.Directives.respondWithHeader
import org.apache.pekko.http.scaladsl.server.Route

/**
 * This object provides methods that can be used to add the [[Server]] header
 * to an [[pekko.http.scaladsl.model.HttpResponse]].
 */
object ServerVersion {

  def serverVersionHeader: Server = Server(products = s"${BuildInfo.name}/${BuildInfo.version}")

  def addServerHeader(route: Route): Route = respondWithHeader(serverVersionHeader) {
    route
  }
}
