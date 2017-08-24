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

package org.knora.webapi.responders.v1


import akka.actor.Props
import akka.testkit._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.sipimessages.{SipiFileInfoGetRequestV1, SipiFileInfoGetResponseV1}
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.responders._
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}

import scala.concurrent.duration._

object SipiResponderV1Spec {

    // A test UserProfileV1.
    private val userProfile: UserProfileV1 = SharedAdminTestData.incunabulaMemberUser

    // A test UserDataV1.
    private val userData: UserDataV1 = userProfile.userData

    private val fileValueResponseFull = SipiFileInfoGetResponseV1(permissionCode = 6)
}

/**
  * Tests [[SipiResponderV1]].
  */
class SipiResponderV1Spec extends CoreSpec() with ImplicitSender {
    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[SipiResponderV1]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/responders.v1.SipiResponderV1Spec/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula")
    )

    // The default timeout for receiving reply messages from actors.
    private val timeout = 20.seconds


    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(SharedAdminTestData.rootUser)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The Sipi responder" should {
        "return details of a full quality file value" in {
            // http://localhost:3333/v1/files/http%3A%2F%2Fdata.knora.org%2F8a0b1e75%2Freps%2F7e4ba672
            actorUnderTest ! SipiFileInfoGetRequestV1(
                userProfile = SipiResponderV1Spec.userProfile,
                filename = "incunabula_0000000002.jp2"
            )

            expectMsg(timeout, SipiResponderV1Spec.fileValueResponseFull)
        }
    }
}
