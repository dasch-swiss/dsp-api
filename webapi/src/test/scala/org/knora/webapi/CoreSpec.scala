/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.pattern.ask
import akka.stream.Materializer
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.app.{APPLICATION_MANAGER_ACTOR_NAME, ApplicationActor, LiveManagers}
import org.knora.webapi.core.Core
import org.knora.webapi.messages.app.appmessages.{AppStart, AppStop, SetAllowReloadOverHTTPState}
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceFlushDB
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetRepositoryContent}
import org.knora.webapi.messages.v1.responder.ontologymessages.LoadOntologiesRequest
import org.knora.webapi.responders.ResponderData
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl}
import org.knora.webapi.util.StartupUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.KnoraSystemInstances

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps

object CoreSpec {

    /*
        Loads the following (first-listed are higher priority):
            - system properties (e.g., -Dconfig.resource=fuseki.conf)
            - test/resources/application.conf
            - main/resources/application.conf
    */
    val defaultConfig: Config = ConfigFactory.load()

    /* Copied from: akka/akka-testkit/src/test/scala/akka/testkit/AkkaSpec.scala */
    def getCallerName(clazz: Class[_]): String = {
        val s = (Thread.currentThread.getStackTrace map (_.getClassName) drop 1)
            .dropWhile(_ matches "(java.lang.Thread|.*CoreSpec.?$)")
        val reduced = s.lastIndexWhere(_ == clazz.getName) match {
            case -1 ⇒ s
            case z ⇒ s drop (z + 1)
        }
        reduced.head.replaceFirst( """.*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
    }
}

abstract class CoreSpec(_system: ActorSystem) extends TestKit(_system) with Core with StartupUtils with AnyWordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

    /* constructors - individual tests can override the configuration by giving their own */
    def this(name: String, config: Config) = this(ActorSystem(name, TestContainers.PortConfig.withFallback(ConfigFactory.load(config.withFallback(CoreSpec.defaultConfig)))))
    def this(config: Config) = this(ActorSystem(CoreSpec.getCallerName(getClass), TestContainers.PortConfig.withFallback(ConfigFactory.load(config.withFallback(CoreSpec.defaultConfig)))))
    def this(name: String) = this(ActorSystem(name, TestContainers.PortConfig.withFallback(ConfigFactory.load())))
    def this() = this(ActorSystem(CoreSpec.getCallerName(getClass), TestContainers.PortConfig.withFallback(ConfigFactory.load())))

    /* needed by the core trait */
    implicit lazy val settings: KnoraSettingsImpl = KnoraSettings(system)
    implicit val materializer: Materializer = Materializer.matFromSystem(system)
    implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

    // can be overridden in individual spec
    lazy val rdfDataObjects = Seq.empty[RdfDataObject]

    // needs to be initialized early on
    StringFormatter.initForTest()

    val log: LoggingAdapter = akka.event.Logging(system, this.getClass)

    lazy val appActor: ActorRef = system.actorOf(Props(new ApplicationActor with LiveManagers), name = APPLICATION_MANAGER_ACTOR_NAME)

    // The main application actor forwards messages to the responder manager and the store manager.
    val responderManager: ActorRef = appActor
    val storeManager: ActorRef = appActor

    val responderData: ResponderData = ResponderData(
        system = system,
        appActor = appActor
    )

    final override def beforeAll() {
        // set allow reload over http
        appActor ! SetAllowReloadOverHTTPState(true)

        // Start Knora, without reading data from the repository
        appActor ! AppStart(ignoreRepository = true, requiresIIIFService = false)

        // waits until knora is up and running
        applicationStateRunning()

        loadTestData(rdfDataObjects)

        // memusage()
    }

    final override def afterAll() {
        appActor ! AppStop()
        // memusage()
    }

    protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit = {
        logger.info("Loading test data started ...")
        implicit val timeout: Timeout = Timeout(settings.defaultTimeout)
        Await.result(appActor ? ResetRepositoryContent(rdfDataObjects), 479999.milliseconds)
        Await.result(appActor ? LoadOntologiesRequest(KnoraSystemInstances.Users.SystemUser), 1 minute)
        Await.result(appActor ? CacheServiceFlushDB(KnoraSystemInstances.Users.SystemUser), 5 seconds)
        logger.info("... loading test data done.")
    }

    def memusage(): Unit = {
        // memory info
        val mb = 1024 * 1024
        val runtime = Runtime.getRuntime
        println("** Used Memory:  " + (runtime.totalMemory - runtime.freeMemory) / mb)
        println("** Free Memory:  " + runtime.freeMemory / mb)
        println("** Total Memory: " + runtime.totalMemory / mb)
        println("** Max Memory:   " + runtime.maxMemory / mb)
    }

}
