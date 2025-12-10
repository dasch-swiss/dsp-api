/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.values

import sttp.tapir.*
import zio.ZLayer

import org.knora.webapi.slice.api.v2.ValueUuid
import org.knora.webapi.slice.api.v2.VersionDate
import org.knora.webapi.slice.common.api.ApiV2
import org.knora.webapi.slice.common.api.BaseEndpoints

final class ValuesEndpoints(baseEndpoint: BaseEndpoints) {

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
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Get a value of a resource. Publicly accessible. Requires appropriate object access permissions on the resource. $linkToValuesDocumentation",
    )

  val postValues = baseEndpoint.withUserEndpoint.post
    .in(base)
    .in(
      stringJsonBody.example(
        "{\n  \"@id\": \"http://rdfh.ch/0001/a-thing\",\n  \"@type\": \"anything:Thing\",\n  \"anything:hasInteger\": {\n    \"@type\": \"knora-api:IntValue\",\n    \"knora-api:intValueAsInt\": 4\n  },\n  \"@context\": {\n    \"knora-api\": \"http://api.knora.org/ontology/knora-api/v2#\",\n    \"anything\": \"http://0.0.0.0:3333/ontology/0001/anything/v2#\"\n  }\n}",
      ),
    )
    .out(stringJsonBody)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Create a new value for a resource. Requires appropriate object access permissions on the resource. $linkToValuesDocumentation",
    )

  val putValues = baseEndpoint.withUserEndpoint.put
    .in(base)
    .in(
      stringJsonBody.example(
        "{\n  \"@id\": \"http://rdfh.ch/0001/a-thing\",\n  \"@type\": \"anything:Thing\",\n  \"anything:hasInteger\": {\n    \"@id\": \"http://rdfh.ch/0001/a-thing/values/vp96riPIRnmQcbMhgpv_Rg\",\n    \"@type\": \"knora-api:IntValue\",\n    \"knora-api:intValueAsInt\": 5\n  },\n  \"@context\": {\n    \"knora-api\": \"http://api.knora.org/ontology/knora-api/v2#\",\n    \"anything\": \"http://0.0.0.0:3333/ontology/0001/anything/v2#\"\n  }\n}",
      ),
    )
    .out(stringJsonBody)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Update a value of a resource. Requires appropriate object access permissions on the resource. $linkToValuesDocumentation",
    )

  val deleteValues = baseEndpoint.withUserEndpoint.post
    .in(base / "delete")
    .in(stringJsonBody.example(ValuesEndpoints.Examples.deleteValue))
    .out(stringJsonBody)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Mark a value as deleted. Requires appropriate object access permissions on the resource. $linkToValuesDocumentation",
    )

  val postValuesErase = baseEndpoint.securedEndpoint.post
    .in(base / "erase")
    .in(stringJsonBody.example(ValuesEndpoints.Examples.deleteValue))
    .out(stringJsonBody)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Erase a Value and all of its old versions from the database completely. Requires SystemAdmin permissions. $linkToValuesDocumentation",
    )

  val postValuesErasehistory = baseEndpoint.securedEndpoint.post
    .in(base / "erasehistory")
    .in(stringJsonBody.example(ValuesEndpoints.Examples.deleteValue))
    .out(stringJsonBody)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Erase all old versions of a Value from the database completely and keep only the latest version. Requires SystemAdmin permissions. $linkToValuesDocumentation",
    )
}

object ValuesEndpoints {
  private[values] val layer = ZLayer.derive[ValuesEndpoints]

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
