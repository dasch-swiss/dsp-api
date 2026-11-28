/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.values

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer

import org.knora.webapi.slice.api.v2.ApiV2
import org.knora.webapi.slice.api.v2.ValueUuid
import org.knora.webapi.slice.api.v2.VersionDate
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.Policy

final class ValuesEndpoints(baseEndpoint: BaseEndpoints) {
  private val readResources = "ReadResourcesService, per resource and value"
  private val objectAccess  = "ObjectAccess, per resource and value"

  private val base: EndpointInput[Unit] = "v2" / "values"

  private val resourceIri = path[String].name("resourceIri").description("The IRI of a Resource.")
  private val valueUuid   = path[ValueUuid].name("valueUuid").description("The UUID of a Value.")
  private val version     = query[Option[VersionDate]]("version")

  private val eraseRequirement =
    s"${Policy.SystemAdmin.requirement} The resource must belong to the project its IRI's shortcode names."

  private val linkToValuesDocumentation =
    """Find detailed documentation on <a href="https://docs.dasch.swiss/DSP-API/03-endpoints/api-v2/editing-values/">docs.dasch.swiss</a>."""

  val getValue = baseEndpoint
    .objectAccessPolicyEndpoint(readResources)
    .get
    .in(base / resourceIri / valueUuid)
    .in(version)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringJsonBody)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Get a value of a resource. ${Policy.objectReadRequirement} $linkToValuesDocumentation",
    )

  val postValues = baseEndpoint
    .authenticatedObjectAccessPolicyEndpoint(objectAccess)
    .post
    .in(base)
    .in(
      stringJsonBody.example(
        "{\n  \"@id\": \"http://rdfh.ch/0001/a-thing\",\n  \"@type\": \"anything:Thing\",\n  \"anything:hasInteger\": {\n    \"@type\": \"knora-api:IntValue\",\n    \"knora-api:intValueAsInt\": 4\n  },\n  \"@context\": {\n    \"knora-api\": \"http://api.knora.org/ontology/knora-api/v2#\",\n    \"anything\": \"http://0.0.0.0:3333/ontology/0001/anything/v2#\"\n  }\n}",
      ),
    )
    .out(stringJsonBody)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Create a new value for a resource. Requires view and modify permission on the resource. ${Policy.objectWriteRequirement} $linkToValuesDocumentation",
    )

  val putValuesOrder = baseEndpoint
    .authenticatedObjectAccessPolicyEndpoint(objectAccess)
    .put
    .in(base / "order")
    .in(
      jsonBody[ReorderValuesRequest].example(
        ReorderValuesRequest(
          resourceIri = "http://rdfh.ch/0001/resource-1",
          propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText",
          orderedValueIris =
            List("http://rdfh.ch/0001/value-3", "http://rdfh.ch/0001/value-1", "http://rdfh.ch/0001/value-2"),
        ),
      ),
    )
    .out(jsonBody[ReorderValuesResponse])
    .description(
      s"Reorder the values of a property of a resource. Requires view and modify permission on the resource. The request lists the values the caller can see, in their new order; they are permuted among their own valueHasOrder slots, a value without a slot gets a new one after the highest, and values hidden from the caller keep their slots. ${Policy.objectWriteRequirement} $linkToValuesDocumentation",
    )

  val putValues = baseEndpoint
    .authenticatedObjectAccessPolicyEndpoint(objectAccess)
    .put
    .in(base)
    .in(
      stringJsonBody.example(
        "{\n  \"@id\": \"http://rdfh.ch/0001/a-thing\",\n  \"@type\": \"anything:Thing\",\n  \"anything:hasInteger\": {\n    \"@id\": \"http://rdfh.ch/0001/a-thing/values/vp96riPIRnmQcbMhgpv_Rg\",\n    \"@type\": \"knora-api:IntValue\",\n    \"knora-api:intValueAsInt\": 5\n  },\n  \"@context\": {\n    \"knora-api\": \"http://api.knora.org/ontology/knora-api/v2#\",\n    \"anything\": \"http://0.0.0.0:3333/ontology/0001/anything/v2#\"\n  }\n}",
      ),
    )
    .out(stringJsonBody)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Update a value of a resource. Requires view permission on the resource and modify permission on the value, or change rights permission on the value to change its permissions, alone or together with its content; a link update also requires modify permission on the resource. ${Policy.objectWriteRequirement} $linkToValuesDocumentation",
    )

  val deleteValues = baseEndpoint
    .authenticatedObjectAccessPolicyEndpoint(objectAccess)
    .post
    .in(base / "delete")
    .in(stringJsonBody.example(ValuesEndpoints.Examples.deleteValue))
    .out(stringJsonBody)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Mark a value as deleted. Requires view permission on the resource and delete permission on the value. ${Policy.objectWriteRequirement} $linkToValuesDocumentation",
    )

  val postValuesErase = baseEndpoint.systemAdminPolicyEndpoint.post
    .in(base / "erase")
    .in(stringJsonBody.example(ValuesEndpoints.Examples.deleteValue))
    .out(stringJsonBody)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Erase a Value and all of its old versions from the database completely. $eraseRequirement $linkToValuesDocumentation",
    )

  val postValuesErasehistory = baseEndpoint.systemAdminPolicyEndpoint.post
    .in(base / "erasehistory")
    .in(stringJsonBody.example(ValuesEndpoints.Examples.deleteValue))
    .out(stringJsonBody)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Erase all old versions of a Value from the database completely and keep only the latest version. $eraseRequirement $linkToValuesDocumentation",
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
