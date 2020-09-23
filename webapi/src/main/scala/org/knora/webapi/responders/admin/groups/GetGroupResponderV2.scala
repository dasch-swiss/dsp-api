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

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.{actor => classic}
import akka.actor.typed.scaladsl.adapter._
import org.knora.webapi.IRI
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructRequest
import org.knora.webapi.settings.{KnoraSettings, KnoraSettingsImpl}

object GetGroupResponderV2 {

    sealed trait Command
    final case class GetAllGroupsForProject(projectIri: IRI, replyTo: ActorRef[AllGroupsForProject]) extends Command
    final case class AllGroupsForProject(groups: String)

    def apply(store: classic.ActorRef): Behavior[GetGroupResponderV2.Command] =
        start(store)

    private def start(store: classic.ActorRef): Behavior[GetGroupResponderV2.Command] =
        Behaviors.receive { (context: ActorContext[GetGroupResponderV2.Command], message: GetGroupResponderV2.Command) =>
            message match {
                case GetAllGroupsForProject(projectIri: IRI, replyTo) =>
                    context.log.info(s"${context.self} got $message from $replyTo")
                    val settings: KnoraSettingsImpl = KnoraSettings(context.system)
                    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
                    val identifier = ProjectIdentifierADM(Some(projectIri))
                    val sparqlQuery = (txt.getGroups(
                        triplestore = settings.triplestoreType,
                        maybeIri = identifier.toIriOption
                    ).toString())

                    store.tell(SparqlExtendedConstructRequest(sparqlQuery), context.self.toClassic)
                    // projectResponse <- (storeManager ? ).mapTo[SparqlExtendedConstructResponse]
                    // classic.tell(Typed.Ping(context.self), context.self.toClassic)

                    val metadataForProject: AllGroupsForProject = getMetadataForProject(projectIri)

                    replyTo ! metadataForProject
                    waitingForTriplestoreAnswer(projectIri)
            }
        }

    private def waitingForTriplestoreAnswer(projectIri: IRI): Behavior[GetGroupResponderV2.Command] =
        Behaviors.receive { (context: ActorContext[GetGroupResponderV2.Command], message: GetGroupResponderV2.Command) =>
            message match {
                case GetAllGroupsForProject(projectIri: IRI, replyTo) =>
                    context.log.info(s"${context.self} got GetMetadataForProject($projectIri) from $replyTo")
                    val metadataForProject: AllGroupsForProject = getMetadataForProject(projectIri)
                    replyTo ! metadataForProject
                    Behaviors.same
            }
        }

    private def getMetadataForProject(projectIri: IRI): AllGroupsForProject = {
        val metadata = "blabla"
        val result = AllGroupsForProject(metadata)
        result
    }
}
