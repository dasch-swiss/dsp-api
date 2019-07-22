package org.knora.webapi.app

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import kamon.Kamon
import org.knora.webapi._
import org.knora.webapi.http.CORSSupport.CORS
import org.knora.webapi.http.ServerVersion.addServerHeader
import org.knora.webapi.messages.admin.responder.KnoraRequestADM
import org.knora.webapi.messages.app.appmessages.AppState.AppState
import org.knora.webapi.messages.app.appmessages._
import org.knora.webapi.messages.store.StoreRequest
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

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class Start()
case class Stop()

trait Managers {
    val responderManager: ActorRef
    val storeManager: ActorRef
}

trait LiveManagers extends Managers {
    this: Actor =>

    // #supervisors
    /**
      * The supervisor actor that forwards messages to actors that deal with persistent storage.
      */
    lazy val storeManager: ActorRef = context.actorOf(Props(new StoreManager with LiveActorMaker).withDispatcher(KnoraDispatchers.KnoraActorDispatcher), name = StoreManagerActorName)

    /**
      * The supervisor actor that forwards messages to responder actors to handle API requests.
      */
    lazy val responderManager: ActorRef = context.actorOf(Props(new ResponderManager(self, storeManager) with LiveActorMaker).withDispatcher(KnoraDispatchers.KnoraActorDispatcher), name = RESPONDER_MANAGER_ACTOR_NAME)
    // #supervisors
}

/**
  * This is the first actor in the application. It accepts messages for starting
  * and stopping the Knora-API This is where the three main supervisor actors are
  * started. Further, this actor holds the current state of the application and
  * is responsible for coordination of the startup and shutdown sequence.
  */
class ApplicationActor extends Actor with AroundDirectives with Timers with ActorLogging {
    this: Managers =>

    log.debug("entered the ApplicationManager constructor")

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


    private var appState: AppState = AppState.Stopped
    private var allowReloadOverHTTPState = false
    private var printConfigState = false
    private var skipOntologies = true

    def receive: PartialFunction[Any, Unit] = {

        /* Entry point for startup */
        case InitStartUp(skipLoadingOfOntologies) => {
            log.info("InitStartUp ... please wait.")

            if (appState == AppState.Stopped) {
                skipOntologies = skipLoadingOfOntologies
                self ! SetAppState(AppState.StartingUp)
            }
        }

        /* EACH app state change goes through here */
        case SetAppState(value: AppState) => {

            appState = value

            log.info("appStateChanged - to state: {}", value)

            value match {
                case AppState.Stopped => // do nothing
                case AppState.StartingUp => self ! SetAppState(AppState.WaitingForRepository)
                case AppState.WaitingForRepository => self ! CheckRepository() // check DB
                case AppState.RepositoryReady => self ! SetAppState(AppState.CreatingCaches)
                case AppState.CreatingCaches => self ! CreateCaches()
                case AppState.CachesReady => self ! SetAppState(AppState.UpdatingSearchIndex)
                case AppState.UpdatingSearchIndex => self ! UpdateSearchIndex()
                case AppState.SearchIndexReady => self ! SetAppState(AppState.LoadingOntologies)
                case AppState.LoadingOntologies if skipOntologies => self ! SetAppState(AppState.OntologiesReady) // skipping loading of ontologies
                case AppState.LoadingOntologies if !skipOntologies => self ! LoadOntologies() // load ontologies
                case AppState.OntologiesReady => self ! SetAppState(AppState.Running)
                case AppState.Running => printWelcomeMsg()
                case AppState.MaintenanceMode => // do nothing
                case other => throw UnsupportedValueException(s"The value: $other is not supported.")
            }
        }
        case GetAppState() => {
            log.debug("ApplicationStateActor - GetAppState - value: {}", appState)
            sender ! appState
        }

        case ActorReady() => {
            sender ! ActorReadyAck()
        }

        case SetAllowReloadOverHTTPState(value) => {
            log.debug("ApplicationStateActor - SetAllowReloadOverHTTPState - value: {}", value)
            allowReloadOverHTTPState = value
        }
        case GetAllowReloadOverHTTPState() => {
            log.debug("ApplicationStateActor - GetAllowReloadOverHTTPState - value: {}", allowReloadOverHTTPState)
            sender ! (allowReloadOverHTTPState | settings.allowReloadOverHTTP)
        }

        case SetPrintConfigExtendedState(value) => {
            log.debug("ApplicationStateActor - SetPrintConfigExtendedState - value: {}", value)
            printConfigState = value
        }
        case GetPrintConfigExtendedState() => {
            log.debug("ApplicationStateActor - GetPrintConfigExtendedState - value: {}", printConfigState)
            sender ! (printConfigState | settings.printExtendedConfig)
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
                    log.info(s"checkRepository - status: {}, message: {}", status, message)
                    log.info("Please initialize repository.")
                    timers.startSingleTimer("CheckRepository", CheckRepository(), 5.seconds)
                case RepositoryStatus.ServiceUnavailable =>
                    log.info(s"checkRepository - status: {}, message: {}", status, message)
                    log.info("Please start repository.")
                    timers.startSingleTimer("CheckRepository", CheckRepository(), 5.seconds)
            }
        }

