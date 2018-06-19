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
import scala.concurrent.{Await, ExecutionContextExecutor}

/**
  * Knora Core abstraction.
  */
trait Core {
    implicit val system: ActorSystem

    implicit val settings: SettingsImpl

    implicit val log: LoggingAdapter
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

    implicit val materializer: ActorMaterializer = ActorMaterializer()

    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    /**
      * The actor used for storing the application application wide variables in a thread safe maner.
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
      * Timeout definition (need to be high enough to allow reloading of data so that checkActorSystem doesn't timeout)
      */
    implicit private val timeout: Timeout = settings.defaultRestoreTimeout

    /**
      * A user representing the Knora API server, used for initialisation on startup.
      */
    private val systemUser = KnoraSystemInstances.Users.SystemUser

    /**
      * The scheduler running at startup up, making sure that the application
      * starts-up in a ordered manner.
      */
    private val startupScheduler: Cancellable = system.scheduler.schedule(0.milli, 500.millis)(startupChecks())

    /**
      * All routes composed together and CORS activated.
      */
    private val apiRoutes: Route = CORS(
        new HealthRoute(system, settings, log, applicationStateActor).knoraApiPath ~
        new RejectingRoute(system, settings, log, applicationStateActor).knoraApiPath() ~
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
      * Sends messages to all supervisor actors in a blocking manner, checking if they are all ready.
      */
    def checkActorSystem(): Unit = {

        val applicationStateActorResult = Await.result(applicationStateActor ? CheckRepositoryRequest(), 5.seconds).asInstanceOf[CheckRepositoryResponse]
        log.info(s"ApplicationStateActor ready: $applicationStateActorResult")

        // TODO: Check if ResponderManager is ready
        log.info(s"ResponderManager ready: - ")

        // TODO: Check if Sipi is also ready/accessible
        val storeManagerResult = Await.result(storeManager ? CheckRepositoryRequest(), 5.seconds).asInstanceOf[CheckRepositoryResponse]
        log.info(s"StoreManager ready: $storeManagerResult")
        log.info(s"ActorSystem ${system.name} started")
    }

    /**
      * Starts the Knora API server.
      */
    def startService(): Unit = {

        printConfig()

        startReporters()

        Http().bindAndHandle(Route.handlerFlow(apiRoutes), settings.internalKnoraApiHost, settings.internalKnoraApiPort)

        printWelcomeMsg()

        // kick of startup tasks
        applicationStateActor ! SetAppState(AppState.StartingUp)
    }

    /**
      * Stops Knora.
      */
    def stopService(): Unit = {
        system.terminate()
        CacheUtil.removeAllCaches()
        //Kamon.shutdown()
    }

    /**
      * Executes startup tasks in the correct order and state.
      */
    private def startupChecks(): Unit = {

        val state = Await.result(applicationStateActor ? GetAppState(), 1.second).asInstanceOf[AppState]

        state match {
            case AppState.Stopped => {} // nothing to do
            case AppState.StartingUp => checkRepository() // check DB
            case AppState.RepositoryReady => createCaches() // create caches
            case AppState.CachesReady => loadOntologies() // load ontologies
            case AppState.OntologiesReady => {

                // everything is up and running so set state to Running and kill scheduler
                applicationStateActor ! SetAppState(AppState.Running)
                startupScheduler.cancel()
            }
        }
    }

    /**
      * Checks if repository is running and initialized
      */
    private def checkRepository(): Unit = {
        val storeManagerResult = Await.result(storeManager ? CheckRepositoryRequest(), 5.seconds).asInstanceOf[CheckRepositoryResponse]
        if (storeManagerResult.repositoryStatus == RepositoryStatus.ServiceAvailable) {
            applicationStateActor ! SetAppState(AppState.RepositoryReady)
        } else {
            applicationStateActor ! SetAppState(AppState.WaitingForRepository)
            log.info(s"${storeManagerResult.repositoryStatus}: ${storeManagerResult.msg}")
        }
    }

    /**
      * Creates caches
      */
    private def createCaches(): Unit = {
        applicationStateActor ! SetAppState(AppState.CreatingCaches)
        CacheUtil.createCaches(settings.caches)
        applicationStateActor ! SetAppState(AppState.CachesReady)
    }

    /**
      * Loads ontologies
      */
    private def loadOntologies(): Unit = {
        // load ontologies and set OntologiesReady state
        applicationStateActor ! SetAppState(AppState.LoadingOntologies)
        val ontologyCacheFuture = responderManager ? LoadOntologiesRequestV2(systemUser)
        Await.result(ontologyCacheFuture, timeout.duration).asInstanceOf[SuccessResponseV2]
        applicationStateActor ! SetAppState(AppState.OntologiesReady)
    }

    /**
      * Prints the welcome message
      */
    private def printWelcomeMsg(): Unit = {
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
    }


}
