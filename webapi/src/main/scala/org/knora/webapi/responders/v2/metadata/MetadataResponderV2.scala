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

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import org.knora.webapi.IRI

object MetadataResponderV2 {

    sealed trait Command
    final case class GetMetadataForProject(projectIri: IRI, replyTo: ActorRef[MetadataForProject]) extends Command
    final case class MetadataForProject(metadata: String)

    def apply(): Behavior[MetadataResponderV2.Command] =
        responder()

    private def responder(): Behavior[MetadataResponderV2.Command] =
        Behaviors.receive { (context: ActorContext[MetadataResponderV2.Command], message: MetadataResponderV2.Command) =>
            message match {
                case GetMetadataForProject(projectIri: IRI, replyTo) =>
                    context.log.info(s"${context.self} got GetMetadataForProject($projectIri) from $replyTo")
                    val metadataForProject: MetadataForProject = getMetadataForProject(projectIri)
                    replyTo ! metadataForProject
                    Behaviors.same
            }
        }

    private def getMetadataForProject(projectIri: IRI): MetadataForProject = {
        val metadata = "blabla"
        val result = MetadataForProject(metadata)
        result
    }
}
