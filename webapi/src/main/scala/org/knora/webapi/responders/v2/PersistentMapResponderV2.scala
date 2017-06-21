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

package org.knora.webapi.responders.v2

import java.time.OffsetDateTime

import akka.pattern._
import org.knora.webapi.messages.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse}
import org.knora.webapi.messages.v2.responder.persistentmapmessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.KnoraIdUtil

import scala.concurrent.Future

/**
  * Manages storage in `knora-base:Map` objects on behalf of other responders. Since this actor does no permission
  * checking, it should not be used directly by routes.
  */
class PersistentMapResponderV2 extends Responder {
    private val knoraIdUtil = new KnoraIdUtil

    def receive = {
        case mapEntryGetRequest: PersistentMapEntryGetRequestV2 => future2Message(sender(), getPersistentMapEntryV2(mapEntryGetRequest), log)
    }

    private def getPersistentMapEntryV2(request: PersistentMapEntryGetRequestV2): Future[PersistentMapEntry] = {
        for {
            entryRequestSparql <- Future(queries.sparql.v2.txt.getMapEntry(
                triplestore = settings.triplestoreType,
                mapIri = knoraIdUtil.makeMapIri(request.mapPath),
                mapEntryKey = request.mapEntryKey
            ).toString())

            resourceRequestResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(entryRequestSparql)).mapTo[SparqlConstructResponse]

        } yield PersistentMapEntry(key = "foo", value = "bar", lastModificationDate = OffsetDateTime.parse("2004-04-12T13:20:00Z"))
    }
}
