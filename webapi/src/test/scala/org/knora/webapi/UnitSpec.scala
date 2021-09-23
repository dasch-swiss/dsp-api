/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

import messages.StringFormatter

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.language.postfixOps

object UnitSpec {

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
      .dropWhile(_ matches "(java.lang.Thread|.*UnitSpec.?$)")
    val reduced = s.lastIndexWhere(_ == clazz.getName) match {
      case -1 => s
      case z  => s drop (z + 1)
    }
    reduced.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
  }
}

abstract class UnitSpec(_config: Config) extends AnyWordSpecLike with Matchers with BeforeAndAfterAll with LazyLogging {

  def this() =
    this(UnitSpec.defaultConfig)

  // needs to be initialized early on
  StringFormatter.initForTest()
}
