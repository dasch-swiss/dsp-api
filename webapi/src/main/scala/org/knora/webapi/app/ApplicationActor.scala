package org.knora.webapi.app

import akka.actor.SupervisorStrategy._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, OneForOneStrategy, Props, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import kamon.Kamon
import org.knora.webapi._
import org.knora.webapi.http.CORSSupport.CORS
import org.knora.webapi.http.ServerVersion.addServerHeader
import org.knora.webapi.messages.admin.responder.KnoraRequestADM
import org.knora.webapi.messages.app.appmessages.AppState.AppState
import org.knora.webapi.messages.app.appmessages._
import org.knora.webapi.messages.store.StoreRequest
import org.knora.webapi.messages.store.cacheservicemessages.{CacheServiceGetStatus, CacheServiceStatusNOK, CacheServiceStatusOK}
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.KnoraRequestV1
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.messages.v2.responder.{KnoraRequestV2, SuccessResponseV2}
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.routing._
import org.knora.webapi.routing.admin._
import org.knora.webapi.routing.v1._
import org.knora.webapi.routing.v2._
import org.knora.webapi.store.{StoreManager, StoreManagerActorName}
import org.knora.webapi.util.CacheUtil
import org.knora.webapi.messages.store.sipimessages.{IIIFServiceGetStatus, IIIFServiceStatusNOK, IIIFServiceStatusOK}
import redis.clients.jedis.exceptions.JedisConnectionException

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait Managers {
    val responderManager: ActorRef
    val storeManager: ActorRef
}

trait LiveManagers extends Managers {
    this: Actor =>

    // #store-responder
    /**
      * The actor that forwards messages to actors that deal with persistent storage.
      */
    lazy val storeManager: ActorRef = context.actorOf(
        Props(new StoreManager with LiveActorMaker)
          .withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
        name = StoreManagerActorName
    )

    /**
      * The actor that forwards messages to responder actors to handle API requests.
      */
    lazy val responderManager: ActorRef = context.actorOf(
        Props(new ResponderManager(self, storeManager) with LiveActorMaker)
          .withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
        name = RESPONDER_MANAGER_ACTOR_NAME
    )
    // #store-responder
}

/**
  * This is the first actor in the application. All other actors are children
  * of this actor and thus it takes also the role of the supervisor actor.
  * It accepts messages for starting and stopping the Knora-API, holds the
  * current state of the application, and is responsible for coordination of
  * the startup and shutdown sequence. Further, it forwards any messages meant
  * for responders or the store to the respective actor.
  */
class ApplicationActor extends Actor with LazyLogging with AroundDirectives with Timers {
    this: Managers =>

    private val log = akka.event.Logging(context.system, this.getClass)

    logger.debug("entered the ApplicationManager constructor")

    implicit val system: ActorSystem = context.system

    /**
      * The application's configuration.
      */
    implicit val settings: SettingsImpl = Settings(system)

    /**
      * Provides the actor materializer (akka-http)
      */
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    /**
      * Provides the default global execution context
      */
    implicit val executionContext: ExecutionContext = context.dispatcher

    /**
      * Timeout definition
      */
    implicit protected val timeout: Timeout = settings.defaultTimeout

    /**
      * A user representing the Knora API server, used for initialisation on startup.
      */
    private val systemUser = KnoraSystemInstances.Users.SystemUser

    /**
      * Route data.
      */
    private val routeData = KnoraRouteData(system, self)


