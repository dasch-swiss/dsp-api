/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.resources

import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer
import zio.json.*

import org.knora.webapi.config.GraphRoute
import org.knora.webapi.config.Resources
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.v2.ApiV2
import org.knora.webapi.slice.api.v2.GraphDirection
import org.knora.webapi.slice.api.v2.IriDto
import org.knora.webapi.slice.api.v2.VersionDate
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.Policy

/**
 * Request body for `POST /v2/resources/batch`: the resource IRIs to fetch. Plain JSON
 * (not JSON-LD) so the IRI list is carried in the body rather than the URL path.
 */
final case class ResourcesBatchRequest(resourceIris: List[String])
object ResourcesBatchRequest {
  given JsonCodec[ResourcesBatchRequest] = DeriveJsonCodec.gen[ResourcesBatchRequest]
}

final class ResourcesEndpoints(baseEndpoints: BaseEndpoints, graphConfig: GraphRoute, resourcesConfig: Resources) {
  private val readResources         = "ReadResourcesService, per resource and value"
  private val resourcesResponder    = "ResourcesResponderV2, per resource"
  private val searchResponder       = "SearchResponderV2, per resource"
  private val createResourceHandler = "CreateResourceV2Handler, on the project and resource class"

  private val base = "v2" / "resources"

  // Schema built from the running config so the batch cap (min 1, max = app.v2.resources.max-batch-size)
  // is enforced at the request boundary and surfaces as minItems/maxItems in the generated OpenAPI.
  private given Schema[ResourcesBatchRequest] =
    Schema
      .derived[ResourcesBatchRequest]
      .modify(_.resourceIris)(
        _.validate(Validator.minSize(1)).validate(Validator.maxSize(resourcesConfig.maxBatchSize)),
      )

  private val versionQuery = query[Option[VersionDate]]("version")
    .and(query[Option[VersionDate]]("version date"))
    .map {
      case (Some(v), _) => Some(v)
      case (_, Some(v)) => Some(v)
      case _            => None
    }(d => (d, d))

  private val startDateQuery = query[Option[VersionDate]]("startDate")
    .and(query[Option[VersionDate]]("start date"))
    .map {
      case (Some(v), _) => Some(v)
      case (_, Some(v)) => Some(v)
      case _            => None
    }(d => (d, d))

  private val endDateQuery = query[Option[VersionDate]]("endDate")
    .and(query[Option[VersionDate]]("end date"))
    .map {
      case (Some(v), _) => Some(v)
      case (_, Some(v)) => Some(v)
      case _            => None
    }(d => (d, d))

