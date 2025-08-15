/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.api.model

import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody

/**
 * Common reusable error out type definitions for V3 API endpoints.
 * These error types can be composed together to avoid duplication across endpoints.
 */
object V3CommonErrors {

  /**
   * Standard V3 error variant for bad requests (400).
   * Covers invalid parameter formats, validation errors, etc.
   */
  val badRequestError: EndpointOutput.OneOfVariant[V3ErrorResponse] =
    oneOfVariant(
      StatusCode.BadRequest,
      jsonBody[V3ErrorResponse]
        .description("Invalid request parameter")
        .example(V3ErrorExamples.invalidProjectId),
    )

  /**
   * Standard V3 error variant for not found (404).
   * Covers missing resources, projects, etc.
   */
  val notFoundError: EndpointOutput.OneOfVariant[V3ErrorResponse] =
    oneOfVariant(
      StatusCode.NotFound,
      jsonBody[V3ErrorResponse]
        .description("Resource not found")
        .example(V3ErrorExamples.projectNotFound),
    )

  /**
   * Standard V3 error variant for request timeout (408).
   * Covers slow queries, external service timeouts, etc.
   */
  val timeoutError: EndpointOutput.OneOfVariant[V3ErrorResponse] =
    oneOfVariant(
      StatusCode.RequestTimeout,
      jsonBody[V3ErrorResponse]
        .description("Request timeout")
        .example(V3ErrorExamples.requestTimeout),
    )

  /**
   * Standard V3 error variant for internal server errors (500).
   * Covers SPARQL failures, unexpected exceptions, etc.
   */
  val internalServerError: EndpointOutput.OneOfVariant[V3ErrorResponse] =
    oneOfVariant(
      StatusCode.InternalServerError,
      jsonBody[V3ErrorResponse]
        .description("Internal server error")
        .example(V3ErrorExamples.sparqlQueryFailed),
    )

  /**
   * Standard V3 error variant for service unavailable (503).
   * Covers external service failures, maintenance, etc.
   */
  val serviceUnavailableError: EndpointOutput.OneOfVariant[V3ErrorResponse] =
    oneOfVariant(
      StatusCode.ServiceUnavailable,
      jsonBody[V3ErrorResponse]
        .description("Service unavailable")
        .example(V3ErrorExamples.serviceUnavailable),
    )

  /**
   * Standard V3 error variant for partial content (206).
   * Used when some data is available but some services failed.
   */
  val partialDataError: EndpointOutput.OneOfVariant[V3ErrorResponse] =
    oneOfVariant(
      StatusCode.PartialContent,
      jsonBody[V3ErrorResponse]
        .description("Partial data available")
        .example(V3ErrorExamples.partialDataAvailable),
    )

  /**
   * Complete error out type using common V3 error variants.
   * Use this for endpoints that need standard error handling.
   */
  val commonV3ErrorOut: EndpointOutput.OneOf[V3ErrorResponse, V3ErrorResponse] =
    oneOf[V3ErrorResponse](
      badRequestError,
      notFoundError,
      timeoutError,
      internalServerError,
      serviceUnavailableError,
    )

  /**
   * Complete error out type including partial data support.
   * Use this for endpoints like project info that can gracefully degrade.
   */
  val extendedV3ErrorOut: EndpointOutput.OneOf[V3ErrorResponse, V3ErrorResponse] =
    oneOf[V3ErrorResponse](
      badRequestError,
      notFoundError,
      timeoutError,
      internalServerError,
      serviceUnavailableError,
      partialDataError,
    )

  /**
   * Minimal error out type for simple endpoints.
   * Use this for lightweight endpoints with basic error handling needs.
   */
  val minimalV3ErrorOut: EndpointOutput.OneOf[V3ErrorResponse, V3ErrorResponse] =
    oneOf[V3ErrorResponse](
      badRequestError,
      notFoundError,
      internalServerError,
    )
}
