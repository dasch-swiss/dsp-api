/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.api.model

import java.time.Instant

/**
 * Centralized V3 error response examples for OpenAPI documentation.
 * These examples are used in endpoint error out definitions to provide
 * consistent documentation across all V3 API endpoints.
 */
object V3ErrorExamples {

  private val exampleTimestamp = Instant.parse("2025-01-31T12:30:00Z")

  /**
   * Example for V3_INVALID_PROJECT_ID error (400).
   */
  val invalidProjectId: V3ErrorResponse =
    V3ErrorResponse(
      error = V3Error(
        code = "V3_INVALID_PROJECT_ID",
        message = "Invalid project shortcode format: 'ABC123'",
        details = Some("Project shortcode must be a 4-character hexadecimal code"),
        field = Some("shortcode"),
      ),
      timestamp = exampleTimestamp,
      requestId = "req-uuid-12345",
    )

  /**
   * Example for V3_PROJECT_NOT_FOUND error (404).
   */
  val projectNotFound: V3ErrorResponse =
    V3ErrorResponse(
      error = V3Error(
        code = "V3_PROJECT_NOT_FOUND",
        message = "Project with identifier '9999' was not found",
        details = Some("The project may have been deleted or the identifier is incorrect"),
        field = None,
      ),
      timestamp = exampleTimestamp,
      requestId = "req-uuid-67890",
    )

  /**
   * Example for V3_TIMEOUT error (408).
   */
  val requestTimeout: V3ErrorResponse =
    V3ErrorResponse(
      error = V3Error(
        code = "V3_TIMEOUT",
        message = "Request timed out after 30000ms",
        details = Some("Try reducing the scope of your request or try again later"),
        field = None,
      ),
      timestamp = exampleTimestamp,
      requestId = "req-uuid-timeout",
    )

  /**
   * Example for V3_SPARQL_QUERY_FAILED error (500).
   */
  val sparqlQueryFailed: V3ErrorResponse =
    V3ErrorResponse(
      error = V3Error(
        code = "V3_SPARQL_QUERY_FAILED",
        message = "Failed to execute instance count query against triplestore",
        details = Some("Connection to triplestore failed or query syntax error"),
        field = None,
      ),
      timestamp = exampleTimestamp,
      requestId = "req-uuid-error",
    )

  /**
   * Example for V3_SERVICE_UNAVAILABLE error (503).
   */
  val serviceUnavailable: V3ErrorResponse =
    V3ErrorResponse(
      error = V3Error(
        code = "V3_SERVICE_UNAVAILABLE",
        message = "Required service 'TriplestoreService' is currently unavailable",
        details = Some("Service is temporarily down for maintenance or experiencing high load"),
        field = None,
      ),
      timestamp = exampleTimestamp,
      requestId = "req-uuid-unavailable",
    )

  /**
   * Example for V3_PARTIAL_DATA_AVAILABLE error (206).
   */
  val partialDataAvailable: V3ErrorResponse =
    V3ErrorResponse(
      error = V3Error(
        code = "V3_PARTIAL_DATA_AVAILABLE",
        message = "Some project data is unavailable due to service failures",
        details = Some("Failed services: TriplestoreService"),
        field = None,
      ),
      timestamp = exampleTimestamp,
      requestId = "req-uuid-partial",
    )

  /**
   * Example for V3_INVALID_PARAMETER error (400).
   * Generic parameter validation error.
   */
  val invalidParameter: V3ErrorResponse =
    V3ErrorResponse(
      error = V3Error(
        code = "V3_INVALID_PARAMETER",
        message = "Invalid parameter 'limit': must be between 1 and 100",
        details = Some("Provided value: '150'"),
        field = Some("limit"),
      ),
      timestamp = exampleTimestamp,
      requestId = "req-uuid-param",
    )
}
