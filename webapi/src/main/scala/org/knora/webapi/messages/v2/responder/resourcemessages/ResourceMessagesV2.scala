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

package org.knora.webapi.messages.v2.responder.resourcemessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder.{KnoraRequestV2, KnoraResponseV2}
import spray.json.{DefaultJsonProtocol, NullOptions, RootJsonFormat}

/**
  * An abstract trait for messages that can be sent to `ResourcesResponderV2`.
  */
sealed trait ResourcesResponderRequestV2 extends KnoraRequestV2 {

    def userProfile: UserProfileV1
}

/**
  * Requests a description of a resource. A successful response will be a [[ResourcesResponseV2]].
  *
  * @param resourceIris the IRI of the resource to be queried.
  * @param userProfile the profile of the user making the request.
  */
case class ResourcesGetRequestV2(resourceIris: Seq[IRI], userProfile: UserProfileV1) extends ResourcesResponderRequestV2

/**
  * Represents a resource.
  *
  * @param resourceClass
  * @param label
  */
case class ResourceV2(resourceClass: IRI, label: String)

/**
  * Represents the Knora API V2 JSON response to a request for a description of a resource.
  *
  * @param resources  a sequence of resources.
  *
  */
case class ResourcesResponseV2(resources: Seq[ResourceV2]) extends KnoraResponseV2 {
    def toJsValue = ResourceV2JsonProtocol.resourcesResponseV2Format.write(this)
}

object ResourceV2JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit val resourceV2Format: RootJsonFormat[ResourceV2] = jsonFormat2(ResourceV2)
    implicit val resourcesResponseV2Format: RootJsonFormat[ResourcesResponseV2] = jsonFormat1(ResourcesResponseV2)

}