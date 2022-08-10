/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.app

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.OneForOneStrategy
import akka.actor.Props
import akka.actor.Stash
import akka.actor.SupervisorStrategy._
import akka.actor.Timers
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.typesafe.scalalogging.Logger
import org.knora.webapi.config.AppConfig
import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.MissingLastModificationDateOntologyException
import dsp.errors.UnexpectedMessageException
import dsp.errors.UnsupportedValueException
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.http.version.ServerVersion
import org.knora.webapi.messages.ResponderRequest._
import org.knora.webapi.messages.app.appmessages._
import org.knora.webapi.messages.store.StoreRequest
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceGetStatus
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceStatusNOK
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceStatusOK
import org.knora.webapi.messages.store.sipimessages.IIIFServiceGetStatus
import org.knora.webapi.messages.store.sipimessages.IIIFServiceStatusNOK
import org.knora.webapi.messages.store.sipimessages.IIIFServiceStatusOK
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.routing._
import org.knora.webapi.routing.admin._
import org.knora.webapi.routing.v1._
import org.knora.webapi.routing.v2._
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.settings.KnoraSettings
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.settings._
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.settings.CacheServiceSettings
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.errors.SipiException
import org.knora.webapi.util.ActorUtil.future2Message
import org.knora.webapi.util.cache.CacheUtil

import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceRequest
import org.knora.webapi.messages.store.sipimessages.IIIFRequest
import org.knora.webapi.util.ActorUtil
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.messages.ResponderRequest
import zio._

/**
  * The application bootstrapper
  */
object AppBoot {

  val run: ZIO[AppServer with AppConfig, Nothing, Unit] =
    for {
      _      <- ZIO.logInfo("AppBoot run initiated")
      server <- ZIO.service[AppServer]
      _      <- server.start
      _      <- printBanner
      _      <- Clock.sleep(5.seconds)
      _      <- server.stop
    } yield ()

  /**
   * Starts the Knora-API server.
   *
   * @param ignoreRepository    if `true`, don't read anything from the repository on startup.
   * @param requiresIIIFService if `true`, ensure that the IIIF service is started.
   * @param retryCnt            how many times was this command tried
   */
  def start(ignoreRepository: Boolean, requiresIIIFService: Boolean, retryCnt: Int): Unit = ???

  /**
   * Stops Knora-API.
   */
  def stop(): Unit = ???

  /**
   * Prints the welcome message
   */
  private val printBanner: ZIO[AppConfig, Nothing, Unit] = {

    val logo =
      """
        |  ____  ____  ____         _    ____ ___
        | |  _ \/ ___||  _ \       / \  |  _ \_ _|
        | | | | \___ \| |_) |____ / _ \ | |_) | |
        | | |_| |___) |  __/_____/ ___ \|  __/| |
        | |____/|____/|_|       /_/   \_\_|  |___|
            """.stripMargin

    for {
      config <- ZIO.service[AppConfig]
      _      <- ZIO.logInfo(logo)
      _ <-
        ZIO.logInfo(
          s"DSP-API Server started: ${config.knoraApi.internalKnoraApiBaseUrl}"
        )

      _ = if (allowReloadOverHTTPState | config.allowReloadOverHttp) {
            ZIO.logWarning("Resetting DB over HTTP is turned ON")
          }

      // which repository are we using
      _ <-
        ZIO.logInfo(s"DB-Name:   ${config.triplestore.fuseki.repositoryName}\t DB-Type: ${config.triplestore.dbtype}")
      _ <- ZIO.logInfo(s"DB-Server: ${config.triplestore.host}\t\t DB Port: ${config.triplestore.fuseki.port}")
    } yield ()
  }

  // private var appState: AppState       = AppStates.Stopped
  private var allowReloadOverHTTPState = false
  // private var ignoreRepository         = true
  // private var withIIIFService          = true
  // private val withCacheService         = cacheServiceSettings.cacheServiceEnabled

