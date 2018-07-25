/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi

import akka.actor._
import akka.dispatch.MessageDispatcher
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import kamon.Kamon
import kamon.jaeger.JaegerReporter
import kamon.prometheus.PrometheusReporter
import kamon.zipkin.ZipkinReporter
import kamon.datadog.DatadogAgentReporter
import org.knora.webapi.app._
import org.knora.webapi.http.CORSSupport.CORS
import org.knora.webapi.messages.app.appmessages.AppState.AppState
import org.knora.webapi.messages.app.appmessages._
import org.knora.webapi.messages.store.triplestoremessages.{CheckRepositoryRequest, CheckRepositoryResponse, RepositoryStatus}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.responders._
import org.knora.webapi.routing.admin._
import org.knora.webapi.routing.v1._
import org.knora.webapi.routing.v2._
import org.knora.webapi.routing.{HealthRoute, RejectingRoute, SwaggerApiDocsRoute}
import org.knora.webapi.store._
import org.knora.webapi.util.{CacheUtil, StringFormatter}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.languageFeature.postfixOps
import scala.util.{Failure, Success}

/**
  * Knora Core abstraction.
  */
trait Core {
    implicit val system: ActorSystem

    implicit val settings: SettingsImpl

    implicit val log: LoggingAdapter

    implicit val materializer: ActorMaterializer

    implicit val executionContext: ExecutionContext
}

/**
  * The applications actor system.
  */
trait LiveCore extends Core {

    /**
      * The application's actor system.
      */
    implicit lazy val system: ActorSystem = ActorSystem("webapi")

    /**
      * The application's configuration.
      */
    implicit lazy val settings: SettingsImpl = Settings(system)

    /**
      * Provide logging
      */
    implicit lazy val log: LoggingAdapter = akka.event.Logging(system, "KnoraService")

    /**
      * Provides the actor materializer (akka-http)
      */
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    /**
      * Provides the default global execution context
      */
    implicit val executionContext: ExecutionContext = system.dispatchers.defaultGlobalDispatcher
}

/**
  * Provides methods for starting and stopping Knora from within another application. This is where the actor system
  * along with the three main supervisor actors is started. All further actors are started and supervised by those
  * three actors.
  */
trait KnoraService {
    this: Core =>

    // Initialise StringFormatter with the system settings. This must happen before any responders are constructed.
    StringFormatter.init(settings)

    import scala.language.postfixOps

    /**
      * The actor used for storing the application application wide variables in a thread safe manner.
      */
    protected val applicationStateActor: ActorRef = system.actorOf(Props(new ApplicationStateActor), name = APPLICATION_STATE_ACTOR_NAME)