  val getResourcesPreview = baseEndpoints
    .objectAccessPolicyEndpoint(readResources)
    .get
    .in("v2" / "resourcespreview" / paths)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Get a preview of one or more resources. ${Policy.objectReadRequirement}",
    )

  val getResourcesIiifManifest = baseEndpoints
    .objectAccessPolicyEndpoint(resourcesResponder)
    .get
    .in(base / "iiifmanifest" / path[IriDto].name("resourceIri"))
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Get the IIIF manifest for a resource. ${Policy.objectReadRequirement}",
    )

  val getResourcesProjectHistoryEvents = baseEndpoints
    .objectAccessPolicyEndpoint(resourcesResponder)
    .get
    .in(base / "projectHistoryEvents" / path[ProjectIri].name("projectIri"))
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Get history events for all resources in a project. Resources the requesting user cannot view are omitted. ${Policy.objectReadRequirement}",
    )

  val getResourcesHistoryEvents = baseEndpoints
    .objectAccessPolicyEndpoint(resourcesResponder)
    .get
    .in(base / "resourceHistoryEvents" / path[IriDto].name("resourceIri"))
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Get history events for a specific resource. ${Policy.objectReadRequirement}",
    )

  val getResourcesHistory = baseEndpoints
    .objectAccessPolicyEndpoint(resourcesResponder)
    .get
    .in(base / "history" / path[IriDto].name("resourceIri"))
    .in(ApiV2.Inputs.formatOptions)
    .in(startDateQuery)
    .in(endDateQuery)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Get the version history of a resource. ${Policy.objectReadRequirement}",
    )

  val getResources = baseEndpoints
    .objectAccessPolicyEndpoint(readResources)
    .get
    .in(base / paths)
    .in(ApiV2.Inputs.formatOptions)
    .in(versionQuery)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Get one or more resources. ${Policy.objectReadRequirement}",
    )

  val postResourcesBatch = baseEndpoints
    .objectAccessPolicyEndpoint(readResources)
    .post
    .in(base / "batch")
    .in(jsonBody[ResourcesBatchRequest])
    .in(ApiV2.Inputs.formatOptions)
    .in(versionQuery)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Fetch one or more resources by IRI, supplied as a JSON body `{\"resourceIris\": [...]}`. " +
        "A POST alternative to GET /v2/resources for large IRI sets that would exceed URL-length limits. " +
        s"Between 1 and ${resourcesConfig.maxBatchSize} IRIs may be sent per request (this deployment's " +
        "configured limit; exceeding it is a 400). " +
        Policy.objectReadRequirement,
    )

  val getResourcesParams = baseEndpoints
    .objectAccessPolicyEndpoint(searchResponder)
    .get
    .in(base)
    .in(query[IriDto]("resourceClass"))
    .in(query[Option[IriDto]]("orderByProperty"))
    .in(query[Int]("page").validate(Validator.min(0)))
    .in(header[ProjectIri](ApiV2.Headers.xKnoraAcceptProject))
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Search for resources by class and project. ${Policy.objectReadRequirement}",
    )

  val getResourcesGraph = baseEndpoints
    .objectAccessPolicyEndpoint(resourcesResponder)
    .get
    .in("v2" / "graph" / path[IriDto].name("resourceIri"))
    .in(ApiV2.Inputs.formatOptions)
    .in(
      query[Int]("depth")
        .validate(Validator.min(1))
        .validate(Validator.max(graphConfig.maxGraphDepth))
        .default(graphConfig.defaultGraphDepth),
    )
    .in(query[GraphDirection]("direction").default(GraphDirection.default))
    .in(query[Option[IriDto]]("excludeProperty"))
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Get a graph of resources starting from a specific resource. ${Policy.objectReadRequirement}",
    )

  val getResourcesTei = baseEndpoints
    .objectAccessPolicyEndpoint(resourcesResponder)
    .get
    .in("v2" / "tei" / path[IriDto].name("resourceIri"))
    .in(query[Option[IriDto]]("mappingIri"))
    .in(query[IriDto]("textProperty"))
    .in(query[Option[IriDto]]("gravsearchTemplateIri"))
    .in(query[Option[IriDto]]("headerXSLTIri"))
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Get a resource as TEI/XML. ${Policy.objectReadRequirement}",
    )

  val postResourcesErase = baseEndpoints
    .projectAdminPolicyEndpoint("body resource IRI's project")
    .post
    .in(base / "erase")
    .in(ApiV2.Inputs.formatOptions)
    .in(stringJsonBody)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Erase a Resource and all of its values from the database completely. " +
        Policy.ProjectAdmin("body resource IRI's project").requirement,
    )

  val getResourcesCanDelete = baseEndpoints
    .objectAccessPolicyEndpoint(resourcesResponder)
    .get
    .in(base / "candelete")
    .in(ApiV2.Inputs.formatOptions)
    .in(query[String]("jsonLd"))
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Check if a resource can be deleted. ${Policy.objectReadRequirement}",
    )

  val postResourcesDelete = baseEndpoints
    .authenticatedObjectAccessPolicyEndpoint(resourcesResponder)
    .post
    .in(base / "delete")
    .in(ApiV2.Inputs.formatOptions)
    .in(stringJsonBody)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(s"Mark a resource as deleted. ${Policy.objectWriteRequirement}")

  val postResources = baseEndpoints
    .authenticatedObjectAccessPolicyEndpoint(createResourceHandler)
    .post
    .in(base)
    .in(ApiV2.Inputs.formatOptions)
    .in(stringJsonBody)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(s"Create a new resource. ${Policy.objectWriteRequirement}")

  val putResources = baseEndpoints
    .authenticatedObjectAccessPolicyEndpoint(resourcesResponder)
    .put
    .in(base)
    .in(ApiV2.Inputs.formatOptions)
    .in(stringJsonBody)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Update resource metadata. Requires Modify object access permission on the resource, " +
        "and ChangeRights if the request changes `knora-api:hasPermissions`. " + Policy.objectWriteRequirement,
    )

  val putResourcesAuthorship = baseEndpoints
    .authenticatedObjectAccessPolicyEndpoint(resourcesResponder)
    .put
    .in(base / "authorship")
    .in(ApiV2.Inputs.formatOptions)
    .in(stringJsonBody)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      s"Update a resource's authorship (data side). Requires Modify object access permission on the resource. ${Policy.objectWriteRequirement}",
    )

  val endpoints: Seq[AnyEndpoint] = Seq(
    getResourcesIiifManifest,
    getResourcesPreview,
    getResourcesProjectHistoryEvents,
    getResourcesHistoryEvents,
    getResourcesHistory,
    getResources,
    postResourcesBatch,
    getResourcesParams,
    getResourcesGraph,
    getResourcesTei,
    getResourcesCanDelete,
    postResourcesErase,
    postResourcesDelete,
    postResources,
    putResources,
    putResourcesAuthorship,
  ).map(_.endpoint).map(_.tag("V2 Resources"))
}

object ResourcesEndpoints {
  private[resources] val layer = ZLayer.derive[ResourcesEndpoints]
}
