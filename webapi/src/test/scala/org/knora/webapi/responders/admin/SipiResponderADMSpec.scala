/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import akka.testkit._
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectRestrictedViewSettingsADM
import org.knora.webapi.messages.admin.responder.sipimessages._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM

import scala.concurrent.duration._

object SipiResponderADMSpec {
  val config: Config = ConfigFactory.parseString("""
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * Tests [[SipiResponderADM]].
 */
class SipiResponderADMSpec extends CoreSpec(SipiResponderADMSpec.config) with ImplicitSender {

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula")
  )

  // The default timeout for receiving reply messages from actors.
  private val timeout = 20.seconds

  "The Sipi responder" should {
    "return details of a full quality file value" in {
      // http://localhost:3333/v1/files/http%3A%2F%2Frdfh.ch%2F8a0b1e75%2Freps%2F7e4ba672
      appActor ! SipiFileInfoGetRequestADM(
        projectID = "0803",
        filename = "incunabula_0000003328.jp2",
        requestingUser = SharedTestDataADM.incunabulaMemberUser
      )

      expectMsg(timeout, SipiFileInfoGetResponseADM(permissionCode = 6, None))
    }

    "return details of a restricted view file value" in {
      // http://localhost:3333/v1/files/http%3A%2F%2Frdfh.ch%2F8a0b1e75%2Freps%2F7e4ba672
      appActor ! SipiFileInfoGetRequestADM(
        projectID = "0803",
        filename = "incunabula_0000003328.jp2",
        requestingUser = SharedTestDataADM.anonymousUser
      )

      expectMsg(
        timeout,
        SipiFileInfoGetResponseADM(
          permissionCode = 1,
          Some(ProjectRestrictedViewSettingsADM(size = Some("!512,512"), watermark = Some("path_to_image")))
        )
      )
    }
  }
}
