/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.http

import akka.http.scaladsl.model._
import org.knora.webapi._

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
    def fromException(ex: Throwable): StatusCode = {
        ex match {
            // Subclasses of RequestRejectedException (which must be last in this group)
            case NotFoundException(_) => StatusCodes.NotFound
            case ForbiddenException(_) => StatusCodes.Forbidden
            case BadCredentialsException(_) => StatusCodes.Unauthorized
            case DuplicateValueException(_) => StatusCodes.BadRequest
            case OntologyConstraintException(_) => StatusCodes.BadRequest
            case EditConflictException(_) => StatusCodes.Conflict
            case RequestRejectedException(_) => StatusCodes.BadRequest

            // Subclasses of InternalServerException (which must be last in this group)
            case UpdateNotPerformedException(_) => StatusCodes.Conflict
            case InternalServerException(_) => StatusCodes.InternalServerError
            case _ => StatusCodes.InternalServerError
        }
    }

}
