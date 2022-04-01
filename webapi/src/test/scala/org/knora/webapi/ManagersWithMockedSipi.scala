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
import org.knora.webapi.store.iiif.MockSipiConnector
import org.knora.webapi.store.cacheservice.CacheServiceManager
import zio.Runtime
import zio.ZIO


/**
 * Mixin trait for running the application with mocked Sipi
 */
trait ManagersWithMockedSipi extends Managers {
  this: Actor =>

  lazy val mockStoreConnectors: Map[String, ActorRef] = Map(
    SipiConnectorActorName -> context.actorOf(Props(new MockSipiConnector))
  )
  lazy val mockResponders: Map[String, ActorRef] = Map.empty[String, ActorRef]

  lazy val cacheServiceManager: CacheServiceManager = Runtime.default
    .unsafeRun(
      (for (manager <- ZIO.service[CacheServiceManager])
        yield manager).provide(CacheServiceInMemImpl.layer, CacheServiceManager.layer)
    )

  lazy val storeManager: ActorRef = context.actorOf(
    Props(
      new MockableStoreManager(mockStoreConnectors = mockStoreConnectors, appActor = self, csm = cacheServiceManager)
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
