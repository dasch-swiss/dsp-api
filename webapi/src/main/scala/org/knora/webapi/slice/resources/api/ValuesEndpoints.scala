/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.tapir.*
import zio.ZLayer

import org.knora.webapi.slice.common.api.ApiV2
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.resources.api.model.ValueUuid
import org.knora.webapi.slice.resources.api.model.VersionDate

final case class ValuesEndpoints(baseEndpoint: BaseEndpoints) {

  private val base: EndpointInput[Unit] = "v2" / "values"

  private val resourceIri = path[String].name("resourceIri").description("The IRI of a Resource.")
  private val valueUuid   = path[ValueUuid].name("valueUuid").description("The UUID of a Value.")
  private val version     = query[Option[VersionDate]]("version")

  private val linkToValuesDocumentation =
    """Find detailed documentation on <a href="https://docs.dasch.swiss/latest/DSP-API/03-endpoints/api-v2/editing-values/">docs.dasch.swiss</a>."""

  val getValue = baseEndpoint.withUserEndpoint.get
    .in(base / resourceIri / valueUuid)
    .in(version)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringJsonBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description(linkToValuesDocumentation)

  val postValues = baseEndpoint.withUserEndpoint.post
    .in(base)
    .in(
      stringJsonBody.example(
        "{\n  \"@id\": \"http://rdfh.ch/0001/a-thing\",\n  \"@type\": \"anything:Thing\",\n  \"anything:hasInteger\": {\n    \"@type\": \"knora-api:IntValue\",\n    \"knora-api:intValueAsInt\": 4\n  },\n  \"@context\": {\n    \"knora-api\": \"http://api.knora.org/ontology/knora-api/v2#\",\n    \"anything\": \"http://0.0.0.0:3333/ontology/0001/anything/v2#\"\n  }\n}",
      ),
    )
    .out(stringJsonBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description(linkToValuesDocumentation)

  val putValues = baseEndpoint.withUserEndpoint.put
    .in(base)
    .in(
      stringJsonBody.example(
        "{\n  \"@id\": \"http://rdfh.ch/0001/a-thing\",\n  \"@type\": \"anything:Thing\",\n  \"anything:hasInteger\": {\n    \"@id\": \"http://rdfh.ch/0001/a-thing/values/vp96riPIRnmQcbMhgpv_Rg\",\n    \"@type\": \"knora-api:IntValue\",\n    \"knora-api:intValueAsInt\": 5\n  },\n  \"@context\": {\n    \"knora-api\": \"http://api.knora.org/ontology/knora-api/v2#\",\n    \"anything\": \"http://0.0.0.0:3333/ontology/0001/anything/v2#\"\n  }\n}",
      ),
    )
    .out(stringJsonBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description(linkToValuesDocumentation)

  val deleteValues = baseEndpoint.withUserEndpoint.post
    .in(base / "delete")
    .in(stringJsonBody.example(ValuesEndpoints.Examples.deleteValue))
    .out(stringJsonBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description(linkToValuesDocumentation)

  val postValuesErase = baseEndpoint.securedEndpoint.post
    .in(base / "erase")
    .in(stringJsonBody.example(ValuesEndpoints.Examples.deleteValue))
    .out(stringJsonBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description(s"Erase a Value and all of its old versions from the database completely. $linkToValuesDocumentation")

  val postValuesErasehistory = baseEndpoint.securedEndpoint.post
    .in(base / "erasehistory")
    .in(stringJsonBody.example(ValuesEndpoints.Examples.deleteValue))
    .out(stringJsonBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description(
      s"Erase all old versions of a Value from the database completely and keep only the latest version. $linkToValuesDocumentation",
    )

  val endpoints: Seq[AnyEndpoint] = Seq(
    getValue,
    postValues,
    putValues,
    deleteValues,
    postValuesErase,
    postValuesErasehistory,
  ).map(_.endpoint.tag("V2 Values"))
}

object ValuesEndpoints {
  val layer = ZLayer.derive[ValuesEndpoints]

  object Examples {
    val deleteValue: String =
      "{\n  \"@id\": \"http://rdfh.ch/0001/a-thing\",\n" +
        "  \"@type\": \"anything:Thing\",\n" +
        "  \"anything:hasInteger\": {\n" +
        "    \"@id\": \"http://rdfh.ch/0001/a-thing/values/vp96riPIRnmQcbMhgpv_Rg\",\n" +
        "    \"@type\": \"knora-api:IntValue\"\n" +
        "  },\n" +
        "  \"@context\": {\n" +
        "    \"knora-api\": \"http://api.knora.org/ontology/knora-api/v2#\",\n" +
        "    \"anything\": \"http://0.0.0.0:3333/ontology/0001/anything/v2#\"\n" +
        "  }\n" +
        "}"
  }

}
