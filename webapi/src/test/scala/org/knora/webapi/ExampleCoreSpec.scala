/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import org.knora.webapi.messages.app.appmessages.ActorReady
import org.knora.webapi.messages.app.appmessages.ActorReadyAck
import scala.concurrent.duration._

object ExampleCoreSpec {

  val config: Config = ConfigFactory.parseString("""
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

class ExampleCoreSpec extends CoreSpec(ExampleCoreSpec.config) with ImplicitSender {

  private val timeout = 5.seconds

  "The ExampleCoreSpec " when {
    "testing something" should {
      "return true" in {
        appActor ! ActorReady()
        val response = expectMsgType[ActorReadyAck](timeout)
      }
    }
  }
}
