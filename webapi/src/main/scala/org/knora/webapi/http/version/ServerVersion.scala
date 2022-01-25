/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.version

import akka.http.scaladsl.model.headers.Server
import akka.http.scaladsl.server.Directives.respondWithHeader
import akka.http.scaladsl.server.Route
import org.knora.webapi.http.version.versioninfo.VersionInfo

/**
 * This object provides methods that can be used to add the [[Server]] header
 * to an [[akka.http.scaladsl.model.HttpResponse]].
 */
object ServerVersion {

  private val ApiNameAndVersion = s"${VersionInfo.name}/${VersionInfo.webapiVersion}"
  private val AkkaNameAndVersion = s"akka-http/${VersionInfo.akkaHttpVersion}"
  private val AllProducts = ApiNameAndVersion + " " + AkkaNameAndVersion

  def serverVersionHeader: Server = Server(products = AllProducts)

  def addServerHeader(route: Route): Route = respondWithHeader(serverVersionHeader) {
    route
  }
}
