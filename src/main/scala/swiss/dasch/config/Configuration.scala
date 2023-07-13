/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.config

import com.typesafe.config
import com.typesafe.config.ConfigFactory
import zio.*
import zio.config.*
import zio.config.ConfigDescriptor.*
import zio.config.magnolia.descriptor
import zio.config.typesafe.{ FromConfigTypesafe, TypesafeConfigSource }
import zio.nio.file.{ Files, Path }

object Configuration {
  final private case class ApplicationConf(
      jwt: JwtConfig,
      service: ServiceConfig,
      storage: StorageConfig,
    )

  final case class JwtConfig(
      secret: String,
      audience: String,
      issuer: String,
      disableAuth: Boolean = false,
    )

  final case class ServiceConfig(
      host: String,
      port: Int,
      logFormat: String,
    )

  final case class StorageConfig(assetDir: String, tempDir: String) {
    val assetPath: Path = Path(assetDir)
    val tempPath: Path  = Path(tempDir)
  }

  val layer: Layer[ReadError[String], ServiceConfig with JwtConfig with StorageConfig] = {
    val applicationConf = ZConfig.fromTypesafeConfig(
      ZIO.attempt(ConfigFactory.defaultApplication().resolve()),
      descriptor[ApplicationConf].mapKey(toKebabCase),
    )
    applicationConf.project(_.service) ++ applicationConf.project(_.storage) ++ applicationConf.project(_.jwt)
  }
}
