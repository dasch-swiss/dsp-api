/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.status

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes
import dsp.errors._
import org.knora.webapi.store.triplestore.errors.TriplestoreTimeoutException

/**
 * The possible values for the HTTP status code that is returned as part of each Knora API v2 response.
 */
object ApiStatusCodesV2 {

  /**
   * Converts an exception to a similar HTTP status code.
   *
   * @param ex an exception.
   * @return an HTTP status code.
   */
  def fromException(ex: Throwable): StatusCode =
    ex match {
      // Subclasses of RequestRejectedException
      case NotFoundException(_)           => StatusCodes.NotFound
      case ForbiddenException(_)          => StatusCodes.Forbidden
      case BadCredentialsException(_)     => StatusCodes.Unauthorized
      case DuplicateValueException(_)     => StatusCodes.BadRequest
      case OntologyConstraintException(_) => StatusCodes.BadRequest
      case EditConflictException(_)       => StatusCodes.Conflict
      case BadRequestException(_)         => StatusCodes.BadRequest
      case ValidationException(_, _)      => StatusCodes.BadRequest
      case RequestRejectedException(_)    => StatusCodes.BadRequest
      // RequestRejectedException must be the last one in this group

      // Subclasses of InternalServerException
      case UpdateNotPerformedException(_)    => StatusCodes.Conflict
      case TriplestoreTimeoutException(_, _) => StatusCodes.GatewayTimeout
      case InternalServerException(_)        => StatusCodes.InternalServerError
      // InternalServerException must be the last one in this group

      case _ => StatusCodes.InternalServerError
    }

}
