/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko
import zio.*

object ActorSystemTest {

  def layer(sys: pekko.actor.ActorSystem): ZLayer[Any, Nothing, ActorSystem] =
    ZLayer.scoped(ZIO.succeed(new ActorSystem { override val system: pekko.actor.ActorSystem = sys }))
}
