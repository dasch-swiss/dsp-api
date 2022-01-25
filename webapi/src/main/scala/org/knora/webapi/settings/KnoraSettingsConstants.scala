/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.settings

/**
 * 'SettingsConstants' contains constants of strings, we would generally expect to find in
 * the 'application.conf' file, which can be accessed by the application 'Settings'
 */
object TriplestoreTypes {

  val EmbeddedJenaTdb = "embedded-jena-tdb"
  val EmbeddedGraphDBSE = "embedded-jena-graphdb"
  val HttpGraphDBSE = "graphdb-se"
  val HttpGraphDBFree = "graphdb-free"
  val HttpFuseki = "fuseki"
}

object KnoraDispatchers {

  /**
   * All normal actors should run on this dispatcher (non-blocking)
   */
  val KnoraActorDispatcher = "knora-actor-dispatcher"

  /**
   * All blocking operations should run on this dispatcher (blocking)
   */
  val KnoraBlockingDispatcher = "knora-blocking-dispatcher"
}
