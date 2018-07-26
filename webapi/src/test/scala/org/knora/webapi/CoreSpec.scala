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

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.pattern._
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.messages.v1.responder.ontologymessages.LoadOntologiesRequest
import org.knora.webapi.responders.{MockableResponderManager, RESPONDER_MANAGER_ACTOR_NAME}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.{CacheUtil, StringFormatter}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
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

abstract class CoreSpec(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

    // can be overridden in individual spec
    lazy val rdfDataObjects = Seq.empty[RdfDataObject]

    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    // needs to be initialized early on
    StringFormatter.initForTest()

    val settings = Settings(system)
    val log = akka.event.Logging(system, this.getClass)

    lazy val mockResponders: Map[String, ActorRef] = Map.empty[String, ActorRef]

    val responderManager: ActorRef = system.actorOf(Props(new MockableResponderManager(mockResponders)), name = RESPONDER_MANAGER_ACTOR_NAME)
    val storeManager: ActorRef = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    final override def beforeAll() {
        CacheUtil.createCaches(settings.caches)
        loadTestData(rdfDataObjects)
        // memusage()
    }

    final override def afterAll() {
        Http().shutdownAllConnectionPools()
        system.terminate()
        atTermination()
        CacheUtil.removeAllCaches()
        // memusage()
    }

    protected def atTermination() {}

    /* individual tests can override the configuration by giving their own */
    def this(name: String, config: Config) = this(ActorSystem(name, ConfigFactory.load(config.withFallback(CoreSpec.defaultConfig))))

    def this(config: Config) = this(ActorSystem(CoreSpec.getCallerName(getClass), ConfigFactory.load(config.withFallback(CoreSpec.defaultConfig))))

    def this(name: String) = this(ActorSystem(name, ConfigFactory.load()))

    def this() = this(ActorSystem(CoreSpec.getCallerName(getClass), ConfigFactory.load()))

    protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit = {
        implicit val timeout: Timeout = Timeout(settings.defaultTimeout)
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 5 minutes)
        Await.result(responderManager ? LoadOntologiesRequest(KnoraSystemInstances.Users.SystemUser), 1 minute)
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
