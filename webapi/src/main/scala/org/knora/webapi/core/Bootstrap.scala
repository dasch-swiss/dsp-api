/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.knora.webapi.config.AppConfig
import zio._
import org.knora.webapi.util.cache.CacheUtil
import org.knora.webapi.settings.KnoraSettings
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.messages.store.sipimessages.IIIFServiceStatusNOK
import org.knora.webapi.messages.store.sipimessages.IIIFServiceStatusOK
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceStatusNOK
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceStatusOK
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.auth.JWTService
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceHttpConnectorImpl

/**
 * The application bootstrapper
 */
object Bootstrap {

  val bootstrap = ZEnvironment()
    ZLayer.make[
      ActorSystem with AppConfig with AppRouter with CacheServiceManager with CacheService with HttpServer with IIIFServiceManager with IIIFService with JWTService with RepositoryUpdater with TriplestoreServiceManager with TriplestoreService
    ](
      ActorSystem.layer,
      AppConfig.live,
      AppRouter.layer,
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer,
      HttpServer.layer,
      IIIFServiceManager.layer,
      IIIFServiceSipiImpl.layer,
      JWTService.layer,
      RepositoryUpdater.layer,
      TriplestoreServiceManager.layer,
      TriplestoreServiceHttpConnectorImpl.layer
    )

  /**
   * Initiates the startup sequence of the DSP-API server.
   *
   * @param requiresRepository  if `true`, check if it is running, run upgrading and loading ontology cache.
   *                                If `false`, check if it is running but don't run upgrading AND loading ontology cache.
   * @param requiresIIIFService if `true`, ensure that the IIIF service is started.
   */
  def startup(
    requiresRepository: Boolean,
    requiresIIIFService: Boolean
  ) =
    (for {
      _ <- ZIO.service[HttpServer].flatMap(server => server.start)
      _ <- checkTriplestoreService
      _ <- upgradeRepository(requiresRepository)
      _ <- buildAllCaches
      _ <- populateOntologyCaches(requiresRepository)
      _ <- checkIIIFService(requiresIIIFService)
      _ <- checkCacheService
      _ <- printBanner
      _ <- ZIO.never
    } yield ()).forever

  /**
   * Checks if the TriplestoreService is running and the repository is properly initialized.
   */
  private val checkTriplestoreService: ZIO[TriplestoreService with AppConfig, Nothing, Unit] =
    for {
      ts     <- ZIO.service[TriplestoreService]
      status <- ts.checkTriplestore().map(_.triplestoreStatus)
      _ <- status match {
             case TriplestoreStatus.Available(msg)      => ZIO.logInfo(msg)
             case TriplestoreStatus.NotInitialized(msg) => ZIO.die(new Exception(msg))
             case TriplestoreStatus.Unavailable(msg)    => ZIO.die(new Exception(msg))
           }
    } yield ()

  /**
   * Initiates repository upgrade if `requiresRepository` is `true` an logs the result.
   */
  private def upgradeRepository(requiresRepository: Boolean): ZIO[RepositoryUpdater, Nothing, Unit] =
    if (requiresRepository)
      ZIO
        .service[RepositoryUpdater]
        .flatMap(svc => svc.maybeUpgradeRepository)
        .flatMap(response => ZIO.logInfo(response.message))
    else
      ZIO.unit

  /* Initiates building of all caches */
  private val buildAllCaches: ZIO[ActorSystem, Nothing, Unit] =
    ZIO
      .service[ActorSystem]
      .map(_.settings)
      .flatMap(settings => ZIO.attempt(CacheUtil.createCaches(settings.caches)))
      .orDie

  /* Initiates population of the ontology caches if `requiresRepository` is `true` */
  private def populateOntologyCaches(requiresRepository: Boolean): ZIO[AppRouter, Nothing, Unit] =
    if (requiresRepository)
      ZIO
        .service[AppRouter]
        .flatMap(svc => svc.populateOntologyCaches)
    else
      ZIO.unit

  /* Checks if the IIIF service is running */
  private def checkIIIFService(requiresIIIFService: Boolean): ZIO[IIIFService, Nothing, Unit] =
    if (requiresIIIFService)
      ZIO
        .service[IIIFService]
        .flatMap(svc => svc.getStatus())
        .flatMap(status =>
          status match {
            case IIIFServiceStatusOK =>
              ZIO.logInfo("IIIF service running")
            case IIIFServiceStatusNOK =>
              ZIO.logError("IIIF service not running") *> ZIO.die(new Exception("IIIF service not running"))
          }
        )
    else
      ZIO.unit

  /* Checks if the Cache service is running */
  private val checkCacheService =
    ZIO
      .service[CacheService]
      .flatMap(svc => svc.getStatus)
      .flatMap(status =>
        status match {
          case CacheServiceStatusNOK =>
            ZIO.logError("Cache service not running.") *> ZIO.die(new Exception("Cache service not running."))
          case CacheServiceStatusOK =>
            ZIO.logInfo("Cache service running.")
        }
      )

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
      _ <- ZIO.logInfo(s"DB-Server: ${config.triplestore.host}\t DB Port: ${config.triplestore.fuseki.port}")
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
