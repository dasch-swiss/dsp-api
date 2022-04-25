/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.settings

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
