/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.store.triplestoremessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json.JsonFormat
import spray.json.RootJsonFormat

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
