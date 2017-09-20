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

import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder._

/**
  * An abstract trait for messages that can be sent to `ResourcesResponderV2`.
  */
sealed trait ResourcesResponderRequestV2 extends KnoraRequestV2 {

    def userProfile: UserProfileV1
}

/**
  * Requests a description of a resource. A successful response will be a [[ReadResourcesSequenceV2]].
  *
  * @param resourceIris the IRIs of the resources to be queried.
  * @param userProfile the profile of the user making the request.
  */
case class ResourcesGetRequestV2(resourceIris: Seq[IRI], userProfile: UserProfileV1) extends ResourcesResponderRequestV2

/**
  * Requests a preview of a resource.
  *
  * @param resourceIris the Iris of the resources to obtain a preview for.
  * @param userProfile the profile of the user making the request.
  */
case class ResourcePreviewRequestV2(resourceIris: Seq[IRI], userProfile: UserProfileV1) extends ResourcesResponderRequestV2