        /* create caches request */
        case CreateCaches() => {
            CacheUtil.createCaches(settings.caches)
            self ! SetAppState(AppState.CachesReady)
        }

        case UpdateSearchIndex() => {
            storeManager ! SearchIndexUpdateRequest()
        }

        case SparqlUpdateResponse() => {
            self ! SetAppState(AppState.SearchIndexReady)
        }

        /* load ontologies request */
        case LoadOntologies() => {
            responderManager ! LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser)
        }

        /* load ontologies response */
        case SuccessResponseV2(_) => {
            self ! SetAppState(AppState.OntologiesReady)
        }

        case AppStart(value) => appStart(value)
        case AppStop() => appStop()

        case responderMessage: KnoraRequestV1 => responderManager forward responderMessage
        case responderMessage: KnoraRequestV2 => responderManager forward responderMessage
        case responderMessage: KnoraRequestADM => responderManager forward responderMessage
        case storeMessage: StoreRequest => storeManager forward storeMessage

        case other => throw UnexpectedMessageException(s"ApplicationActor received an unexpected message $other of type ${other.getClass.getCanonicalName}")
    }

    override def postStop(): Unit = {
        super.postStop()
        log.debug("ApplicationManager - postStop called")
    }

    /**
      * All routes composed together and CORS activated.
      */
    private val apiRoutes: Route = logDuration {
        addServerHeader {
            CORS(
                new HealthRoute(routeData).knoraApiPath ~
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

    // #startService
    /**
      * Starts the Knora-API server.
      */
    def appStart(skipLoadingOfOntologies: Boolean): Unit = {

        val bindingFuture: Future[Http.ServerBinding] = Http().bindAndHandle(Route.handlerFlow(apiRoutes), settings.internalKnoraApiHost, settings.internalKnoraApiPort)

        bindingFuture onComplete {
            case Success(_) => {

                if (settings.prometheusEndpoint) {
                    // Load Kamon monitoring
                    Kamon.loadModules()
                }

                // Kick of startup procedure.
                self ! InitStartUp(skipLoadingOfOntologies)
            }
            case Failure(ex) => {
                log.error("Failed to bind to {}:{}! - {}", settings.internalKnoraApiHost, settings.internalKnoraApiPort, ex.getMessage)
                appStop()
            }
        }
    }
    // #startService

    /**
      * Stops Knora-API.
      */
    def appStop(): Unit = {
        log.info("KnoraService - Shutting down.")

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
    private def printWelcomeMsg(): Unit = {

        var msg = ""

        msg += "\n"
        msg += "================================================================\n"
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


        if (printConfigState) {

            msg += s"DB User: ${settings.triplestoreUsername}\n"
            msg += s"DB Password: ${settings.triplestorePassword}\n"

            msg += s"Swagger Json: ${settings.externalKnoraApiBaseUrl}/api-docs/swagger.json\n"
            msg += s"Webapi internal URL: ${settings.internalKnoraApiBaseUrl}\n"
            msg += s"Webapi external URL: ${settings.externalKnoraApiBaseUrl}\n"
            msg += s"Sipi internal URL: ${settings.internalSipiBaseUrl}\n"
            msg += s"Sipi external URL: ${settings.externalSipiBaseUrl}\n"
        }

        msg += "================================================================\n"

        log.info(msg)
    }
}
