/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.api.model

import zio.json.*

import java.time.Instant

/**
 * Standard error response format for V3 API endpoints.
 * This is completely independent from the existing DSP-API error handling.
 *
 * @param error     The specific error information
 * @param timestamp When the error occurred
 * @param requestId Correlation ID for request tracing
 */
final case class V3ErrorResponse(
  error: V3Error,
  timestamp: Instant,
  requestId: String,
)

/**
 * Detailed error information with structured data.
 *
 * @param code    Specific error code (e.g., "V3_PROJECT_NOT_FOUND")
 * @param message Human-readable error message
 * @param details Optional additional context or debugging information
 * @param field   Optional field name that caused the error (for validation errors)
 */
final case class V3Error(
  code: String,
  message: String,
  details: Option[String] = None,
  field: Option[String] = None,
)

/**
 * V3-specific exceptions for project-related errors.
 * These are independent from the existing DSP-API error system.
 */
sealed abstract class V3ProjectException(
  val code: String,
  val message: String,
  val details: Option[String] = None,
  val field: Option[String] = None,
  val httpStatusCode: Int = 500,
) extends Exception(message) {
  def toV3Error: V3Error                                    = V3Error(code, message, details, field)
  def toV3ErrorResponse(requestId: String): V3ErrorResponse = V3ErrorResponse(toV3Error, Instant.now(), requestId)
}

/**
 * Exception for when a project is not found.
 */
final case class V3ProjectNotFoundException(projectId: String)
    extends V3ProjectException(
      code = "V3_PROJECT_NOT_FOUND",
      message = s"Project with identifier '$projectId' was not found",
      details = Some("The project may have been deleted or the identifier is incorrect"),
      httpStatusCode = 404,
    )

/**
 * Exception for invalid project ID formats.
 */
final case class V3InvalidProjectIdException(projectId: String, reason: String)
    extends V3ProjectException(
      code = "V3_INVALID_PROJECT_ID",
      message = s"Invalid project identifier format: '$projectId'",
      details = Some(reason),
      field = Some("id"),
      httpStatusCode = 400,
    )

/**
 * Exception for service unavailability scenarios.
 */
final case class V3ServiceUnavailableException(serviceName: String, cause: String)
    extends V3ProjectException(
      code = "V3_SERVICE_UNAVAILABLE",
      message = s"Required service '$serviceName' is currently unavailable",
      details = Some(cause),
      httpStatusCode = 503,
    )

/**
 * Exception for partial data availability (when some services fail but others succeed).
 */
final case class V3PartialDataException(missingServices: List[String])
    extends V3ProjectException(
      code = "V3_PARTIAL_DATA_AVAILABLE",
      message = "Some project data is unavailable due to service failures",
      details = Some(s"Failed services: ${missingServices.mkString(", ")}"),
      httpStatusCode = 206,
    )

/**
 * Exception for SPARQL query failures.
 */
final case class V3SparqlQueryException(queryType: String, cause: String)
    extends V3ProjectException(
      code = "V3_SPARQL_QUERY_FAILED",
      message = s"Failed to execute $queryType query against triplestore",
      details = Some(cause),
      httpStatusCode = 500,
    )

/**
 * Exception for request timeouts.
 */
final case class V3RequestTimeoutException(timeoutMs: Long)
    extends V3ProjectException(
      code = "V3_TIMEOUT",
      message = s"Request timed out after ${timeoutMs}ms",
      details = Some("Try reducing the scope of your request or try again later"),
      httpStatusCode = 408,
    )

/**
 * Exception for invalid parameters.
 */
final case class V3InvalidParameterException(paramName: String, value: String, reason: String)
    extends V3ProjectException(
      code = "V3_INVALID_PARAMETER",
      message = s"Invalid parameter '$paramName': $reason",
      details = Some(s"Provided value: '$value'"),
      field = Some(paramName),
      httpStatusCode = 400,
    )

/**
 * Utility object for creating V3 errors and responses.
 */
object V3ProjectErrors {

