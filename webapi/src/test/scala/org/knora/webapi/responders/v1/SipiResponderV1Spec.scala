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

package org.knora.webapi.responders.v1


import akka.testkit._
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectRestrictedViewSettingsADM
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v1.responder.sipimessages.{SipiFileInfoGetRequestV1, SipiFileInfoGetResponseV1}

import scala.concurrent.duration._

object SipiResponderV1Spec {
    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * Tests [[SipiResponderV1]].
  */
class SipiResponderV1Spec extends CoreSpec(SipiResponderV1Spec.config) with ImplicitSender {

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/responders.v1.SipiResponderV1Spec/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula")
    )

    // The default timeout for receiving reply messages from actors.
    private val timeout = 20.seconds

    "The Sipi responder" should {
        "return details of a full quality file value" in {
            // http://localhost:3333/v1/files/http%3A%2F%2Frdfh.ch%2F8a0b1e75%2Freps%2F7e4ba672
            responderManager ! SipiFileInfoGetRequestV1(
                userProfile = SharedTestDataV1.incunabulaMemberUser,
                filename = "incunabula_0000000002.jp2"
            )

            expectMsg(timeout, SipiFileInfoGetResponseV1(permissionCode = 6, None))
        }

        "return details of a restricted view file value" in {
            // http://localhost:3333/v1/files/http%3A%2F%2Frdfh.ch%2F8a0b1e75%2Freps%2F7e4ba672
            responderManager ! SipiFileInfoGetRequestV1(
                userProfile = SharedTestDataV1.anonymousUser,
                filename = "incunabula_0000000002.jp2"
            )

            expectMsg(timeout, SipiFileInfoGetResponseV1(permissionCode = 1, Some(ProjectRestrictedViewSettingsADM(size=Some("!512x512"), watermark = Some("path_to_image")))))
        }
    }
}
