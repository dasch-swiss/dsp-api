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

package org.knora.webapi.messages.v2.responder.ontologymessages

import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder._

/**
  * An abstract trait for messages that can be sent to `ResourcesResponderV2`.
  */
sealed trait OntologiesResponderRequestV2 extends KnoraRequestV2 {

    def userProfile: UserProfileV1
}


/**
  * Requests that all ontologies in the repository are loaded. This message must be sent only once, when the application
  * starts, before it accepts any API requests.
  *
  * @param userProfile the profile of the user making the request.
  */
case class LoadOntologiesRequestV2(userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests the entity definitions for the given resource class Iris.
  *
  * @param resourceClassIris the IRIs of the resource classes to be queried.
  * @param userProfile the profile of the user making the request.
  */
case class ResourceClassesGetRequestV2(resourceClassIris: Set[IRI], userProfile: UserProfileV1) extends OntologiesResponderRequestV2