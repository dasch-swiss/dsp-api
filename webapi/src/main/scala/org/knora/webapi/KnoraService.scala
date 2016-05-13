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
import org.knora.webapi.http._
import org.knora.webapi.messages.v1.store.triplestoremessages.{ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.store._
import org.knora.webapi.store.triplestore.RdfDataObjectFactory
import org.knora.webapi.util.CacheUtil
import spray.can.Http

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Provides methods for starting and stopping Knora from within another application. This is where the actor system
  * is started along with the three main supervisor actors is started. All further actors are started and supervised
  * by those three actors.
  */
object KnoraService {


    /**
      * The applications actor system.
      */
    implicit lazy val system = ActorSystem("webapi")

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

    //
    /**
      * The application's configuration.
      */
    val settings = Settings(system)

    /**
      * Starts Knora.
      */
    def start = {
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
        println(s"Knora Webapi started! You can access it on http://${settings.httpInterface}:${settings.httpPort}.")
    }

    /**
      * Stops Knora.
      */
    def stop() = {
        system.terminate()
        CacheUtil.removeAllCaches()
        //Kamon.shutdown()
    }
}
