/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.status

import zio.http.model.Status

import dsp.errors._
import org.knora.webapi.store.triplestore.errors.TriplestoreTimeoutException

/**
 * The possible values for the HTTP status code that is returned as part of each Knora API v2 response.
 * Migrated from [[org.knora.webapi.http.status.ApiStatusCodes]]
 */
object ApiStatusCodesZ {

  /**
   * Converts an exception to a suitable HTTP status code.
   *
   * @param ex an exception.
   * @return an HTTP status code.
   */

  def fromExceptionZ(ex: Throwable): Status =
    ex match {
      // Subclasses of RequestRejectedException
      case NotFoundException(_)           => Status.NotFound
      case ForbiddenException(_)          => Status.Forbidden
      case BadCredentialsException(_)     => Status.Unauthorized
      case DuplicateValueException(_)     => Status.BadRequest
      case OntologyConstraintException(_) => Status.BadRequest
      case EditConflictException(_)       => Status.Conflict
      case BadRequestException(_)         => Status.BadRequest
      case ValidationException(_, _)      => Status.BadRequest
      case RequestRejectedException(_)    => Status.BadRequest
      // RequestRejectedException must be the last one in this group

      // Subclasses of InternalServerException
      case UpdateNotPerformedException(_)    => Status.Conflict
      case TriplestoreTimeoutException(_, _) => Status.GatewayTimeout
      case InternalServerException(_)        => Status.InternalServerError
      // InternalServerException must be the last one in this group

      case _ => Status.InternalServerError
    }

}
