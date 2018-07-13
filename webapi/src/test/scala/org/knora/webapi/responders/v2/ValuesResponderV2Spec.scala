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

package org.knora.webapi.responders.v2

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.ConfigFactory
import org.knora.webapi.SharedOntologyTestDataADM._
import org.knora.webapi.SharedTestDataADM._
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.responders._
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.twirl.{StandoffTagIriAttributeV2, StandoffTagV2}
import org.knora.webapi.util.MutableTestIri

import scala.concurrent.duration._

class ValuesResponderV2Spec extends CoreSpec() with ImplicitSender {

    private val incunabulaProjectIri = INCUNABULA_PROJECT_IRI
    private val anythingProjectIri = ANYTHING_PROJECT_IRI

    private val zeitglöckleinIri = "http://rdfh.ch/c5058f3a"
    private val miscResourceIri = "http://rdfh.ch/miscResource"
    private val aThingIri = "http://rdfh.ch/0001/a-thing"

    private val incunabulaUser = SharedTestDataADM.incunabulaMemberUser
    private val imagesUser = SharedTestDataADM.imagesUser01
    private val anythingUser = SharedTestDataADM.anythingUser1

    val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/responders.v2.ValuesResponderV2Spec/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    private val actorUnderTest = TestActorRef[ValuesResponderV2]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    // The default timeout for receiving reply messages from actors.
    private val timeout = 30.seconds

    private def loadTestData(rdfDataObjs: List[RdfDataObject], expectOK: Boolean = false): Unit = {
        storeManager ! ResetTriplestoreContent(rdfDataObjs)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser)

        if (expectOK) {
            expectMsgType[SuccessResponseV2](10.seconds)
        }
    }

    "Load test data" in {
        loadTestData(rdfDataObjs = rdfDataObjects, expectOK = true)
    }

    "The values responder" should {
        /*
        "add a new Integer value (seqnum of a page)" in {

            val seqnum = 4

            actorUnderTest ! CreateValueRequestV1(
                resourceIri = "http://rdfh.ch/8a0b1e75",
                propertyIri = "http://www.knora.org/ontology/0803/incunabula#seqnum",
                value = IntegerValueV1(seqnum),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case CreateValueResponseV1(newValue: IntegerValueV1, _, newValueIri: IRI, _) =>
                    currentSeqnumValueIri.set(newValueIri)
                    newValue should ===(IntegerValueV1(seqnum))
            }
        }
        */

    }
}