  // Error code constants
  val PROJECT_NOT_FOUND      = "V3_PROJECT_NOT_FOUND"
  val INVALID_PROJECT_ID     = "V3_INVALID_PROJECT_ID"
  val SERVICE_UNAVAILABLE    = "V3_SERVICE_UNAVAILABLE"
  val PARTIAL_DATA_AVAILABLE = "V3_PARTIAL_DATA_AVAILABLE"
  val SPARQL_QUERY_FAILED    = "V3_SPARQL_QUERY_FAILED"
  val TIMEOUT                = "V3_TIMEOUT"
  val INVALID_PARAMETER      = "V3_INVALID_PARAMETER"

  // Factory methods for common error scenarios
  def projectNotFound(projectId: String): V3Error =
    V3Error(
      code = PROJECT_NOT_FOUND,
      message = s"Project with identifier '$projectId' was not found",
      details = Some("The project may have been deleted or the identifier is incorrect"),
    )

  def invalidProjectId(projectId: String, reason: String): V3Error =
    V3Error(
      code = INVALID_PROJECT_ID,
      message = s"Invalid project identifier format: '$projectId'",
      details = Some(reason),
      field = Some("id"),
    )

  def serviceUnavailable(serviceName: String, cause: String): V3Error =
    V3Error(
      code = SERVICE_UNAVAILABLE,
      message = s"Required service '$serviceName' is currently unavailable",
      details = Some(cause),
    )

  def partialDataAvailable(missingServices: List[String]): V3Error =
    V3Error(
      code = PARTIAL_DATA_AVAILABLE,
      message = "Some project data is unavailable due to service failures",
      details = Some(s"Failed services: ${missingServices.mkString(", ")}"),
    )

  def sparqlQueryFailed(queryType: String, cause: String): V3Error =
    V3Error(
      code = SPARQL_QUERY_FAILED,
      message = s"Failed to execute $queryType query against triplestore",
      details = Some(cause),
    )

  def requestTimeout(timeoutMs: Long): V3Error =
    V3Error(
      code = TIMEOUT,
      message = s"Request timed out after ${timeoutMs}ms",
      details = Some("Try reducing the scope of your request or try again later"),
    )

  def invalidParameter(paramName: String, value: String, reason: String): V3Error =
    V3Error(
      code = INVALID_PARAMETER,
      message = s"Invalid parameter '$paramName': $reason",
      details = Some(s"Provided value: '$value'"),
      field = Some(paramName),
    )
}

// JSON codecs for serialization
object V3ErrorResponse {
  implicit val codec: JsonCodec[V3ErrorResponse] = DeriveJsonCodec.gen[V3ErrorResponse]
}

object V3Error {
  implicit val codec: JsonCodec[V3Error] = DeriveJsonCodec.gen[V3Error]
}

object V3ProjectNotFoundException {
  implicit val codec: JsonCodec[V3ProjectNotFoundException] = DeriveJsonCodec.gen[V3ProjectNotFoundException]
}

object V3InvalidProjectIdException {
  implicit val codec: JsonCodec[V3InvalidProjectIdException] = DeriveJsonCodec.gen[V3InvalidProjectIdException]
}

object V3ServiceUnavailableException {
  implicit val codec: JsonCodec[V3ServiceUnavailableException] = DeriveJsonCodec.gen[V3ServiceUnavailableException]
}

object V3PartialDataException {
  implicit val codec: JsonCodec[V3PartialDataException] = DeriveJsonCodec.gen[V3PartialDataException]
}

object V3SparqlQueryException {
  implicit val codec: JsonCodec[V3SparqlQueryException] = DeriveJsonCodec.gen[V3SparqlQueryException]
}

object V3RequestTimeoutException {
  implicit val codec: JsonCodec[V3RequestTimeoutException] = DeriveJsonCodec.gen[V3RequestTimeoutException]
}

object V3InvalidParameterException {
  implicit val codec: JsonCodec[V3InvalidParameterException] = DeriveJsonCodec.gen[V3InvalidParameterException]
}
