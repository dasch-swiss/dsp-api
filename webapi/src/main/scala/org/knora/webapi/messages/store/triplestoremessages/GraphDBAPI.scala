/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.store.triplestoremessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

// Used to communicate with the GraphDB API

sealed trait GraphDBAPI

case class GraphDBRepository(externalUrl: String,
                             id: String,
                             local: Boolean,
                             location: String,
                             readable: Boolean,
                             sesameType: String,
                             title: String,
                             typeOf: String,
                             unsupported: Boolean,
                             uri: String,
                             writable: Boolean)
    extends GraphDBAPI

object GraphDBJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  // 'typeOf' in the case class is 'type' in json
  implicit val graphDBRepositoryFormat: RootJsonFormat[GraphDBRepository] = jsonFormat(GraphDBRepository,
                                                                                       "externalUrl",
                                                                                       "id",
                                                                                       "local",
                                                                                       "location",
                                                                                       "readable",
                                                                                       "sesameType",
                                                                                       "title",
                                                                                       "type",
                                                                                       "unsupported",
                                                                                       "uri",
                                                                                       "writable")
}
