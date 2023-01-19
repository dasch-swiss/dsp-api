/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.handler

import spray.json._
import zhttp.http.Http
import zhttp.http.Response

import dsp.errors.RequestRejectedException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.status.ApiStatusCodesZ

/**
 * Migrated from [[org.knora.webapi.http.handler.KnoraExceptionHandler]]
 */
object ExceptionHandlerZ {
  private val GENERIC_INTERNAL_SERVER_ERROR_MESSAGE =
    "The request could not be completed because of an internal server error."

  def exceptionToJsonHttpResponseZ(ex: Throwable, appConfig: AppConfig): Http[Any, Nothing, Any, Response] = {

    // Get the HTTP status code that corresponds to the exception.
    val httpStatus = ApiStatusCodesZ.fromExceptionZ(ex)

    // Generate an HTTP response containing the error message ...
    val responseFields: Map[String, JsValue] = Map(
      "error" -> JsString(makeClientErrorMessage(ex, appConfig))
    )

    val json = JsObject(responseFields).compactPrint

    // ... and the HTTP status code.
    Http.response(Response.json(json).setStatus(httpStatus))

  }

  /**
   * Given an exception, returns an error message suitable for clients.
   *
   * @param ex        the exception.
   * @param appConfig the application's configuration.
   * @return an error message suitable for clients.
   */
  private def makeClientErrorMessage(ex: Throwable, appConfig: AppConfig): String =
    ex match {
      case rre: RequestRejectedException => rre.toString

      case other =>
        if (appConfig.showInternalErrors) {
          other.toString
        } else {
          GENERIC_INTERNAL_SERVER_ERROR_MESSAGE
        }
    }
}
