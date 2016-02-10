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
import org.knora.webapi.messages.v1respondermessages.triplestoremessages.{ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.store.triplestore.RdfDataObjectFactory
import org.knora.webapi.util.CacheUtil
import spray.can.Http

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Provides methods for starting and stopping Knora from within another application. This is where the cake pattern
  * is used to gather all dependencies needed for starting the server. The trait [[CoreBooted]] provides
  * us with an actor system and the trait [[CoreManagerActors]] with the three main manager actors which are started
  * inside the actor system provided by [[CoreBooted]].
  */
object KnoraService extends CoreBooted with CoreManagerActors {

    // The application's configuration.
    val settings = Settings(system)

    /**
      * Starts Knora.
      */
    def start(loadDemoData: Boolean) = {
        CacheUtil.createCaches(settings.caches)

        if (loadDemoData) {
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
