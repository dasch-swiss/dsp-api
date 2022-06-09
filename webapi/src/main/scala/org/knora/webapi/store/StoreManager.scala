/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store

import akka.actor._
import akka.event.LoggingReceive
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.LiveActorMaker
import org.knora.webapi.core._
import dsp.errors.UnexpectedMessageException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.feature.KnoraSettingsFeatureFactoryConfig
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceRequest
import org.knora.webapi.messages.store.sipimessages.IIIFRequest
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreRequest
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.settings.KnoraSettings
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.settings._
import org.knora.webapi.store.cacheservice.CacheServiceManager
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.triplestore.TriplestoreManager
import org.knora.webapi.util.ActorUtil

import scala.concurrent.ExecutionContext

/**
 * This actor receives messages for different stores, and forwards them to the corresponding store manager.
 * At the moment only triple stores and Sipi are implemented, but in the future, support for different
 * remote repositories will probably be needed. This place would then be the crossroad for these different kinds
 * of 'stores' and their requests.
 *
 * @param appActor a reference to the main application actor.
 */
class StoreManager(
  appActor: ActorRef,
  cacheServiceManager: CacheServiceManager,
  iiifsm: IIIFServiceManager,
  appConfig: AppConfig
) extends Actor
    with ActorLogging {
  this: ActorMaker =>

  /**
   * The Knora Akka actor system.
   */
  protected implicit val system: ActorSystem = context.system

  /**
   * The Akka actor system's execution context for futures.
   */
  protected implicit val executionContext: ExecutionContext =
    system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  /**
   * The Knora settings.
   */
  protected val settings: KnoraSettingsImpl = KnoraSettings(system)

  /**
   * The default feature factory configuration.
   */
  protected val defaultFeatureFactoryConfig: FeatureFactoryConfig = new KnoraSettingsFeatureFactoryConfig(settings)

  /**
   * Starts the Triplestore Manager Actor
   */
  protected lazy val triplestoreManager: ActorRef = makeActor(
    Props(
      new TriplestoreManager(
        appActor = appActor,
        settings = settings,
        defaultFeatureFactoryConfig = defaultFeatureFactoryConfig
      ) with LiveActorMaker
    ).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    TriplestoreManagerActorName
  )

  def receive: Receive = LoggingReceive {
    case req: CacheServiceRequest => ActorUtil.zio2Message(sender(), cacheServiceManager.receive(req), log, appConfig)
    case req: IIIFRequest         => ActorUtil.zio2Message(sender(), iiifsm.receive(req), log, appConfig)
    case req: TriplestoreRequest  => triplestoreManager forward req
    case other =>
      sender() ! Status.Failure(UnexpectedMessageException(s"StoreManager received an unexpected message: $other"))
  }
}