  // /**
  //  * Startup of the ApplicationActor is a two step process:
  //  * 1. Step: Start the http server and bind to ip and port. This is done with
  //  * the "initializing" behaviour
  //  * - Success: After a successful bind, go to step 2.
  //  * - Failure: If bind fails, then retry up to 5 times before exiting.
  //  *
  //  * 2. Step:
  //  */
  // def receive: Receive = initializing()

  // def initializing(): Receive = {
  //   /* Called from main. Initiates application startup. */
  //   case appStartMsg: AppStart =>
  //     log.info("==> AppStart")
  //     appStart(appStartMsg.ignoreRepository, appStartMsg.requiresIIIFService, appStartMsg.retryCnt)
  //   case AppStop() =>
  //     log.info("==> AppStop")
  //     appStop()
  //   case AppReady() =>
  //     log.info("==> AppReady")
  //     unstashAll() // unstash any messages, so that they can be processed
  //     context.become(ready(), discardOld = true)
  //   case _ =>
  //     stash() // stash any messages which we cannot handle in this state
  // }

  // def ready(): Receive = {

  //   /* Usually only called from tests */
  //   case AppStop() =>
  //     appStop()

  //   /* Called from the "appStart" method. Entry point for startup sequence. */
  //   case initStartUp: InitStartUp =>
  //     log.info("=> InitStartUp")

  //     if (appState == AppStates.Stopped) {
  //       ignoreRepository = initStartUp.ignoreRepository
  //       withIIIFService = initStartUp.requiresIIIFService

  //       self ! SetAppState(AppStates.StartingUp)
  //     }

  //   /* Each app state change goes through here */
  //   case SetAppState(value: AppState) =>
  //     appState = value
  //     log.debug("appStateChanged - to state: {}", value)
  //     value match {
  //       case AppStates.Stopped =>
  //       // do nothing
  //       case AppStates.StartingUp =>
  //         self ! SetAppState(AppStates.WaitingForTriplestore)

  //       case AppStates.WaitingForTriplestore =>
  //         // check DB
  //         self ! CheckTriplestore()

  //       case AppStates.TriplestoreReady =>
  //         self ! SetAppState(AppStates.UpdatingRepository)

  //       case AppStates.UpdatingRepository =>
  //         if (ignoreRepository) {
  //           self ! SetAppState(AppStates.RepositoryUpToDate)
  //         } else {
  //           self ! UpdateRepository()
  //         }

  //       case AppStates.RepositoryUpToDate =>
  //         self ! SetAppState(AppStates.CreatingCaches)

  //       case AppStates.CreatingCaches =>
  //         self ! CreateCaches()

  //       case AppStates.CachesReady =>
  //         self ! SetAppState(AppStates.LoadingOntologies)

  //       case AppStates.LoadingOntologies =>
  //         if (ignoreRepository) {
  //           self ! SetAppState(AppStates.OntologiesReady)
  //         } else {
  //           self ! LoadOntologies()
  //         }

  //       case AppStates.OntologiesReady =>
  //         self ! SetAppState(AppStates.WaitingForIIIFService)

  //       case AppStates.WaitingForIIIFService =>
  //         if (withIIIFService) {
  //           // check if sipi is running
  //           self ! CheckIIIFService
  //         } else {
  //           // skip sipi check
  //           self ! SetAppState(AppStates.IIIFServiceReady)
  //         }

  //       case AppStates.IIIFServiceReady =>
  //         self ! SetAppState(AppStates.WaitingForCacheService)

  //       case AppStates.WaitingForCacheService =>
  //         if (withCacheService) {
  //           self ! CheckCacheService
  //         } else {
  //           self ! SetAppState(AppStates.CacheServiceReady)
  //         }

  //       case AppStates.CacheServiceReady =>
  //         self ! SetAppState(AppStates.Running)

  //       case AppStates.Running =>
  //         log.info("=> Running")
  //         printBanner()

