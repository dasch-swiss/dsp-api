/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.store.triplestoremessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

case class FusekiServer(version: String,
                        built: String,
                        startDateTime: String,
                        uptime: Int,
                        datasets: Seq[FusekiDataset])

case class FusekiDataset(dsName: String,
                         dsState: Boolean,
                         dsServices: Seq[FusekiService])

case class FusekiService(srvType: String,
                         srvDescription: String,
                         srvEndpoints: Seq[String])

object FusekiJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val fusekiServiceFormat: JsonFormat[FusekiService] = jsonFormat(FusekiService, "srv.type", "srv.description", "srv.endpoints")
    implicit val fusekiDatasetFormat: JsonFormat[FusekiDataset] = jsonFormat(FusekiDataset, "ds.name", "ds.state", "ds.services")
    implicit val fusekiServerFormat: RootJsonFormat[FusekiServer] = jsonFormat5(FusekiServer)
}
