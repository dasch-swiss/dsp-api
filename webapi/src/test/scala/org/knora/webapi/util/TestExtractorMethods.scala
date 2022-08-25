/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import spray.json.JsString

import scala.concurrent.ExecutionContext

import dsp.errors.InvalidApiJsonException

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
  def getResIriFromJsonResponse(response: HttpResponse)(implicit ec: ExecutionContext, system: ActorSystem) =
    AkkaHttpUtils.httpResponseToJson(response).fields.get("res_id") match {
      case Some(JsString(resourceId)) => resourceId
      case None                       => throw InvalidApiJsonException(s"The response does not contain a field called 'res_id'")
      case other =>
        throw InvalidApiJsonException(s"The response does not contain a res_id of type JsString, but ${other}")
    }

}

object ValuesResponseExtractorMethods {

  /**
   * Gets the field `id` from a JSON response to value creation (new value).
   *
   * @param response the response sent back from the API.
   * @return the value of `res_id`.
   */
  def getNewValueIriFromJsonResponse(response: HttpResponse)(implicit ec: ExecutionContext, system: ActorSystem) =
    AkkaHttpUtils.httpResponseToJson(response).fields.get("id") match {
      case Some(JsString(resourceId)) => resourceId
      case None                       => throw InvalidApiJsonException(s"The response does not contain a field called 'res_id'")
      case other =>
        throw InvalidApiJsonException(s"The response does not contain a res_id of type JsString, but $other")
    }

}
