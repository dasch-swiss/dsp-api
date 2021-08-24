/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  The DaSCH Service Platform  is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU Affero General Public
 *  License as published by the Free Software Foundation, either version 3
 *  of the License, or (at your option) any later version.
 *
 *  The DaSCH Service Platform is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with the DaSCH Service Platform.  If not, see
 *  <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.cacheservice.settings

import com.typesafe.config.Config

/**
 * Holds the Cache Service specific settings.
 */
class CacheServiceSettings(config: Config) {
  val cacheServiceEnabled: Boolean  = config.getBoolean("app.cache-service.enabled")
  val cacheServiceRedisHost: String = config.getString("app.cache-service.redis.host")
  val cacheServiceRedisPort: Int    = config.getInt("app.cache-service.redis.port")
}
