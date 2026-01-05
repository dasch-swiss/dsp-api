/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices
import sttp.client4.*
import sttp.model.*
import zio.*

final case class ResponseError(message: String) extends Exception(message)
object ResponseError {
  def from(expected: StatusCode, response: Response[Either[String, Any]]): ResponseError = {
    val error = response.body.fold(error => s"Error body: $error", any => s"Success body: $any")
    ResponseError(s"Expected $expected but got ${response.code}, error response: $error")
  }
}

object ResponseOps {
  extension [A](r: Response[Either[String, A]]) {
    def assert200: IO[ResponseError, A] =
      (r.body, r.code) match
        case (Left(error), StatusCode.Ok) => ZIO.fail(ResponseError(s"Expected 200 OK but got an error: $error"))
        case (Right(data), StatusCode.Ok) => ZIO.succeed(data)
        case _                            => ZIO.fail(ResponseError.from(StatusCode.Ok, r))

    def assert400: IO[ResponseError, String] =
      (r.body, r.code) match
        case (Left(error), StatusCode.BadRequest) => ZIO.succeed(error)
        case (Right(_), StatusCode.BadRequest)    =>
          ZIO.fail(ResponseError("Expected 400 Bad Request but got a successful response"))
        case _ => ZIO.fail(ResponseError.from(StatusCode.BadRequest, r))

    def assert404: IO[ResponseError, String] =
      (r.body, r.code) match
        case (Left(error), StatusCode.NotFound) => ZIO.succeed(error)
        case (Right(_), StatusCode.NotFound)    =>
          ZIO.fail(ResponseError("Expected 404 Not Found but got a successful response"))
        case _ => ZIO.fail(ResponseError.from(StatusCode.NotFound, r))

  }
}
