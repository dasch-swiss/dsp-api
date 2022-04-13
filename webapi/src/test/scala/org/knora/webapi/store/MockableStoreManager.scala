/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store

import akka.actor.{ActorRef, Props}
import org.knora.webapi.core.LiveActorMaker
import org.knora.webapi.settings.{KnoraDispatchers, _}
import org.knora.webapi.store.cacheservice.api.CacheService
import zio.ZLayer
import org.knora.webapi.store.cacheservice.CacheServiceManager
import org.knora.webapi.store.iiif.IIIFServiceManager

class MockableStoreManager(appActor: ActorRef, iiifsm: IIIFServiceManager, csm: CacheServiceManager)
    extends StoreManager(appActor, iiifsm, csm)
    with LiveActorMaker {

}
