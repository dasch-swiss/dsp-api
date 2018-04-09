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
import org.knora.webapi.messages.app.appmessages._
import org.knora.webapi.messages.store.triplestoremessages.{Initialized, InitializedResponse, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.responders._
import org.knora.webapi.routing.RejectingRoute
import org.knora.webapi.routing.admin._
import org.knora.webapi.routing.v1._
import org.knora.webapi.routing.v2._
import org.knora.webapi.store._
import org.knora.webapi.store.triplestore.RdfDataObjectFactory
import org.knora.webapi.util.{CacheUtil, StringFormatter}

import scala.collection.JavaConverters._
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
      * All routes composed together and CORS activated.
      */
    private val apiRoutes: Route = CORS(
        RejectingRoute.knoraApiPath(system, settings, log) ~
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
            AuthenticationRouteV2.knoraApiPath(system, settings, log) ~
            GroupsRouteADM.knoraApiPath(system, settings, log) ~
            ListsRouteADM.knoraApiPath(system, settings, log) ~
            PermissionsRouteADM.knoraApiPath(system, settings, log) ~
            ProjectsRouteADM.knoraApiPath(system, settings, log) ~
            StoreRouteADM.knoraApiPath(system, settings, log) ~
            UsersRouteADM.knoraApiPath(system, settings, log),
        settings,
        log
    )

    /**
      * Sends messages to all supervisor actors in a blocking manner, checking if they are all ready.
      */
    def checkActorSystem(): Unit = {

        val applicationStateActorResult = Await.result(applicationStateActor ? Initialized(), 5.seconds).asInstanceOf[InitializedResponse]
        log.info(s"ApplicationStateActor ready: $applicationStateActorResult")

        // TODO: Check if ResponderManager is ready
        log.info(s"ResponderManager ready: - ")

        // TODO: Check if Sipi is also ready/accessible
        val storeManagerResult = Await.result(storeManager ? Initialized(), 5.seconds).asInstanceOf[InitializedResponse]
        log.info(s"StoreManager ready: $storeManagerResult")
        log.info(s"ActorSystem ${system.name} started")
    }

    /**
      * Starts the Knora API server.
      */
    def startService(): Unit = {

        implicit val materializer: ActorMaterializer = ActorMaterializer()

        // needed for startup flags and the future map/flatmap in the end
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher

        CacheUtil.createCaches(settings.caches)

        // which repository are we using
        println(s"DB Server: ${settings.triplestoreHost}, DB Port: ${settings.triplestorePort}")
        println(s"Repository: ${settings.triplestoreDatabaseName}")
        println(s"DB User: ${settings.triplestoreUsername}")
        println(s"DB Password: ${settings.triplestorePassword}")

        // get loadDemoData value from application state actor
        val loadDemoData = Await.result(applicationStateActor ? GetLoadDemoDataState(), 1.second).asInstanceOf[Boolean]

        if (loadDemoData) {
            println("Start loading of demo data ...")
            val configList = settings.tripleStoreConfig.getConfigList("rdf-data")
            val rdfDataObjectList = configList.asScala.map {
                config => RdfDataObjectFactory(config)
            }
            val resultFuture = storeManager ? ResetTriplestoreContent(rdfDataObjectList)
            Await.result(resultFuture, timeout.duration).asInstanceOf[ResetTriplestoreContentACK]
            println("... loading of demo data finished.")
        }

        val ontologyCacheFuture = responderManager ? LoadOntologiesRequestV2(systemUser)
        Await.result(ontologyCacheFuture, timeout.duration).asInstanceOf[SuccessResponseV2]

        // get allowReloadOverHTTP value from application state actor
        val allowReloadOverHTTP = Await.result(applicationStateActor ? GetAllowReloadOverHTTPState(), 1.second).asInstanceOf[Boolean]

        if (allowReloadOverHTTP) {
            println("WARNING: Resetting Triplestore Content over HTTP is turned ON.")
        }

        // start the different reporters. reporters are the connection points between kamon (the collector) and
        // the application which we will use to look at the collected data.

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

        Http().bindAndHandle(Route.handlerFlow(apiRoutes), settings.internalKnoraApiHost, settings.internalKnoraApiPort)
        println(s"Knora API Server started at http://${settings.internalKnoraApiHost}:${settings.internalKnoraApiPort}.")
    }

    /**
      * Stops Knora.
      */
    def stopService(): Unit = {
        system.terminate()
        CacheUtil.removeAllCaches()
        //Kamon.shutdown()
    }
}
