/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.stream.Materializer

import scala.concurrent.ExecutionContext

import org.knora.webapi.config.AppConfig
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.triplestore.TriplestoreServiceManager

/**
 * Knora Core abstraction.
 */
trait Core {
  implicit val system: ActorSystem

  implicit val settings: KnoraSettingsImpl

  implicit val materializer: Materializer

  implicit val executionContext: ExecutionContext

  val iiifServiceManager: IIIFServiceManager

  val cacheServiceManager: CacheServiceManager

  val triplestoreServiceManager: TriplestoreServiceManager

  val appConfig: AppConfig

  val runtime: zio.Runtime[Any]

  val appActor: ActorRef
}
