/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
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
import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

/**
 * Represents a response from Fuseki about the state of the Fuseki server.
 *
 * @param version       the server version.
 * @param built         the date when the server was built.
 * @param startDateTime the date when the server was started.
 * @param uptime        the server's uptime.
 * @param datasets      the datasets available on the server.
 */
case class FusekiServer(
  version: String,
  built: String,
  startDateTime: String,
  uptime: Int,
  datasets: Seq[FusekiDataset]
)

/**
 * Represents a Fuseki dataset.
 *
 * @param dsName     the name of the dataset.
 * @param dsState    the state of the dataset.
 * @param dsServices the services available for the dataset.
 */
case class FusekiDataset(dsName: String, dsState: Boolean, dsServices: Seq[FusekiService])

/**
 * Represets a service available for a Fuseki dataset.
 *
 * @param srvType        the service type.
 * @param srvDescription a description of the service.
 * @param srvEndpoints   the endpoints provided by the service.
 */
case class FusekiService(srvType: String, srvDescription: String, srvEndpoints: Seq[String])

/**
 * Parses server status responses from Fuseki.
 */
object FusekiJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val fusekiServiceFormat: JsonFormat[FusekiService] =
    jsonFormat(FusekiService, "srv.type", "srv.description", "srv.endpoints")
  implicit val fusekiDatasetFormat: JsonFormat[FusekiDataset] =
    jsonFormat(FusekiDataset, "ds.name", "ds.state", "ds.services")
  implicit val fusekiServerFormat: RootJsonFormat[FusekiServer] = jsonFormat5(FusekiServer)
}
