/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.store.triplestoremessages.{ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.routing.v1._
import org.knora.webapi.store._
import org.knora.webapi.store.triplestore.RdfDataObjectFactory
import org.knora.webapi.util.CacheUtil

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Provides methods for starting and stopping Knora from within another application. This is where the actor system
  * is started along with the three main supervisor actors is started. All further actors are started and supervised
  * by those three actors.
  */
object KnoraService {

    /**
      * The applications actor system.
      */
    implicit lazy private val system = ActorSystem("webapi")

    /**
      * Logging
      */
    private val log = akka.event.Logging(system, this.getClass())

    /**
      * The supervisor actor that receives HTTP requests.
      */
    //private val knoraHttpServiceManager = system.actorOf(Props(new KnoraHttpServiceManager with LiveActorMaker), name = KNORA_HTTP_SERVICE_MANAGER_ACTOR_NAME)

    /**
      * The supervisor actor that forwards messages to responder actors to handle API requests.
      */
    private val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)

    /**
      * The supervisor actor that forwards messages to actors that deal with persistent storage.
      */
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    /**
      * The application's configuration.
      */
    private val settings = Settings(system)

    /**
      * A user representing the Knora API server, used for initialisation on startup.
      */
    private val systemUser = UserProfileV1(userData = UserDataV1(lang = "en"), isSystemUser = true)


    private val apiRoutes =
                ResourcesRouteV1.knoraApiPath(system, settings, log) ~
                ValuesRouteV1.knoraApiPath(system, settings, log) ~
                SipiRouteV1.knoraApiPath(system, settings, log) ~
                ListsRouteV1.knoraApiPath(system, settings, log) ~
                SearchRouteV1.knoraApiPath(system, settings, log) ~
                ResourceTypesRouteV1.knoraApiPath(system, settings, log) ~
                AuthenticateRouteV1.knoraApiPath(system, settings, log) ~
                AssetsRouteV1.knoraApiPath(system, settings, log) ~
                GraphDataRouteV1.knoraApiPath(system, settings, log) ~
                ProjectsRouteV1.knoraApiPath(system, settings, log) ~
                CkanRouteV1.knoraApiPath(system, settings, log) ~
                StoreRouteV1.knoraApiPath(system, settings, log)

    /**
      * Starts the Knora API server.
      */
    def start(): Unit = {
        implicit val timeout = Timeout(300.seconds)
        implicit val materializer = ActorMaterializer()
        // needed for the future map/flatmap in the end
        implicit val executionContext = system.dispatcher

        CacheUtil.createCaches(settings.caches)

        if (StartupFlags.loadDemoData.get) {
            println("Start loading of demo data ...")
            val configList = settings.tripleStoreConfig.getConfigList("rdf-data")
            val rdfDataObjectList = configList.map {
                config => RdfDataObjectFactory(config)
            }
            val resultFuture = storeManager ? ResetTriplestoreContent(rdfDataObjectList)
            Await.result(resultFuture, timeout.duration).asInstanceOf[ResetTriplestoreContentACK]
            println("... loading of demo data finished.")
        }

        val ontologyCacheFuture = responderManager ? LoadOntologiesRequest(systemUser)
        Await.result(ontologyCacheFuture, timeout.duration).asInstanceOf[LoadOntologiesResponse]

        if (StartupFlags.allowResetTriplestoreContentOperationOverHTTP.get) {
            println("WARNING: Resetting Triplestore Content over HTTP is turned ON.")
        }

        val host = settings.httpInterface
        val port = settings.httpPort
        val bindingFuture: Future[ServerBinding] = Http().bindAndHandle(Route.handlerFlow(apiRoutes), host, port)
        println(s"Knora API Server started. You can access it on http://${settings.httpInterface}:${settings.httpPort}.")

        bindingFuture.onFailure {
            case ex: Exception =>
                log.error(ex, s"Failed to bind to ${settings.httpInterface}:${settings.httpPort}!")
        }
    }

    /**
      * Stops Knora.
      */
    def stop(): Unit = {
        system.terminate()
        CacheUtil.removeAllCaches()
        //Kamon.shutdown()
    }
}
