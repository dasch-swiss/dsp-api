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

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import akka.actor.{ActorSystem, _}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.pattern._
import akka.stream.ActorMaterializer
import org.knora.webapi.http.CORSSupport.CORS
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.store.triplestoremessages.{Initialized, InitializedResponse, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.routing.v1._
import org.knora.webapi.store._
import org.knora.webapi.store.triplestore.RdfDataObjectFactory
import org.knora.webapi.util.CacheUtil

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

trait Core {
    implicit val system: ActorSystem
}

/**
  * The applications actor system.
  */
trait LiveCore extends Core {
    implicit lazy val system = ActorSystem("webapi")
}

/**
  * Provides methods for starting and stopping Knora from within another application. This is where the actor system
  * along with the three main supervisor actors is started. All further actors are started and supervised by those
  * three actors.
  */
trait KnoraService {
    this: Core =>

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
    protected val settings: SettingsImpl = Settings(system)

    /**
      * Provide logging
      */
    protected val log = akka.event.Logging(system, "KnoraService")

    /**
      * Timeout definition (need to be high enough to allow reloading of data so that checkActorSystem doesn't timeout)
      */
    implicit private val timeout = settings.defaultRestoreTimeout

    /**
      * A user representing the Knora API server, used for initialisation on startup.
      */
    private val systemUser = UserProfileV1(userData = UserDataV1(lang = "en"), isSystemUser = true)

    /**
      * All routes composed together and CORS activated.
      */
    private val apiRoutes = CORS(
        ResourcesRouteV1.knoraApiPath(system, settings, log) ~
            ValuesRouteV1.knoraApiPath(system, settings, log) ~
            SipiRouteV1.knoraApiPath(system, settings, log) ~
            ListsRouteV1.knoraApiPath(system, settings, log) ~
            ResourceTypesRouteV1.knoraApiPath(system, settings, log) ~
            SearchRouteV1.knoraApiPath(system, settings, log) ~
            AuthenticateRouteV1.knoraApiPath(system, settings, log) ~
            AssetsRouteV1.knoraApiPath(system, settings, log) ~
            ProjectsRouteV1.knoraApiPath(system, settings, log) ~
            CkanRouteV1.knoraApiPath(system, settings, log) ~
            StoreRouteV1.knoraApiPath(system, settings, log) ~
			PermissionsRouteV1.knoraApiPath(system, settings, log),
        settings,
        log
    )

    /**
      * Sends messages to all supervisor actors in a blocking manner, checking if they are all ready.
      */
    def checkActorSystem(): Unit = {
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

        implicit val materializer = ActorMaterializer()

        // needed for startup flags and the future map/flatmap in the end
        implicit val executionContext = system.dispatcher

        CacheUtil.createCaches(settings.caches)

        if (StartupFlags.loadDemoData.get) {
            println("Start loading of demo data ...")
            val configList = settings.tripleStoreConfig.getConfigList("rdf-data")
            val rdfDataObjectList = configList.asScala.map {
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

        // Either HTTP or HTTPs, or both, must be enabled.
        if (!(settings.knoraApiUseHttp || settings.knoraApiUseHttps)) {
            throw HttpConfigurationException("Neither HTTP nor HTTPS is enabled")
        }

        // Activate HTTP if enabled.
        if (settings.knoraApiUseHttp) {
            Http().bindAndHandle(Route.handlerFlow(apiRoutes), settings.knoraApiHost, settings.knoraApiHttpPort)
            println(s"Knora API Server using HTTP at http://${settings.knoraApiHost}:${settings.knoraApiHttpPort}.")
        }

        // Activate HTTPS if enabled.
        if (settings.knoraApiUseHttps) {
            val keystorePassword: Array[Char] = settings.httpsKeystorePassword.toCharArray
            val keystore: KeyStore = KeyStore.getInstance("JKS")
            val keystoreFile: InputStream = getClass.getClassLoader.getResourceAsStream(settings.httpsKeystore)
            require(keystoreFile != null, s"Could not load keystore ${settings.httpsKeystore}")
            keystore.load(keystoreFile, keystorePassword)

            val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
            keyManagerFactory.init(keystore, keystorePassword)

            val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
            trustManagerFactory.init(keystore)

            val sslContext: SSLContext = SSLContext.getInstance("TLS")
            sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
            val https = ConnectionContext.https(sslContext)

            Http().bindAndHandle(Route.handlerFlow(apiRoutes), settings.knoraApiHost, settings.knoraApiHttpsPort, connectionContext = https)
            println(s"Knora API Server using HTTPS at https://${settings.knoraApiHost}:${settings.knoraApiHttpsPort}.")
        }
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
