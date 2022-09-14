/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.cache

import akka.testkit.TestKit

import org.knora.webapi.CoreSpec
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.sharedtestdata.SharedTestDataV1

class CacheUtilSpec extends CoreSpec {

  private val cacheName = Authenticator.AUTHENTICATION_INVALIDATION_CACHE_NAME
  private val sessionId = System.currentTimeMillis().toString

  "Caching" should {

    "allow to set and get the value " in {
      CacheUtil.removeAllCaches()
      CacheUtil.createCaches(appConfig.cacheConfigs)
      CacheUtil.put(cacheName, sessionId, SharedTestDataV1.rootUser)
      CacheUtil.get(cacheName, sessionId) should be(Some(SharedTestDataV1.rootUser))
      TestKit.shutdownActorSystem(system)
    }

    "return none if key is not found " in {
      CacheUtil.removeAllCaches()
      CacheUtil.createCaches(appConfig.cacheConfigs)
      CacheUtil.get(cacheName, 213.toString) should be(None)
      TestKit.shutdownActorSystem(system)
    }

    "allow to delete a set value " in {
      CacheUtil.removeAllCaches()
      CacheUtil.createCaches(appConfig.cacheConfigs)
      CacheUtil.remove(cacheName, sessionId)
      CacheUtil.get(cacheName, sessionId) should be(None)
      TestKit.shutdownActorSystem(system)
    }
  }
}
