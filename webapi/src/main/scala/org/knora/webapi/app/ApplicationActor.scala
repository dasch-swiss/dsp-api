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
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.LiveActorMaker
import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.MissingLastModificationDateOntologyException
import dsp.errors.SipiException
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
import org.knora.webapi.responders.ResponderManager
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
import org.knora.webapi.util.ActorUtil.future2Message
import org.knora.webapi.util.cache.CacheUtil
import redis.clients.jedis.exceptions.JedisConnectionException

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceRequest
import org.knora.webapi.messages.store.sipimessages.IIIFRequest
import org.knora.webapi.util.ActorUtil
import org.knora.webapi.store.triplestore.TriplestoreServiceManager

/**
 * This is the first actor in the application. All other actors are children
 * of this actor and thus it takes also the role of the supervisor actor.
 * It accepts messages for starting and stopping the Knora-API, holds the
 * current state of the application, and is responsible for coordination of
 * the startup and shutdown sequence. Further, it forwards any messages meant
 * for responders or the store to the respective actor.
 */
class ApplicationActor(
  cacheServiceManager: CacheServiceManager,
  iiifServiceManager: IIIFServiceManager,
  triplestoreManager: TriplestoreServiceManager,
  appConfig: AppConfig
) extends Actor
    with Stash
    with LazyLogging
    with AroundDirectives
    with Timers {

  implicit val system: ActorSystem = context.system
  val log                          = logger

  log.debug("entered the ApplicationManager constructor")

  /**
   * The application's configuration.
   */
  implicit val knoraSettings: KnoraSettingsImpl = KnoraSettings(system)

  /**
   * The Cache Service's configuration.
   */
  implicit val cacheServiceSettings: CacheServiceSettings = new CacheServiceSettings(system.settings.config)

  /**
   * Provides the actor materializer (akka-http)
   */
  implicit val materializer: Materializer = Materializer.matFromSystem(system)

  /**
   * Provides the default global execution context
   */
  implicit val executionContext: ExecutionContext = context.dispatcher

  /**
   * Timeout definition
   */
  implicit protected val timeout: Timeout = knoraSettings.defaultTimeout

  /**
   * Data used in responders.
   */
  val responderData: ResponderData = ResponderData(system, self, knoraSettings, cacheServiceSettings)

  /**
   * The object that forwards messages to responder instances to handle API requests.
   */
  val responderManager: ResponderManager = ResponderManager(responderData)

  /**
   * Route data.
   */
  private val routeData = KnoraRouteData(
    system = system,
    appActor = self
  )

  /**
   * This actor acts as the supervisor for its child actors.
   * Here we can override the default supervisor strategy.
   */
  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
      case _: ArithmeticException      => Resume
      case _: NullPointerException     => Restart
      case _: IllegalArgumentException => Stop
      case e: InconsistentRepositoryDataException =>
        log.info(s"Received a 'InconsistentTriplestoreDataException', will shutdown now. Cause: ${e.message}")
        Stop
      case e: SipiException =>
        log.warn(s"Received a 'SipiException', will continue. Cause: ${e.message}")
        Resume
      case _: JedisConnectionException =>
        log.warn(s"Received a 'JedisConnectionException', will continue. Probably the Redis-Server is not running.")
        Resume
      case _: Exception => Escalate
    }

  private var appState: AppState       = AppStates.Stopped
  private var allowReloadOverHTTPState = false
  private var printConfigState         = false
  private var ignoreRepository         = true
  private var withIIIFService          = true
  private val withCacheService         = cacheServiceSettings.cacheServiceEnabled

  /**
   * Startup of the ApplicationActor is a two step process:
   * 1. Step: Start the http server and bind to ip and port. This is done with
   * the "initializing" behaviour
   * - Success: After a successful bind, go to step 2.
   * - Failure: If bind fails, then retry up to 5 times before exiting.
   *
   * 2. Step:
   */
  def receive: Receive = initializing()

  def initializing(): Receive = {
    /* Called from main. Initiates application startup. */
    case appStartMsg: AppStart =>
      log.info("==> AppStart")
      appStart(appStartMsg.ignoreRepository, appStartMsg.requiresIIIFService, appStartMsg.retryCnt)
    case AppStop() =>
      log.info("==> AppStop")
      appStop()
    case AppReady() =>
      log.info("==> AppReady")
      unstashAll() // unstash any messages, so that they can be processed
      context.become(ready(), discardOld = true)
    case _ =>
      stash() // stash any messages which we cannot handle in this state
  }

  def ready(): Receive = {
    /* Usually only called from tests */
    case AppStop() =>
      appStop()

    /* Called from the "appStart" method. Entry point for startup sequence. */
    case initStartUp: InitStartUp =>
      log.info("=> InitStartUp")

      if (appState == AppStates.Stopped) {
        ignoreRepository = initStartUp.ignoreRepository
        withIIIFService = initStartUp.requiresIIIFService

        self ! SetAppState(AppStates.StartingUp)
      }

    /* Each app state change goes through here */
    case SetAppState(value: AppState) =>
      appState = value
      log.debug("appStateChanged - to state: {}", value)
      value match {
        case AppStates.Stopped =>
        // do nothing
        case AppStates.StartingUp =>
          self ! SetAppState(AppStates.WaitingForTriplestore)

        case AppStates.WaitingForTriplestore =>
          // check DB
          self ! CheckTriplestore()

        case AppStates.TriplestoreReady =>
          self ! SetAppState(AppStates.UpdatingRepository)

        case AppStates.UpdatingRepository =>
          if (ignoreRepository) {
            self ! SetAppState(AppStates.RepositoryUpToDate)
          } else {
            self ! UpdateRepository()
          }

        case AppStates.RepositoryUpToDate =>
          self ! SetAppState(AppStates.CreatingCaches)

        case AppStates.CreatingCaches =>
          self ! CreateCaches()

        case AppStates.CachesReady =>
          self ! SetAppState(AppStates.LoadingOntologies)

        case AppStates.LoadingOntologies =>
          if (ignoreRepository) {
            self ! SetAppState(AppStates.OntologiesReady)
          } else {
            self ! LoadOntologies()
          }

        case AppStates.OntologiesReady =>
          self ! SetAppState(AppStates.WaitingForIIIFService)

        case AppStates.WaitingForIIIFService =>
          if (withIIIFService) {
            // check if sipi is running
            self ! CheckIIIFService
          } else {
            // skip sipi check
            self ! SetAppState(AppStates.IIIFServiceReady)
          }

        case AppStates.IIIFServiceReady =>
          self ! SetAppState(AppStates.WaitingForCacheService)

        case AppStates.WaitingForCacheService =>
          if (withCacheService) {
            self ! CheckCacheService
          } else {
            self ! SetAppState(AppStates.CacheServiceReady)
          }

        case AppStates.CacheServiceReady =>
          self ! SetAppState(AppStates.Running)

        case AppStates.Running =>
          log.info("=> Running")
          printBanner()

        case AppStates.MaintenanceMode =>
          // do nothing
          ()

        case other =>
          throw UnsupportedValueException(
            s"The value: $other is not supported."
          )
      }

    case GetAppState() =>
      log.debug("ApplicationStateActor - GetAppState - value: {}", appState)
      sender() ! appState

    case ActorReady() =>
      sender() ! ActorReadyAck()

    case SetAllowReloadOverHTTPState(value) =>
      log.debug("ApplicationStateActor - SetAllowReloadOverHTTPState - value: {}", value)
      allowReloadOverHTTPState = value

    case GetAllowReloadOverHTTPState() =>
      log.debug("ApplicationStateActor - GetAllowReloadOverHTTPState - value: {}", allowReloadOverHTTPState)
      sender() ! (allowReloadOverHTTPState | knoraSettings.allowReloadOverHTTP)

    case SetPrintConfigExtendedState(value) =>
      log.debug("ApplicationStateActor - SetPrintConfigExtendedState - value: {}", value)
      printConfigState = value

    case GetPrintConfigExtendedState() =>
      log.debug("ApplicationStateActor - GetPrintConfigExtendedState - value: {}", printConfigState)
      sender() ! (printConfigState | knoraSettings.printExtendedConfig)

    /* check repository request */
    case CheckTriplestore() =>
      self ! CheckTriplestoreRequest()

    /* check repository response */
    case CheckTriplestoreResponse(status, message) =>
      status match {
        case TriplestoreStatus.ServiceAvailable =>
          self ! SetAppState(AppStates.TriplestoreReady)
        case TriplestoreStatus.NotInitialized =>
          log.warn(s"checkRepository - status: $status, message: $message")
          log.warn("Please initialize repository.")
          timers.startSingleTimer("CheckRepository", CheckTriplestore(), 5.seconds)
        case TriplestoreStatus.ServiceUnavailable =>
          log.warn(s"checkRepository - status: $status, message: $status")
          log.warn("Please start repository.")
          timers.startSingleTimer("CheckRepository", CheckTriplestore(), 5.seconds)
      }

    case UpdateRepository() =>
      self ! UpdateRepositoryRequest()

    case RepositoryUpdatedResponse(message) =>
      log.info(message)
      self ! SetAppState(AppStates.RepositoryUpToDate)

    /* create caches request */
    case CreateCaches() =>
      CacheUtil.createCaches(knoraSettings.caches)
      self ! SetAppState(AppStates.CachesReady)

    /* load ontologies request */
    case LoadOntologies() =>
      self ! LoadOntologiesRequestV2(
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )

    /* load ontologies response */
    case SuccessResponseV2(_) =>
      self ! SetAppState(AppStates.OntologiesReady)

    case CheckIIIFService =>
      self ! IIIFServiceGetStatus

    case IIIFServiceStatusOK =>
      self ! SetAppState(AppStates.IIIFServiceReady)

    case IIIFServiceStatusNOK if withIIIFService =>
      log.warn("Sipi not running. Please start it.")
      timers.startSingleTimer("CheckIIIFService", CheckIIIFService, 5.seconds)

    case CheckCacheService =>
      self ! CacheServiceGetStatus

    case CacheServiceStatusOK =>
      self ! SetAppState(AppStates.CacheServiceReady)

    case CacheServiceStatusNOK =>
      log.warn("Redis server not running. Please start it.")
      timers.startSingleTimer("CheckCacheService", CheckCacheService, 5.seconds)

    // Forward messages to the responder manager and the different store managers.
    case msg: KnoraRequestV1      => future2Message(sender(), responderManager.receive(msg), log)
    case msg: KnoraRequestV2      => future2Message(sender(), responderManager.receive(msg), log)
    case msg: KnoraRequestADM     => future2Message(sender(), responderManager.receive(msg), log)
    case msg: CacheServiceRequest => ActorUtil.zio2Message(sender(), cacheServiceManager.receive(msg), appConfig)
    case msg: IIIFRequest         => ActorUtil.zio2Message(sender(), iiifServiceManager.receive(msg), appConfig)
    case msg: TriplestoreRequest  => ActorUtil.zio2Message(sender(), triplestoreManager.receive(msg), appConfig)

    case akka.actor.Status.Failure(ex: Exception) =>
      ex match {
        case MissingLastModificationDateOntologyException(_, _) =>
          log.info("Application stopped because of loading ontology into the cache failed.")
          appStop()
        case _ => throw ex
      }

    case other =>
      throw UnexpectedMessageException(
        s"ApplicationActor received an unexpected message $other of type ${other.getClass.getCanonicalName}"
      )
  }

  /**
   * All routes composed together and CORS activated based on the
   * the configuration in application.conf (akka-http-cors).
   *
   * ALL requests go through each of the routes in ORDER.
   * The FIRST matching route is used for handling a request.
   */
  private val apiRoutes: Route = logDuration {
    ServerVersion.addServerHeader {
      DSPApiDirectives.handleErrors(system) {
        CorsDirectives.cors(CorsSettings(system)) {
          DSPApiDirectives.handleErrors(system) {
            new HealthRoute(routeData).knoraApiPath ~
              new VersionRoute(routeData).knoraApiPath ~
              new RejectingRoute(routeData).knoraApiPath ~
              new ResourcesRouteV1(routeData).knoraApiPath ~
              new ValuesRouteV1(routeData).knoraApiPath ~
              new StandoffRouteV1(routeData).knoraApiPath ~
              new ListsRouteV1(routeData).knoraApiPath ~
              new ResourceTypesRouteV1(routeData).knoraApiPath ~
              new SearchRouteV1(routeData).knoraApiPath ~
              new AuthenticationRouteV1(routeData).knoraApiPath ~
              new AssetsRouteV1(routeData).knoraApiPath ~
              new CkanRouteV1(routeData).knoraApiPath ~
              new UsersRouteV1(routeData).knoraApiPath ~
              new ProjectsRouteV1(routeData).knoraApiPath ~
              new OntologiesRouteV2(routeData).knoraApiPath ~
              new SearchRouteV2(routeData).knoraApiPath ~
              new ResourcesRouteV2(routeData).knoraApiPath ~
              new ValuesRouteV2(routeData).knoraApiPath ~
              new StandoffRouteV2(routeData).knoraApiPath ~
              new ListsRouteV2(routeData).knoraApiPath ~
              new AuthenticationRouteV2(routeData).knoraApiPath ~
              new GroupsRouteADM(routeData).knoraApiPath ~
              new ListsRouteADM(routeData).knoraApiPath ~
              new PermissionsRouteADM(routeData).knoraApiPath ~
              new ProjectsRouteADM(routeData).knoraApiPath ~
              new StoreRouteADM(routeData).knoraApiPath ~
              new UsersRouteADM(routeData).knoraApiPath ~
              new SipiRouteADM(routeData).knoraApiPath ~
              new SwaggerApiDocsRoute(routeData).knoraApiPath
          }
        }
      }
    }
  }

  /**
   * Starts the Knora-API server.
   *
   * @param ignoreRepository    if `true`, don't read anything from the repository on startup.
   * @param requiresIIIFService if `true`, ensure that the IIIF service is started.
   * @param retryCnt            how many times was this command tried
   */
  def appStart(ignoreRepository: Boolean, requiresIIIFService: Boolean, retryCnt: Int): Unit = {

    val bindingFuture: Future[Http.ServerBinding] = Http()
      .newServerAt(knoraSettings.internalKnoraApiHost, knoraSettings.internalKnoraApiPort)
      .bindFlow(Route.toFlow(apiRoutes))

    bindingFuture onComplete {
      case Success(_) =>
        // Transition to ready state
        self ! AppReady()

        // Kick of startup procedure.
        self ! InitStartUp(ignoreRepository, requiresIIIFService)

      case Failure(ex) =>
        if (retryCnt < 5) {
          log.error(
            "Failed to bind to {}:{}! - {} - retryCnt: {}",
            knoraSettings.internalKnoraApiHost,
            knoraSettings.internalKnoraApiPort,
            ex.getMessage,
            retryCnt
          )

          // wait 1 second before trying to start again
          system.scheduler.scheduleOnce(1000 milliseconds) {
            self ! AppStart(ignoreRepository, requiresIIIFService, retryCnt + 1)
          }
        } else {
          log.error(
            "Failed to bind to {}:{}! - {}",
            knoraSettings.internalKnoraApiHost,
            knoraSettings.internalKnoraApiPort,
            ex.getMessage
          )
          self ! AppStop()
        }
    }
  }

  /**
   * Stops Knora-API.
   */
  def appStop(): Unit = {
    log.info("ApplicationActor - initiating shutdown ...")
    context.stop(self)
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info("ApplicationActor - ... shutdown in progress, initiating post stop cleanup...")

    CacheUtil.removeAllCaches()

    log.info("ApplicationActor - ... Bye!")

    Http().shutdownAllConnectionPools() andThen { case _ => system.terminate() }
  }

  /**
   * Prints the welcome message
   */
  private def printBanner(): Unit = {

    var msg =
      """
        |  ____  ____  ____         _    ____ ___
        | |  _ \/ ___||  _ \       / \  |  _ \_ _|
        | | | | \___ \| |_) |____ / _ \ | |_) | |
        | | |_| |___) |  __/_____/ ___ \|  __/| |
        | |____/|____/|_|       /_/   \_\_|  |___|
            """.stripMargin

    msg += "\n"
    msg += s"DSP-API Server started: http://${knoraSettings.internalKnoraApiHost}:${knoraSettings.internalKnoraApiPort}\n"
    msg += "------------------------------------------------\n"

    if (allowReloadOverHTTPState | knoraSettings.allowReloadOverHTTP) {
      msg += "WARNING: Resetting DB over HTTP is turned ON.\n"
      msg += "------------------------------------------------\n"
    }

    // which repository are we using
    msg += s"DB-Name:   ${knoraSettings.triplestoreDatabaseName}\t DB-Type: ${knoraSettings.triplestoreType}\n"
    msg += s"DB-Server: ${knoraSettings.triplestoreHost}\t\t DB Port: ${knoraSettings.triplestorePort}\n"

    if (printConfigState | knoraSettings.printExtendedConfig) {

      msg += s"DB User: ${knoraSettings.triplestoreUsername}\n"
      msg += s"DB Password: ${knoraSettings.triplestorePassword}\n"

      msg += s"Swagger Json: ${knoraSettings.externalKnoraApiBaseUrl}/api-docs/swagger.json\n"
      msg += s"Webapi internal URL: ${knoraSettings.internalKnoraApiBaseUrl}\n"
      msg += s"Webapi external URL: ${knoraSettings.externalKnoraApiBaseUrl}\n"
      msg += s"Sipi internal URL: ${knoraSettings.internalSipiBaseUrl}\n"
      msg += s"Sipi external URL: ${knoraSettings.externalSipiBaseUrl}\n"
    }

    msg += "================================================\n"

    println(msg)
  }
}
