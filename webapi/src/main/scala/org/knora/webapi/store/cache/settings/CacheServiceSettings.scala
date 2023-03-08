/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache.settings

import org.knora.webapi.config.AppConfig

/**
 * Holds the Cache Service specific settings.
 */
class CacheServiceSettings(appConfig: AppConfig) {
  val cacheServiceEnabled: Boolean = appConfig.cacheService.enabled
}
