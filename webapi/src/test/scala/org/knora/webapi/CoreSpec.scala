/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.util.CacheUtil
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

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

    val settings = Settings(_system)
    val logger = akka.event.Logging(_system, this.getClass())

    final override def beforeAll() {
        atStartup()
        CacheUtil.createCaches(settings.caches)
        // memusage()
    }

    protected def atStartup() {}

    final override def afterAll() {
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
