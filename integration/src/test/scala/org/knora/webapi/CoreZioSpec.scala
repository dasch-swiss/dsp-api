/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio._
import zio.test._
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.core.MessageRelayLive

abstract class CoreZioSpec() extends ZIOSpec[MessageRelay] {

  override def bootstrap: ZLayer[Any, Any, MessageRelay] = MessageRelayLive.layer
  def spec: Spec[TestEnvironment with MessageRelay, Any]
}
