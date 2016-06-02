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
import akka.io.IO
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.http._
import org.knora.webapi.messages.v1.store.triplestoremessages.{Initialized, InitializedResponse, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.store._
import org.knora.webapi.store.triplestore.RdfDataObjectFactory
import org.knora.webapi.util.CacheUtil
import spray.can.Http

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

object KnoraService {

    /*
        Loads the following (first-listed are higher priority):
            - system properties (e.g., -Dconfig.resource=fuseki.conf)
            - test/resources/application.conf
            - main/resources/application.conf
    */
    val defaultConfig: Config = ConfigFactory.load()
}

/**
  * Provides methods for starting and stopping Knora from within another application. This is where the actor system
  * is started along with the three main supervisor actors is started. All further actors are started and supervised
  * by those three actors.
  */
class KnoraService(_system: ActorSystem) {

    /* used if supplied name and config */
    def this(name: String, config: Config) = this(ActorSystem(name, ConfigFactory.load(config.withFallback(KnoraService.defaultConfig))))

    /* used if only config is supplied */
    def this(config: Config) = this(ActorSystem("webapi", ConfigFactory.load(config.withFallback(KnoraService.defaultConfig))))

    /* used if only if name is supplied */
    def this(name: String) = this(ActorSystem(name, ConfigFactory.load()))

    /* used if nothing is supplied */
    def this() = this(ActorSystem("webapi", ConfigFactory.load()))

    /**
      * The applications actor system.
      */
    implicit lazy val system = _system

    /**
      * The supervisor actor that receives HTTP requests.
      */
    val knoraHttpServiceManager = system.actorOf(Props(new KnoraHttpServiceManager with LiveActorMaker), name = KNORA_HTTP_SERVICE_MANAGER_ACTOR_NAME)

    /**
      * The supervisor actor that forwards messages to responder actors to handle API requests.
      */
    val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)

    /**
      * The supervisor actor that forwards messages to actors that deal with persistent storage.
      */
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    /**
      * The application's configuration.
      */
    val settings = Settings(system)

    /**
      * Provide logging
      */
    val log = akka.event.Logging(system, this.getClass())

    /**
      * Timeout definition
      */
    implicit val timeout = Timeout(5 seconds)


    /**
      * Sends messages to all supervisor actors, and checks if they are all ready
      */
    def startActorSystem() {

        // TODO: Check if HttpServiceManager is ready
        log.info(s"HttoServiceManager ready: - ")

        // TODO: Check if ResponderManager is ready
        log.info(s"ResponderManager ready: - ")

        // TODO: Check if Sipi is also ready/accessible
        val storeManagerResult = Await.result(storeManager ? Initialized(), timeout.duration).asInstanceOf[InitializedResponse]
        log.info(s"StoreManager ready: $storeManagerResult")
        log.info(s"ActorSystem ${system.name} started")
    }

    /**
      * Starts Knora.
      */
    def startService = {
        CacheUtil.createCaches(settings.caches)

        if (StartupFlags.loadDemoData.get) {
            println("Start loading of demo data ...")
            implicit val timeout = Timeout(300.seconds)
            val configList = settings.tripleStoreConfig.getConfigList("rdf-data")
            val rdfDataObjectList = configList.map {
                config => RdfDataObjectFactory(config)
            }
            val resultFuture = storeManager ? ResetTriplestoreContent(rdfDataObjectList)
            val result = Await.result(resultFuture, timeout.duration).asInstanceOf[ResetTriplestoreContentACK]
            println("... loading of demo data finished!")
        }

        if (StartupFlags.allowResetTriplestoreContentOperationOverHTTP.get) {
            println("WARNING: Resetting Triplestore Content over HTTP is turned ON!")
        }


        IO(Http) ! Http.Bind(knoraHttpServiceManager, settings.httpInterface, port = settings.httpPort)
        println(s"Knora Webapi started! You can access it under http://${settings.httpInterface}:${settings.httpPort}.")
    }

    /**
      * Stops Knora.
      */
    def stopService = {
        system.terminate()
        CacheUtil.removeAllCaches()
        //Kamon.shutdown()
    }
}