  //       case AppStates.MaintenanceMode =>
  //         // do nothing
  //         ()

  //       case other =>
  //         throw UnsupportedValueException(
  //           s"The value: $other is not supported."
  //         )
  //     }

  //   case GetAppState() =>
  //     log.debug("ApplicationStateActor - GetAppState - value: {}", appState)
  //     sender() ! appState

  //   case ActorReady() =>
  //     sender() ! ActorReadyAck()

  //   case SetAllowReloadOverHTTPState(value) =>
  //     log.debug("ApplicationStateActor - SetAllowReloadOverHTTPState - value: {}", value)
  //     allowReloadOverHTTPState = value

  //   case GetAllowReloadOverHTTPState() =>
  //     log.debug("ApplicationStateActor - GetAllowReloadOverHTTPState - value: {}", allowReloadOverHTTPState)
  //     sender() ! (allowReloadOverHTTPState | knoraSettings.allowReloadOverHTTP)

  //   /* check repository request */
  //   case CheckTriplestore() =>
  //     self ! CheckTriplestoreRequest()

  //   /* check repository response */
  //   case CheckTriplestoreResponse(status, message) =>
  //     status match {
  //       case TriplestoreStatus.ServiceAvailable =>
  //         self ! SetAppState(AppStates.TriplestoreReady)
  //       case TriplestoreStatus.NotInitialized =>
  //         log.warn(s"checkRepository - status: $status, message: $message")
  //         log.warn("Please initialize repository.")
  //         timers.startSingleTimer("CheckRepository", CheckTriplestore(), 5.seconds)
  //       case TriplestoreStatus.ServiceUnavailable =>
  //         log.warn(s"checkRepository - status: $status, message: $status")
  //         log.warn("Please start repository.")
  //         timers.startSingleTimer("CheckRepository", CheckTriplestore(), 5.seconds)
  //     }

  //   case UpdateRepository() =>
  //     self ! UpdateRepositoryRequest()

  //   case RepositoryUpdatedResponse(message) =>
  //     log.info(message)
  //     self ! SetAppState(AppStates.RepositoryUpToDate)

  //   /* create caches request */
  //   case CreateCaches() =>
  //     CacheUtil.createCaches(knoraSettings.caches)
  //     self ! SetAppState(AppStates.CachesReady)

  //   /* load ontologies request */
  //   case LoadOntologies() =>
  //     self ! LoadOntologiesRequestV2(
  //       requestingUser = KnoraSystemInstances.Users.SystemUser
  //     )

  //   /* load ontologies response */
  //   case SuccessResponseV2(_) =>
  //     self ! SetAppState(AppStates.OntologiesReady)

  //   case CheckIIIFService =>
  //     self ! IIIFServiceGetStatus

  //   case IIIFServiceStatusOK =>
  //     self ! SetAppState(AppStates.IIIFServiceReady)

  //   case IIIFServiceStatusNOK if withIIIFService =>
  //     log.warn("Sipi not running. Please start it.")
  //     timers.startSingleTimer("CheckIIIFService", CheckIIIFService, 5.seconds)

  //   case CheckCacheService =>
  //     self ! CacheServiceGetStatus

  //   case CacheServiceStatusOK =>
  //     self ! SetAppState(AppStates.CacheServiceReady)

  //   case CacheServiceStatusNOK =>
  //     log.warn("Redis server not running. Please start it.")
  //     timers.startSingleTimer("CheckCacheService", CheckCacheService, 5.seconds)

  //   case akka.actor.Status.Failure(ex: Exception) =>
  //     ex match {
  //       case MissingLastModificationDateOntologyException(_, _) =>
  //         log.info("Application stopped because of loading ontology into the cache failed.")
  //         appStop()
  //       case _ => throw ex
  //     }

  //   case other =>
  //     throw UnexpectedMessageException(
  //       s"ApplicationActor received an unexpected message $other of type ${other.getClass.getCanonicalName}"
  //     )
  // }

}