    /**
      * The supervisor actor that forwards messages to responder actors to handle API requests.
      */
    protected val responderManager: ActorRef = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)

    /**
      * The supervisor actor that forwards messages to actors that deal with persistent storage.
      */
    protected val storeManager: ActorRef = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    /**
      * Timeout definition
      */
    implicit private val timeout: Timeout = settings.defaultTimeout

    /**
      * A user representing the Knora API server, used for initialisation on startup.
      */
    private val systemUser = KnoraSystemInstances.Users.SystemUser

    /**
      * All routes composed together and CORS activated.
      */
    private val apiRoutes: Route = CORS(
        new HealthRoute(system, settings).knoraApiPath ~
        new RejectingRoute(system, settings).knoraApiPath() ~
            ResourcesRouteV1.knoraApiPath(system, settings, log) ~
            ValuesRouteV1.knoraApiPath(system, settings, log) ~
            SipiRouteV1.knoraApiPath(system, settings, log) ~
            StandoffRouteV1.knoraApiPath(system, settings, log) ~
            ListsRouteV1.knoraApiPath(system, settings, log) ~
            ResourceTypesRouteV1.knoraApiPath(system, settings, log) ~
            SearchRouteV1.knoraApiPath(system, settings, log) ~
            AuthenticationRouteV1.knoraApiPath(system, settings, log) ~
            AssetsRouteV1.knoraApiPath(system, settings, log) ~
            CkanRouteV1.knoraApiPath(system, settings, log) ~
            UsersRouteV1.knoraApiPath(system, settings, log) ~
            ProjectsRouteV1.knoraApiPath(system, settings, log) ~
            OntologiesRouteV2.knoraApiPath(system, settings, log) ~
            SearchRouteV2.knoraApiPath(system, settings, log) ~
            ResourcesRouteV2.knoraApiPath(system, settings, log) ~
            StandoffRouteV2.knoraApiPath(system, settings, log) ~
            ListsRouteV2.knoraApiPath(system, settings, log) ~
            AuthenticationRouteV2.knoraApiPath(system, settings, log) ~
            new GroupsRouteADM(system, settings, log).knoraApiPath ~
            new ListsRouteADM(system, settings, log).knoraApiPath ~
            new PermissionsRouteADM(system, settings, log).knoraApiPath ~
            new ProjectsRouteADM(system, settings, log).knoraApiPath ~
            new StoreRouteADM(system, settings, log).knoraApiPath ~
            new UsersRouteADM(system, settings, log).knoraApiPath ~
            new SwaggerApiDocsRoute(system, settings, log).knoraApiPath
        , settings,
        log
    )

    /**
      * Starts the Knora API server.
      */
    def startService(startupTasks: Boolean): Unit = {

        val bindingFuture: Future[Http.ServerBinding] = Http().bindAndHandle(Route.handlerFlow(apiRoutes), settings.internalKnoraApiHost, settings.internalKnoraApiPort)

        bindingFuture onComplete {
            case Success(_) => {

                startReporters()

                if (startupTasks) {
                    // Kick of startup tasks. This method returns when Running state is reached.
                    println("KnoraService - startupTaskRunner startingo")
                    startupTaskRunner()
                    println("KnoraService - startupTaskRunner finished")
                } else {
                    // fast track startup
                    CacheUtil.createCaches(settings.caches)
                    applicationStateActor ! SetAppState(AppState.Running)
                }
            }
            case Failure(ex) => {
                log.error("Failed to bind to {}:{}! - {}", settings.internalKnoraApiHost, settings.internalKnoraApiPort, ex.getMessage)
                stopService()
            }
        }
    }

    /**
      * Stops Knora.
      */
    def stopService(): Unit = {
        CacheUtil.removeAllCaches()
        Kamon.stopAllReporters()
        system.terminate()
    }

    /**
      * Returns only when the application state actor is ready.
      */
    def applicationStateActorReady(): Unit = {

        implicit val blockingDispatcher: MessageDispatcher = system.dispatchers.lookup("my-blocking-dispatcher")
        implicit val executor: ExecutionContext = blockingDispatcher

        try {
            Await.result(applicationStateActor ? ActorReady(), 2.second).asInstanceOf[ActorReadyAck]
            log.info("KnoraService - applicationStateActorReady")
        } catch {
            case e: AskTimeoutException => {
                // if we are here, then the ask timed out, so we need to try again until the actor is ready
                applicationStateActorReady()
            }
        }
    }

    /**
      * Returns only when the application state is 'Running'.
      */
    def applicationStateRunning(): Unit = {

        implicit val blockingDispatcher: MessageDispatcher = system.dispatchers.lookup("my-blocking-dispatcher")
        implicit val executor: ExecutionContext = blockingDispatcher

        val state: AppState = Await.result(applicationStateActor ? GetAppState(), 2.second).asInstanceOf[AppState]

        if (state != AppState.Running) {
            // not in running state so call startup checks again
            // we should wait a bit before we call ourselves again
            Await.result(blockingFuture(), 2.second)
            applicationStateRunning()
        }
    }

    /**
      * Triggers the startupChecks periodically and only returns when AppState.Running is reached.
      */
    private def startupTaskRunner(): Unit = {

        implicit val blockingDispatcher: MessageDispatcher = system.dispatchers.lookup("my-blocking-dispatcher")
        implicit val executor: ExecutionContext = blockingDispatcher

        val state: AppState = Await.result(applicationStateActor ? GetAppState(), 2.second).asInstanceOf[AppState]

        state match {
            case value if value == AppState.Running => {
                printWelcomeMsg()
                printConfig()
            }
            case value => {
                // not in running state so call startup checks again
                startupChecks()

                // we should wait a bit before we call ourselves again
                Await.result(blockingFuture(), 2.second)
                startupTaskRunner()
            }
        }
    }

    /**
      * A blocking future running on the blocking dispatcher.
      */
    private def blockingFuture(): Future[Unit] = {

        implicit val blockingDispatcher: MessageDispatcher = system.dispatchers.lookup("my-blocking-dispatcher")
        implicit val executor: ExecutionContext = blockingDispatcher

        val delay: Long = 1.second.toMillis

        Future {
            // uses the good "blocking dispatcher" that we configured,
            // instead of the default dispatcher to isolate the blocking.
            Thread.sleep(delay)
            Future.successful(())
        }
    }

    /**
      * Executes startup tasks in the correct order and state.
      */
    private def startupChecks(): Unit = {

        implicit val blockingDispatcher: MessageDispatcher = system.dispatchers.lookup("my-blocking-dispatcher")
        implicit val executor: ExecutionContext = blockingDispatcher

        val state = Await.result(applicationStateActor ? GetAppState(), 2.second).asInstanceOf[AppState]

        state match {
            case AppState.Stopped => applicationStateActor ! SetAppState(AppState.StartingUp)
            case AppState.StartingUp => {
                log.info(s"KnoraService - Startup State: {}", AppState.StartingUp)
                checkRepository()
            }
            case AppState.WaitingForRepository => checkRepository() // check DB again
            case AppState.RepositoryReady => createCaches() // create caches
            case AppState.CachesReady => loadOntologies() // load ontologies
            case AppState.OntologiesReady => {
                // everything is up and running so set state to Running
                applicationStateActor ! SetAppState(AppState.Running)
                log.info(s"KnoraService - Startup State: {}", AppState.Running)
            }
            case value => throw UnsupportedValueException(s"The value: $value is not supported.")
        }
    }

    /**
      * Checks if repository is running and initialized
      */
    private def checkRepository(): Unit = {

        implicit val blockingDispatcher: MessageDispatcher = system.dispatchers.lookup("my-blocking-dispatcher")
        implicit val executor: ExecutionContext = blockingDispatcher

        val storeManagerResult = Await.result(storeManager ? CheckRepositoryRequest(), 1.seconds).asInstanceOf[CheckRepositoryResponse]
        if (storeManagerResult.repositoryStatus == RepositoryStatus.ServiceAvailable) {
            applicationStateActor ! SetAppState(AppState.RepositoryReady)
            log.info(s"KnoraService - Startup State: {}", AppState.RepositoryReady)
        } else if (storeManagerResult.repositoryStatus == RepositoryStatus.NotInitialized) {
            log.info(s"KnoraService - checkRepository - status: {}: {}", storeManagerResult.repositoryStatus, storeManagerResult.msg)
            log.info("Please initialize repository. Will exit now.")
            stopService()
        } else {
            applicationStateActor ! SetAppState(AppState.WaitingForRepository)
            log.info(s"KnoraService - Startup State: {}", AppState.WaitingForRepository)

        }
    }

    /**
      * Creates caches
      */
    private def createCaches(): Unit = {
        applicationStateActor ! SetAppState(AppState.CreatingCaches)
        CacheUtil.createCaches(settings.caches)
        applicationStateActor ! SetAppState(AppState.CachesReady)
        log.info(s"KnoraService - Startup State: {}", AppState.CachesReady)
    }

    /**
      * Loads ontologies
      */
    private def loadOntologies(): Unit = {

        implicit val blockingDispatcher: MessageDispatcher = system.dispatchers.lookup("my-blocking-dispatcher")
        implicit val executor: ExecutionContext = blockingDispatcher

        // load ontologies and set OntologiesReady state
        applicationStateActor ! SetAppState(AppState.LoadingOntologies)
        val ontologyCacheFuture = responderManager ? LoadOntologiesRequestV2(systemUser)

        Await.result(ontologyCacheFuture, timeout.duration).asInstanceOf[SuccessResponseV2]
        applicationStateActor ! SetAppState(AppState.OntologiesReady)
        log.info(s"KnoraService - Startup State: {}", AppState.OntologiesReady)
    }

    /**
      * Prints the welcome message
      */
    private def printWelcomeMsg(): Unit = {

        implicit val blockingDispatcher: MessageDispatcher = system.dispatchers.lookup("my-blocking-dispatcher")
        implicit val executor: ExecutionContext = blockingDispatcher

        println("")
        println("----------------------------------------------------------------")
        println(s"Knora API Server started at http://${settings.internalKnoraApiHost}:${settings.internalKnoraApiPort}")
        println("----------------------------------------------------------------")

        // get allowReloadOverHTTP value from application state actor
        val allowReloadOverHTTP = Await.result(applicationStateActor ? GetAllowReloadOverHTTPState(), 1.second).asInstanceOf[Boolean]

        if (allowReloadOverHTTP) {
            println("WARNING: Resetting Triplestore Content over HTTP is turned ON.")
        }
    }

    /**
      * Prints the configuration if the print config flag is set.
      */
    private def printConfig(): Unit = {

        implicit val blockingDispatcher: MessageDispatcher = system.dispatchers.lookup("my-blocking-dispatcher")
        implicit val executor: ExecutionContext = blockingDispatcher

        val printConfig = Await.result(applicationStateActor ? GetPrintConfigState(), 1.second).asInstanceOf[Boolean]
        if (printConfig) {
            println("================================================================")
            println("Server Configuration:")

            // which repository are we using
            println(s"DB Server: ${settings.triplestoreHost}, DB Port: ${settings.triplestorePort}")
            println(s"Repository: ${settings.triplestoreDatabaseName}")
            println(s"DB User: ${settings.triplestoreUsername}")
            println(s"DB Password: ${settings.triplestorePassword}")

            println(s"Swagger Json: ${settings.externalKnoraApiBaseUrl}/api-docs/swagger.json")
            println(s"Webapi internal URL: ${settings.internalKnoraApiBaseUrl}")
            println(s"Webapi external URL: ${settings.externalKnoraApiBaseUrl}")
            println(s"Sipi internal URL: ${settings.internalSipiBaseUrl}")
            println(s"Sipi external URL: ${settings.externalSipiBaseUrl}")
            println("================================================================")
            println("")
        }

    }

    /**
      * Start the different reporters if defined. Reporters are the connection points between kamon (the collector) and
      * the application which we will use to look at the collected data.
      */
    private def startReporters(): Unit = {

        implicit val blockingDispatcher: MessageDispatcher = system.dispatchers.lookup("my-blocking-dispatcher")
        implicit val executor: ExecutionContext = blockingDispatcher

        val prometheusReporter = Await.result(applicationStateActor ? GetPrometheusReporterState(), 1.second).asInstanceOf[Boolean]
        if (prometheusReporter) {
            Kamon.addReporter(new PrometheusReporter()) // metrics
        }

        val zipkinReporter = Await.result(applicationStateActor ? GetZipkinReporterState(), 1.second).asInstanceOf[Boolean]
        if (zipkinReporter) {
            Kamon.addReporter(new ZipkinReporter()) // tracing
        }

        val jaegerReporter = Await.result(applicationStateActor ? GetJaegerReporterState(), 1.second).asInstanceOf[Boolean]
        if (jaegerReporter) {
            Kamon.addReporter(new JaegerReporter()) // tracing
        }

        val datadogReporter = Await.result(applicationStateActor ? GetDataDogReporterState(), 1.second).asInstanceOf[Boolean]
        if (datadogReporter) {
            Kamon.addReporter(new DatadogAgentReporter()) // tracing
        }
    }
}
