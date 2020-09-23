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

package org.knora.webapi.responders.admin.groups
import akka.{actor => classic}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.knora.webapi.messages.store.triplestoremessages.{SparqlExtendedConstructRequest, SparqlExtendedConstructResponse, SubjectV2}
import org.scalatest.wordspec.AnyWordSpecLike

class GroupsResponderV2Spec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

    "The Get Metadata Responder" must {
        "return metadata" in {
            val mockedStore = system.toClassic.actorOf(classic.Props(new classic.Actor {
                def receive = {
                    case SparqlExtendedConstructRequest(sparql) =>
                        SparqlExtendedConstructResponse(
                            Map[SubjectV2, SparqlExtendedConstructResponse.ConstructPredicateObjects]()
                        )
                }
            }))

            val responder = testKit.spawn(GetGroupResponderV2(mockedStore), "responder")
            val probe = testKit.createTestProbe[GetGroupResponderV2.AllGroupsForProject]()

            responder ! GetGroupResponderV2.GetAllGroupsForProject("iri", probe.ref)
            probe.expectMessage(GetGroupResponderV2.AllGroupsForProject("blabla"))
        }
    }
}
