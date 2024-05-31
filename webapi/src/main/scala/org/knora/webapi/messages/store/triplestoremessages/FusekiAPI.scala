/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.store.triplestoremessages

import zio.json.*

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
  startDateTime: String,
  uptime: Int,
  datasets: Seq[FusekiDataset],
)

/**
 * Represents a Fuseki dataset.
 *
 * @param dsName     the name of the dataset.
 * @param dsState    the state of the dataset.
 * @param dsServices the services available for the dataset.
 */
case class FusekiDataset(
  @jsonField("ds.name") dsName: String,
  @jsonField("ds.state") dsState: Boolean,
  @jsonField("ds.services") dsServices: Seq[FusekiService],
)

/**
 * Represets a service available for a Fuseki dataset.
 *
 * @param srvType        the service type.
 * @param srvDescription a description of the service.
 * @param srvEndpoints   the endpoints provided by the service.
 */
case class FusekiService(
  @jsonField("srv.type") srvType: String,
  @jsonField("srv.description") srvDescription: String,
  @jsonField("srv.endpoints") srvEndpoints: Seq[String],
)

/**
 * Parses server status responses from Fuseki.
 */
object FusekiJsonProtocol {
  implicit val fusekiServiceFormat: JsonCodec[FusekiService] = DeriveJsonCodec.gen[FusekiService]
  implicit val fusekiDatasetFormat: JsonCodec[FusekiDataset] = DeriveJsonCodec.gen[FusekiDataset]
  implicit val fusekiServerFormat: JsonCodec[FusekiServer]   = DeriveJsonCodec.gen[FusekiServer]
}
