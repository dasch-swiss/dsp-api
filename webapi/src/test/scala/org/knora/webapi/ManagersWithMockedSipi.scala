/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.actor.{Actor, ActorRef, Props}
import org.knora.webapi.app.Managers
import org.knora.webapi.core.LiveActorMaker
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.responders.MockableResponderManager
import org.knora.webapi.settings._
import org.knora.webapi.store.MockableStoreManager
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
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.iiif.config.IIIFServiceConfig
import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.JWTConfig

/**
 * Mixin trait for running the application with mocked Sipi
 */
trait ManagersWithMockedSipi extends Managers {
  this: Actor =>

  lazy val mockResponders: Map[String, ActorRef] = Map.empty[String, ActorRef]

  lazy val cacheServiceManager: CacheServiceManager = Runtime.default
    .unsafeRun(
      ZIO
        .service[CacheServiceManager]
        .provide(
          CacheServiceInMemImpl.layer,
          CacheServiceManager.layer
        )
    )

  lazy val iiifServiceManager: IIIFServiceManager =
    Runtime(ZEnvironment.empty, RuntimeConfig.default @@ Logging.live)
      .unsafeRun(
        ZIO
          .service[IIIFServiceManager]
          .provide(
            IIIFServiceManager.layer,
            MockSipiImpl.layer
          )
      )

  lazy val storeManager: ActorRef = context.actorOf(
    Props(
      new MockableStoreManager(appActor = self, iiifsm = iiifServiceManager, csm = cacheServiceManager)
        with LiveActorMaker
    ),
    name = StoreManagerActorName
  )

  lazy val responderManager: ActorRef = context.actorOf(
    Props(
      new MockableResponderManager(
        mockRespondersOrStoreConnectors = mockResponders,
        appActor = self,
        responderData = ResponderData(
          system,
          self,
          knoraSettings = KnoraSettings(system),
          cacheServiceSettings = new CacheServiceSettings(system.settings.config)
        )
      )
    ),
    name = RESPONDER_MANAGER_ACTOR_NAME
  )
}
