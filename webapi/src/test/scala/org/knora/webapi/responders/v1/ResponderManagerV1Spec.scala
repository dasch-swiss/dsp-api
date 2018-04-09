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

package org.knora.webapi.responders.v1

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef, TestProbe}
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceInfoGetRequestV1
import org.knora.webapi.responders._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.{CoreSpec, SharedTestDataADM, TestProbeMaker}

/**
  * Tests [[ResponderManager]].
  */
class ResponderManagerV1Spec extends CoreSpec("ResponderManagerTestSystem") with ImplicitSender with Authenticator {


    val actorUnderTest = TestActorRef(Props(new ResponderManager with TestProbeMaker), RESPONDER_MANAGER_ACTOR_NAME)

    val mockResourcesRouter = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(RESOURCES_ROUTER_V1_ACTOR_NAME, null)
    val mockValuesRouter = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(VALUES_ROUTER_V1_ACTOR_NAME, null)
    val mockSipiRouter = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(SIPI_ROUTER_V1_ACTOR_NAME, null)
    val mockUsersRouter = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(USERS_ROUTER_V1_ACTOR_NAME, null)
    val mockListsRouter = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(LISTS_ROUTER_V1_ACTOR_NAME, null)
    val mockSearchRouter = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(SEARCH_ROUTER_V1_ACTOR_NAME, null)
    val mockOntologyRouter = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(ONTOLOGY_ROUTER_V1_ACTOR_NAME, null)

    /**
      * TODO: Add test cases testing if for every type of message, the message gets forwarded correctly to the
      * correct router
      */
    "The ResponderManagerV1 " must {
        "start the 'ResourcesResponder' router " in {
            mockResourcesRouter.isInstanceOf[TestProbe] should ===(true)

            val testMsg = ResourceInfoGetRequestV1("http://data.knora.org/xyz", SharedTestDataADM.anonymousUser)
            actorUnderTest ! testMsg
            mockResourcesRouter.expectMsg(testMsg)
        }

        "start the 'ValuesResponder' router " in {
            mockValuesRouter.isInstanceOf[TestProbe] should ===(true)
        }

        "start the 'SipiResponder' router " in {
            mockSipiRouter.isInstanceOf[TestProbe] should ===(true)
        }

        "start the 'UsersResponder' router " in {
            mockUsersRouter.isInstanceOf[TestProbe] should ===(true)
        }

        "start the 'HierarchicalListsResponder' router " in {
            mockListsRouter.isInstanceOf[TestProbe] should ===(true)
        }

        "start the 'SearchResponder' router " in {
            mockSearchRouter.isInstanceOf[TestProbe] should ===(true)
        }

        "start the 'OntologyResponder' router " in {
            mockOntologyRouter.isInstanceOf[TestProbe] should ===(true)
        }
    }
}
