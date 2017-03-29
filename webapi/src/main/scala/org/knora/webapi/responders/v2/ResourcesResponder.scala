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

import org.knora.webapi.messages.v2.responder.resourcemessages.{ResourcesGetRequestV2, ResourcesResponseV2}
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil.future2Message
import org.knora.webapi.IRI

import scala.concurrent.Future

class ResourcesResponderV2 extends Responder {

    def receive = {
        case resourcesGetRequest: ResourcesGetRequestV2 => future2Message(sender(), getResources(resourcesGetRequest.resourceIris), log)
    }

    private def getResources(resourceIris: Seq[IRI]): Future[ResourcesResponseV2] = {

        Future(ResourcesResponseV2("test"))


    }

}

