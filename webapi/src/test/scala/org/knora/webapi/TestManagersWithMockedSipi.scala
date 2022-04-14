/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.actor.{Actor, ActorRef, Props}
import org.knora.webapi.app.Managers
import org.knora.webapi.core.LiveActorMaker
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.settings._
import org.knora.webapi.store.cacheservice.impl.CacheServiceInMemImpl
import org.knora.webapi.store.cacheservice.settings.CacheServiceSettings
import org.knora.webapi.store.iiif.impl.MockSipiImpl
import org.knora.webapi.store.cacheservice.CacheServiceManager
import zio.Runtime
import zio.ZIO
import org.knora.webapi.store.iiif.IIIFServiceManager
import zio.RuntimeConfig
import zio.ZEnvironment
import org.knora.webapi.core.Logging
import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.JWTConfig
import org.knora.webapi.app.LiveManagers
import zio.ZLayer

/**
 * Mixin trait for running the application with mocked Sipi
 */
trait TestManagersWithMockedSipi extends LiveManagers {
  this: Actor =>

  /**
   * A combined layer, that allows to build a [[CacheServiceManager]].
   */
  override val cacheServiceManagerLayer: ZLayer[Any, Nothing, CacheServiceManager] =
    ZLayer.make[CacheServiceManager](
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer
    )

  /**
   * A combined layer, that allows to build a [[IIIFServiceManager]].
   */
  override val iiifServiceManagerLayer: ZLayer[Any, Nothing, IIIFServiceManager] =
    ZLayer.make[IIIFServiceManager](
      IIIFServiceManager.layer,
      MockSipiImpl.layer
    )
}
