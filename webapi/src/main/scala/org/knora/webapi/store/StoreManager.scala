/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store

import akka.actor._
import akka.event.LoggingReceive
import org.knora.webapi.core.{LiveActorMaker, _}
import org.knora.webapi.exceptions.UnexpectedMessageException
import org.knora.webapi.feature.{FeatureFactoryConfig, KnoraSettingsFeatureFactoryConfig}
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceRequest
import org.knora.webapi.messages.store.sipimessages.IIIFRequest
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreRequest
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl, _}
import org.knora.webapi.store.cacheservice.{CacheService, CacheServiceManager}
import org.knora.webapi.store.iiif.IIIFManager
import org.knora.webapi.store.triplestore.TriplestoreManager

import scala.concurrent.ExecutionContext

/**
 * This actor receives messages for different stores, and forwards them to the corresponding store manager.
 * At the moment only triple stores and Sipi are implemented, but in the future, support for different
 * remote repositories will probably be needed. This place would then be the crossroad for these different kinds
 * of 'stores' and their requests.
 *
 * @param appActor a reference to the main application actor.
 */
class StoreManager(appActor: ActorRef, cs: CacheService) extends Actor with ActorLogging {
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

  /**
   * Starts the IIIF Manager Actor
   */
  protected lazy val iiifManager: ActorRef = makeActor(
    Props(new IIIFManager with LiveActorMaker).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    IIIFManagerActorName
  )

  /**
   * Instantiates the Redis Manager
   */
  protected lazy val cacheServiceManager: ActorRef = makeActor(
    Props(new CacheServiceManager(cs)).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    RedisManagerActorName
  )

  def receive: Receive = LoggingReceive {
    case tripleStoreMessage: TriplestoreRequest    => triplestoreManager forward tripleStoreMessage
    case iiifMessages: IIIFRequest                 => iiifManager forward iiifMessages
    case cacheServiceMessages: CacheServiceRequest => cacheServiceManager forward cacheServiceMessages
    case other =>
      sender() ! Status.Failure(UnexpectedMessageException(s"StoreManager received an unexpected message: $other"))
  }
}
