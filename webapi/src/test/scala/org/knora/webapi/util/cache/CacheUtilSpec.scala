/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.cache

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.settings.KnoraSettings
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.sharedtestdata.SharedTestDataV1

class CacheUtilSpec
    extends TestKit(ActorSystem("CacheUtilSpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with LazyLogging
    with Authenticator {

  StringFormatter.initForTest()
  val settings: KnoraSettingsImpl = KnoraSettings(system)

  private val cacheName = Authenticator.AUTHENTICATION_INVALIDATION_CACHE_NAME
  private val sessionId = System.currentTimeMillis().toString

  final override def beforeAll(): Unit = {
    CacheUtil.removeAllCaches()
    CacheUtil.createCaches(settings.caches)
  }

  final override def afterAll(): Unit = {
    CacheUtil.removeAllCaches()
    TestKit.shutdownActorSystem(system)
  }

  "Caching" should {

    "allow to set and get the value " in {
      CacheUtil.put(cacheName, sessionId, SharedTestDataV1.rootUser)
      CacheUtil.get(cacheName, sessionId) should be(Some(SharedTestDataV1.rootUser))
    }

    "return none if key is not found " in {
      CacheUtil.get(cacheName, 213.toString) should be(None)
    }

    "allow to delete a set value " in {
      CacheUtil.remove(cacheName, sessionId)
      CacheUtil.get(cacheName, sessionId) should be(None)
    }
  }
}