    /**
      * This actor acts as the supervisor for its child actors.
      * Here we can override the default supervisor strategy.
      */
    override val supervisorStrategy: OneForOneStrategy =
        OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
            case _: ArithmeticException      => Resume
            case _: NullPointerException     => Restart
            case _: IllegalArgumentException => Stop
            case e: InconsistentTriplestoreDataException =>
                logger.info(s"Received a 'InconsistentTriplestoreDataException', will shutdown now. Cause: {}", e.message)
                Stop
            case e: SipiException =>
                logger.warn(s"Received a 'SipiException', will continue. Cause: {}", e.message)
                Resume
            case _: JedisConnectionException =>
                logger.warn(s"Received a 'JedisConnectionException', will continue. Probably the Redis-Server is not running.")
                Resume
            case _: Exception                => Escalate
        }

    private var appState: AppState = AppState.Stopped
    private var allowReloadOverHTTPState = false
    private var printExtendedConfigState = false
    private var skipOntologies = true
    private var withIIIFService = true
    private val withCacheService = settings.cacheServiceEnabled

    def receive: PartialFunction[Any, Unit] = {

        /* Entry point for startup sequence */
        case InitStartUp(skipLoadingOfOntologies, requiresIIIFService) => {
            logger.info("Startup initiated, please wait ...")

            if (appState == AppState.Stopped) {
                skipOntologies = skipLoadingOfOntologies
                withIIIFService = requiresIIIFService

                self ! SetAppState(AppState.StartingUp)
            }
        }

        /* EACH app state change goes through here */
        case SetAppState(value: AppState) => {

            appState = value

            logger.debug("appStateChanged - to state: {}", value)

            value match {
                case AppState.Stopped =>
                    // do nothing
                case AppState.StartingUp =>
                    self ! SetAppState(AppState.WaitingForRepository)
                    
                case AppState.WaitingForRepository =>
                    // check DB
                    self ! CheckRepository()

                case AppState.RepositoryReady =>
                    self ! SetAppState(AppState.CreatingCaches)

                case AppState.CreatingCaches =>
                    self ! CreateCaches()

                case AppState.CachesReady =>
                    self ! SetAppState(AppState.UpdatingSearchIndex)

                case AppState.UpdatingSearchIndex =>
                    self ! UpdateSearchIndex()

                case AppState.SearchIndexReady =>
                    self ! SetAppState(AppState.LoadingOntologies)

                case AppState.LoadingOntologies if skipOntologies =>
                    // skip loading of ontologies
                    self ! SetAppState(AppState.OntologiesReady)

                case AppState.LoadingOntologies if !skipOntologies =>
                    // load ontologies
                    self ! LoadOntologies()

                case AppState.OntologiesReady =>
                    self ! SetAppState(AppState.WaitingForIIIFService)

                case AppState.WaitingForIIIFService if withIIIFService =>
                    // check if sipi is running
                    self ! CheckIIIFService

                case AppState.WaitingForIIIFService if !withIIIFService =>
                    // skip sipi check
                    self ! SetAppState(AppState.IIIFServiceReady)

                case AppState.IIIFServiceReady =>
                    self ! SetAppState(AppState.WaitingForCacheService)

                case AppState.WaitingForCacheService if withCacheService =>
                    self ! CheckCacheService

                case AppState.WaitingForCacheService if !withCacheService =>
                    self ! SetAppState(AppState.CacheServiceReady)

                case AppState.CacheServiceReady =>
                    self ! SetAppState(AppState.Running)

                case AppState.Running =>
                    printBanner()

                case AppState.MaintenanceMode =>
                    // do nothing

                case other =>
                    throw UnsupportedValueException(
                        s"The value: $other is not supported."
                    )
            }
        }
        case GetAppState() => {
            logger.debug("ApplicationStateActor - GetAppState - value: {}", appState)
            sender ! appState
        }

        case ActorReady() => {
            sender ! ActorReadyAck()
        }

        case SetAllowReloadOverHTTPState(value) => {
            logger.debug("ApplicationStateActor - SetAllowReloadOverHTTPState - value: {}", value)
            allowReloadOverHTTPState = value
        }
        case GetAllowReloadOverHTTPState() => {
            logger.debug("ApplicationStateActor - GetAllowReloadOverHTTPState - value: {}", allowReloadOverHTTPState)
            sender ! (allowReloadOverHTTPState | settings.allowReloadOverHTTP)
        }

        case SetPrintConfigExtendedState(value) => {
            logger.debug("ApplicationStateActor - SetPrintConfigExtendedState - value: {}", value)
            printExtendedConfigState = value
        }
        case GetPrintConfigExtendedState() => {
            logger.debug("ApplicationStateActor - GetPrintConfigExtendedState - value: {}", printExtendedConfigState)
            sender ! (printExtendedConfigState | settings.printExtendedConfig)
        }

        /* check repository request */
        case CheckRepository() => {
            storeManager ! CheckRepositoryRequest()
        }

        /* check repository response */
        case CheckRepositoryResponse(status, message) => {
            status match {
                case RepositoryStatus.ServiceAvailable =>
                    self ! SetAppState(AppState.RepositoryReady)
                case RepositoryStatus.NotInitialized =>
                    logger.warn(s"checkRepository - status: {}, message: {}", status, message)
                    logger.warn("Please initialize repository.")
                    timers.startSingleTimer("CheckRepository", CheckRepository(), 5.seconds)
                case RepositoryStatus.ServiceUnavailable =>
                    logger.warn(s"checkRepository - status: {}, message: {}", status, message)
                    logger.warn("Please start repository.")
                    timers.startSingleTimer("CheckRepository", CheckRepository(), 5.seconds)
            }
        }

        /* create caches request */
        case CreateCaches() =>
            CacheUtil.createCaches(settings.caches)
            self ! SetAppState(AppState.CachesReady)

        case UpdateSearchIndex() =>
            storeManager ! SearchIndexUpdateRequest()

        case SparqlUpdateResponse() =>
            self ! SetAppState(AppState.SearchIndexReady)

        /* load ontologies request */
        case LoadOntologies() =>
            responderManager ! LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser)

        /* load ontologies response */
        case SuccessResponseV2(_) =>
            self ! SetAppState(AppState.OntologiesReady)

        case CheckIIIFService =>
            self ! IIIFServiceGetStatus

        case IIIFServiceStatusOK =>
            self ! SetAppState(AppState.IIIFServiceReady)

        case IIIFServiceStatusNOK if withIIIFService =>
            logger.warn("Sipi not running. Please start Sipi.")
            timers.startSingleTimer("CheckIIIFService", CheckIIIFService, 5.seconds)

        case CheckCacheService =>
            self ! CacheServiceGetStatus

        case CacheServiceStatusOK =>
            self ! SetAppState(AppState.CacheServiceReady)

        case CacheServiceStatusNOK =>
            logger.warn("Redis-Server not running. Please start the Redis-Server.")
            timers.startSingleTimer("CheckCacheService", CheckCacheService, 5.seconds)

        case AppStart(withOntologies, requiresSipi) => appStart(withOntologies, requiresSipi)
        case AppStop() => appStop()

        case responderMessage: KnoraRequestV1 => responderManager forward responderMessage
        case responderMessage: KnoraRequestV2 => responderManager forward responderMessage
        case responderMessage: KnoraRequestADM => responderManager forward responderMessage
        case storeMessage: StoreRequest => storeManager forward storeMessage

        case other => throw UnexpectedMessageException(s"ApplicationActor received an unexpected message $other of type ${other.getClass.getCanonicalName}")
    }


    /**
      * All routes composed together and CORS activated.
      */
    private val apiRoutes: Route = logDuration {
        addServerHeader {
            CORS(
                new HealthRoute(routeData).knoraApiPath ~
                  new VersionRoute(routeData).knoraApiPath ~
                  new RejectingRoute(routeData).knoraApiPath ~
                  new ClientApiRoute(routeData).knoraApiPath ~
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
                  new SwaggerApiDocsRoute(routeData).knoraApiPath,
                settings
            )
        }
    }

    // #start-api-server
    /**
      * Starts the Knora-API server.
      */
    def appStart(skipLoadingOfOntologies: Boolean, requiresSipi: Boolean): Unit = {

        val bindingFuture: Future[Http.ServerBinding] = Http()
          .bindAndHandle(
            Route.handlerFlow(apiRoutes),
            settings.internalKnoraApiHost,
            settings.internalKnoraApiPort
        )

        bindingFuture onComplete {
            case Success(_) => {

                if (settings.prometheusEndpoint) {
                    // Load Kamon monitoring
                    Kamon.loadModules()
                }

                // Kick of startup procedure.
                self ! InitStartUp(skipLoadingOfOntologies, requiresSipi)
            }
            case Failure(ex) => {
                logger.error(
                    "Failed to bind to {}:{}! - {}",
                    settings.internalKnoraApiHost,
                    settings.internalKnoraApiPort,
                    ex.getMessage
                )
                appStop()
            }
        }
    }
    // #start-api-server

    /**
      * Stops Knora-API.
      */
    def appStop(): Unit = {
        logger.info("ApplicationActor - initiating shutdown ...")
        context.stop(self)
    }

    override def postStop(): Unit = {
        super.postStop()
        logger.info("ApplicationActor - shutdown in progress, initiating post stop cleanup. Bye!")

        if (settings.prometheusEndpoint) {
            // Stop Kamon monitoring
            Kamon.stopModules()
        }

        CacheUtil.removeAllCaches()

        Http().shutdownAllConnectionPools() andThen { case _ => system.terminate() }
    }

    /**
      * Prints the welcome message
      */
    private def printBanner(): Unit = {

        var msg =
            """
              | _   __                              ___  ______ _____
              || | / /                             / _ \ | ___ \_   _|
              || |/ / _ __   ___  _ __ __ _ ______/ /_\ \| |_/ / | |
              ||    \| '_ \ / _ \| '__/ _` |______|  _  ||  __/  | |
              || |\  \ | | | (_) | | | (_| |      | | | || |    _| |_
              |\_| \_/_| |_|\___/|_|  \__,_|      \_| |_/\_|    \___/
            """.stripMargin


        msg += "\n"
        msg += s"Knora API Server started at http://${settings.internalKnoraApiHost}:${settings.internalKnoraApiPort}\n"
        msg += "----------------------------------------------------------------\n"

        if (allowReloadOverHTTPState | settings.allowReloadOverHTTP) {
            msg += "WARNING: Resetting Triplestore Content over HTTP is turned ON.\n"
            msg += "----------------------------------------------------------------\n"
        }

        // which repository are we using
        msg += s"DB-Name: ${settings.triplestoreDatabaseName}\n"
        msg += s"DB-Type: ${settings.triplestoreType}\n"
        msg += s"DB Server: ${settings.triplestoreHost}, DB Port: ${settings.triplestorePort}\n"


        if (printExtendedConfigState | settings.printExtendedConfig) {

            // msg += s"DB User: ${settings.triplestoreUsername}\n"
            // msg += s"DB Password: ${settings.triplestorePassword}\n"

            msg += s"Swagger Json: ${settings.externalKnoraApiBaseUrl}/api-docs/swagger.json\n"
            msg += s"Webapi internal URL: ${settings.internalKnoraApiBaseUrl}\n"
            msg += s"Webapi external URL: ${settings.externalKnoraApiBaseUrl}\n"
            msg += s"Sipi internal URL: ${settings.internalSipiBaseUrl}\n"
            msg += s"Sipi external URL: ${settings.externalSipiBaseUrl}\n"
        }

        msg += "================================================================\n"

        println(msg)
    }
}
