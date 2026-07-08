/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import zio.*
import zio.config.typesafe.TypesafeConfigProvider

/** Test helpers for building a Typesafe-backed ZIO `ConfigProvider`. */
object TestAppConfig {

  /**
   * Builds a `ConfigProvider` rooted at `app.*` from the default `application.conf`,
   * optionally overriding individual keys. Override keys must be fully qualified
   * (e.g. `app.features.allow-placeholder`).
   */
  def provider(overrides: (String, Any)*): ConfigProvider = {
    val merged = overrides.foldLeft(ConfigFactory.load()) { case (cfg, (k, v)) =>
      cfg.withValue(k, ConfigValueFactory.fromAnyRef(v))
    }
    TypesafeConfigProvider.fromTypesafeConfig(merged.getConfig("app").resolve)
  }

  /** Layer that installs the runtime config provider for tests. */
  def layer(overrides: (String, Any)*): ULayer[Unit] =
    Runtime.setConfigProvider(provider(overrides*))
}
