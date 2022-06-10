/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache.settings

import com.typesafe.config.Config

/**
 * Holds the Cache Service specific settings.
 */
class CacheServiceSettings(config: Config) {
  val cacheServiceEnabled: Boolean  = config.getBoolean("app.cache-service.enabled")
  val cacheServiceRedisHost: String = config.getString("app.cache-service.redis.host")
  val cacheServiceRedisPort: Int    = config.getInt("app.cache-service.redis.port")
}
