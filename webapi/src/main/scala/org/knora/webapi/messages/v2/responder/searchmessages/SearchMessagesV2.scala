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

package org.knora.webapi.messages.v2.responder.searchmessages

import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.util.search.ConstructQuery

/**
  * An abstract trait for messages that can be sent to `SearchResponderV2`.
  */
sealed trait SearchResponderRequestV2 extends KnoraRequestV2 {

    def userProfile: UserProfileV1
}

/**
  * Requests a fulltext search. A successful response will be a [[ReadResourcesSequenceV2]].
  *
  * @param searchValue the values to search for.
  * @param offset the offset to be used for paging.
  * @param limitToProject limit search to given project.
  * @param limitToResourceClass limit search to given resource class.
  * @param userProfile the profile of the user making the request.
  */
case class FulltextSearchGetRequestV2(searchValue: String,
                                      offset: Int,
                                      limitToProject: Option[IRI],
                                      limitToResourceClass: Option[IRI],
                                      userProfile: UserProfileV1) extends SearchResponderRequestV2

/**
  *
  * Requests an extended search. A successful response will be a [[ReadResourcesSequenceV2]].
  *
  * @param constructQuery a Sparql construct query provided by the client.
  * @param userProfile the profile of the user making the request.
  */
case class ExtendedSearchGetRequestV2(constructQuery: ConstructQuery,
                                      userProfile: UserProfileV1) extends SearchResponderRequestV2


/**
  * Requests a search of resources by their label. A successful response will be a [[ReadResourcesSequenceV2]].
  *
  * @param searchValue the values to search for.
  * @param limitToProject limit search to given project.
  * @param limitToResourceClass limit search to given resource class.
  * @param userProfile the profile of the user making the request.
  */
case class SearchResourceByLabelRequestV2(searchValue: String,
                                          limitToProject: Option[IRI],
                                          limitToResourceClass: Option[IRI],
                                          userProfile: UserProfileV1) extends SearchResponderRequestV2