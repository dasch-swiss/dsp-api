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
  * The possible values for the status code that is returned as part of each Knora API v1 response.
  * Based on `salsah/api/ApiErrors.php`.
  */
object ApiStatusCodesV1 extends Enumeration {
    val OK = Value(0)
    val INVALID_REQUEST_METHOD = Value(1)
    val CREDENTIALS_NOT_VALID = Value(2)
    val NO_RIGHTS_FOR_OPERATION = Value(3)
    val INTERNAL_SALSAH_ERROR = Value(4)
    val NO_PROPERTIES = Value(5)
    val NOT_IN_USERDATA = Value(6)
    val RESOURCE_ID_MISSING = Value(7)
    val UNKNOWN_VOCABULARY = Value(8)

    /**
      * The requested item was not found. This was called NO_NODES_FOUND in SALSAH; its meaning has been broadened here.
      */
    val NOT_FOUND = Value(9)

    val API_ENDPOINT_NOT_FOUND = Value(10)
    val INVALID_REQUEST_TYPE = Value(11)
    val PROPERTY_ID_MISSING = Value(12)
    val NOT_YET_IMPLEMENTED = Value(13)
    val COULD_NOT_OPEN_PROGRESS_FILE = Value(14)
    val VALUE_ID_OR_RESTYPE_ID_MISSING = Value(15)

    val HLIST_ALREADY_EXISTENT = Value(16)
    val HLIST_NO_LABELS = Value(17)
    val HLIST_NOT_EXISTING = Value(18)
    val HLIST_NO_POSITION = Value(19)
    val HLIST_INVALID_POSITION = Value(20)

    val SELECTION_NO_LABELS = Value(21)
    val SELECTION_ALREADY_EXISTENT = Value(22)
    val SELECTION_MISSING_OR_INVALID_POSITION = Value(23)
    val SELECTION_DELETE_FAILED = Value(24)
    val SELECTION_NODE_ALREADY_EXISTENT = Value(25)

    val GEONAMES_GEONAME_ID_EXISTING = Value(26)

    /**
      * The requested update was not performed, perhaps because it was based on outdated information (e.g. because of an edit conflict). (New in Knora.)
      */
    val UPDATE_NOT_PERFORMED = Value(27)

    /**
      * The requested update was not performed, because it would have created a duplicate value. (New in Knora.)
      */
    val DUPLICATE_VALUE = Value(28)

    /**
      * The requested update was not performed, because it would have violated an ontology constraint. (New in Knora.)
      */
    val ONTOLOGY_CONSTRAINT = Value(29)

    val UNSPECIFIED_ERROR = Value(999)

    /**
      * Converts an exception to a similar API status code.
      *
      * @param ex an exception.
      * @return an API status code.
      */
    def fromException(ex: Throwable): Value = {
        ex match {
            // Subclasses of RequestRejectedException (which must be last in this group)
            case NotFoundException(_) => ApiStatusCodesV1.NOT_FOUND
            case ForbiddenException(_) => ApiStatusCodesV1.NO_RIGHTS_FOR_OPERATION
            case BadCredentialsException(_) => ApiStatusCodesV1.CREDENTIALS_NOT_VALID
            case DuplicateValueException(_) => ApiStatusCodesV1.DUPLICATE_VALUE
            case OntologyConstraintException(_) => ApiStatusCodesV1.ONTOLOGY_CONSTRAINT
            case EditConflictException(_) => ApiStatusCodesV1.UPDATE_NOT_PERFORMED
            case RequestRejectedException(_) => ApiStatusCodesV1.INVALID_REQUEST_TYPE

            // Subclasses of InternalServerException (which must be last in this group)
            case UpdateNotPerformedException(_) => ApiStatusCodesV1.UPDATE_NOT_PERFORMED
            case InternalServerException(_) => ApiStatusCodesV1.INTERNAL_SALSAH_ERROR
            case _ => ApiStatusCodesV1.INTERNAL_SALSAH_ERROR
        }
    }

    /**
      * Converts an API status code to a similar HTTP status code.
      *
      * @param apiStatus an API status code.
      * @return an HTTP status code.
      */
    def toHttpStatus(apiStatus: Value): StatusCode = {
        apiStatus match {
            case OK => StatusCodes.OK
            case INVALID_REQUEST_METHOD => StatusCodes.MethodNotAllowed
            case CREDENTIALS_NOT_VALID => StatusCodes.Unauthorized
            case NO_RIGHTS_FOR_OPERATION => StatusCodes.Forbidden
            case INTERNAL_SALSAH_ERROR => StatusCodes.InternalServerError
            case NO_PROPERTIES => StatusCodes.BadRequest
            case NOT_IN_USERDATA => StatusCodes.Unauthorized
            case RESOURCE_ID_MISSING => StatusCodes.BadRequest
            case UNKNOWN_VOCABULARY => StatusCodes.BadRequest
            case NOT_FOUND => StatusCodes.NotFound
            case API_ENDPOINT_NOT_FOUND => StatusCodes.NotFound
            case INVALID_REQUEST_TYPE => StatusCodes.BadRequest
            case PROPERTY_ID_MISSING => StatusCodes.BadRequest
            case NOT_YET_IMPLEMENTED => StatusCodes.NotImplemented
            case COULD_NOT_OPEN_PROGRESS_FILE => StatusCodes.InternalServerError
            case VALUE_ID_OR_RESTYPE_ID_MISSING => StatusCodes.BadRequest
            case HLIST_ALREADY_EXISTENT => StatusCodes.BadRequest
            case HLIST_NO_LABELS => StatusCodes.BadRequest
            case HLIST_NOT_EXISTING => StatusCodes.NotFound
            case HLIST_NO_POSITION => StatusCodes.BadRequest
            case HLIST_INVALID_POSITION => StatusCodes.BadRequest
            case SELECTION_NO_LABELS => StatusCodes.BadRequest
            case SELECTION_ALREADY_EXISTENT => StatusCodes.BadRequest
            case SELECTION_MISSING_OR_INVALID_POSITION => StatusCodes.BadRequest
            case SELECTION_DELETE_FAILED => StatusCodes.InternalServerError
            case SELECTION_NODE_ALREADY_EXISTENT => StatusCodes.BadRequest
            case GEONAMES_GEONAME_ID_EXISTING => StatusCodes.BadRequest
            case ONTOLOGY_CONSTRAINT => StatusCodes.BadRequest
            case DUPLICATE_VALUE => StatusCodes.BadRequest
            case UPDATE_NOT_PERFORMED => StatusCodes.Conflict
            case UNSPECIFIED_ERROR => StatusCodes.InternalServerError
        }
    }
}
