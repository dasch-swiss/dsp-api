package org.knora.webapi.app

import akka.actor.{Actor, ActorLogging, ActorSelection, Timers}
import org.knora.webapi._
import org.knora.webapi.messages.app.appmessages.AppState.AppState
import org.knora.webapi.messages.app.appmessages._
import org.knora.webapi.messages.store.triplestoremessages.{CheckRepositoryRequest, CheckRepositoryResponse, RepositoryStatus}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_PATH
import org.knora.webapi.store.StoreManagerActorPath
import org.knora.webapi.util.CacheUtil

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * This actor holds the current state of the application and is responsible for coordination of the startup sequence.
  */
class ApplicationStateActor extends Actor with Timers with ActorLogging {

    log.debug("entered the ApplicationStateActor constructor")

    val executionContext: ExecutionContext = context.system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

    /**
      * A reference to the Knora API responder manager.
      */
    protected val responderManager: ActorSelection = context.actorSelection(RESPONDER_MANAGER_ACTOR_PATH)

    /**
      * A reference to the store manager.
      */
    protected val storeManager: ActorSelection = context.actorSelection(StoreManagerActorPath)


    // the prometheus, zipkin, jaeger, datadog, and printConfig flags can be set via application.conf and via command line parameter
    val settings: SettingsImpl = Settings(context.system)

    private var appState: AppState = AppState.Stopped
    private var allowReloadOverHTTPState = false
    private var prometheusReporterState = false
    private var zipkinReporterState = false
    private var jaegerReporterState = false
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
                case AppState.RepositoryReady =>  self ! SetAppState(AppState.CreatingCaches)
                case AppState.CreatingCaches => self ! CreateCaches()
                case AppState.CachesReady => self ! SetAppState(AppState.LoadingOntologies)
                case AppState.LoadingOntologies if skipOntologies => self ! SetAppState(AppState.OntologiesReady)  // skipping loading of ontologies
                case AppState.LoadingOntologies if !skipOntologies => self ! LoadOntologies() // load ontologies
                case AppState.OntologiesReady => self ! SetAppState(AppState.Running)
                case AppState.Running => printWelcomeMsg()
                case AppState.MaintenanceMode => // do nothing
                case value => throw UnsupportedValueException(s"The value: $value is not supported.")
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
            sender ! allowReloadOverHTTPState
        }

        case SetPrometheusReporterState(value) => {
            log.debug("ApplicationStateActor - SetPrometheusReporterState - value: {}", value)
            prometheusReporterState = value
        }
        case GetPrometheusReporterState() => {
            log.debug("ApplicationStateActor - GetPrometheusReporterState - value: {}", prometheusReporterState)
            sender ! (prometheusReporterState | settings.prometheusReporter)
        }

        case SetZipkinReporterState(value) => {
            log.debug("ApplicationStateActor - SetZipkinReporterState - value: {}", value)
            zipkinReporterState = value
        }
        case GetZipkinReporterState() => {
            log.debug("ApplicationStateActor - GetZipkinReporterState - value: {}", zipkinReporterState)
            sender ! (zipkinReporterState | settings.zipkinReporter)
        }
        case SetJaegerReporterState(value) => {
            log.debug("ApplicationStateActor - SetJaegerReporterState - value: {}", value)
            jaegerReporterState = value
        }
        case GetJaegerReporterState() => {
            log.debug("ApplicationStateActor - GetJaegerReporterState - value: {}", jaegerReporterState)
            sender ! (jaegerReporterState | settings.jaegerReporter)
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

        /* load ontologies request */
        case LoadOntologies() => {
            responderManager !  LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser)
        }

        /* load ontologies response */
        case SuccessResponseV2(msg) => {
            self ! SetAppState(AppState.OntologiesReady)
        }
    }

    override def postStop(): Unit = {
        super.postStop()
        log.debug("ApplicationStateActor - postStop called")
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

        if (allowReloadOverHTTPState) {
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
