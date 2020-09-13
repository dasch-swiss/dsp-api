/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2.metadata

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import org.knora.webapi.messages.store.triplestoremessages.{SparqlExtendedConstructRequest, SparqlExtendedConstructResponse, SubjectV2}
import org.scalatest.wordspec.AnyWordSpecLike
import akka.actor.typed.scaladsl.adapter._
import akka.{actor => classic}
import org.knora.webapi.responders.v2.metadata.GetMetadataResponderV2.InitWithStore

class MetadataResponderV2Spec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

    "The Get Metadata Responder" must {
        "return metadata" in {
            val responder = testKit.spawn(GetMetadataResponderV2(), "responder")
            val probe = testKit.createTestProbe[GetMetadataResponderV2.MetadataForProject]()

            val mockStore = system.toClassic.actorOf(classic.Props(new classic.Actor {
                def receive = {
                    case SparqlExtendedConstructRequest(sparql) =>
                        SparqlExtendedConstructResponse(
                            Map[SubjectV2, SparqlExtendedConstructResponse.ConstructPredicateObjects]()
                        )
                }
            }))

            responder ! InitWithStore(store = mockStore)
            responder ! GetMetadataResponderV2.GetMetadataForProject("iri", probe.ref)
            probe.expectMessage(GetMetadataResponderV2.MetadataForProject("blabla"))
        }
    }
}
