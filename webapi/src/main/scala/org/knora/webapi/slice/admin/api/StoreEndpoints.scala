/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.query
import zio.ZLayer
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject.jsonCodec
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class MessageResponse(message: String)
object MessageResponse {
  implicit val jsonCodec: JsonCodec[MessageResponse] = DeriveJsonCodec.gen[MessageResponse]
}

final case class StoreEndpoints(baseEndpoints: BaseEndpoints) {

  val postStoreResetTriplestoreContent =
    baseEndpoints.publicEndpoint
      .in("admin" / "store" / "ResetTriplestoreContent")
      .in(
        jsonBody[Option[List[RdfDataObject]]]
          .description("RDF data objects to load into the triplestore, uses defaults if not present.")
      )
      .in(query[Boolean]("prependDefaults").default(true).description("Prepend defaults to the data objects."))
      .out(jsonBody[MessageResponse])
      .description(
        "Resets the content of the triplestore, only available if configuration `allowReloadOverHttp` is set to `true`."
      )
      .tags(List("admin"))

  val endpoints = Seq(postStoreResetTriplestoreContent)
}

object StoreEndpoints {
  val layer = ZLayer.derive[StoreEndpoints]
}
