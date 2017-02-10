/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.util

import akka.actor.{Actor, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.HttpResponse
import org.knora.webapi.InvalidApiJsonException
import spray.json.JsString

import scala.concurrent.ExecutionContext

/**
  * Created by subotic on 06.02.17.
  */
object ResourceResponseExtractorMethods {

    /**
      * Gets the field `res_id` from a JSON response to resource creation.
      *
      * @param response the response sent back from the API.
      * @return the value of `res_id`.
      */
    def getResIriFromJsonResponse(response: HttpResponse)(implicit ec: ExecutionContext, system: ActorSystem, log: LoggingAdapter) = {

        AkkaHttpUtils.httpResponseToJson(response).fields.get("res_id") match {
            case Some(JsString(resourceId)) => resourceId
            case None => throw InvalidApiJsonException(s"The response does not contain a field called 'res_id'")
            case other => throw InvalidApiJsonException(s"The response does not contain a res_id of type JsString, but ${other}")
        }


    }

}

object ValuesResponseExtractorMethods {
    /**
      * Gets the field `id` from a JSON response to value creation (new value).
      *
      * @param response the response sent back from the API.
      * @return the value of `res_id`.
      */
    def getNewValueIriFromJsonResponse(response: HttpResponse)(implicit ec: ExecutionContext, system: ActorSystem, log: LoggingAdapter) = {

        AkkaHttpUtils.httpResponseToJson(response).fields.get("id") match {
            case Some(JsString(resourceId)) => resourceId
            case None => throw InvalidApiJsonException(s"The response does not contain a field called 'res_id'")
            case other => throw InvalidApiJsonException(s"The response does not contain a res_id of type JsString, but $other")
        }

    }
}
