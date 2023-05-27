/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.handler

import org.slf4j.LoggerFactory
import spray.json._
import zio.http._

import dsp.errors.RequestRejectedException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.status.ApiStatusCodesZ

/**
 * Migrated from [[org.knora.webapi.http.handler.KnoraExceptionHandler]]
 */
object ExceptionHandlerZ {
  private val GENERIC_INTERNAL_SERVER_ERROR_MESSAGE =
    "The request could not be completed because of an internal server error."

  private val logger = LoggerFactory.getLogger(ExceptionHandlerZ.getClass)

  def exceptionToJsonHttpResponseZ(ex: Throwable, appConfig: AppConfig): Http[Any, Nothing, Any, Response] = {

    // Get the HTTP status code that corresponds to the exception.
    val httpStatus = ApiStatusCodesZ.fromExceptionZ(ex)
    if (httpStatus.code == 500) {
      logger.error(ex.getMessage, ex)
    }

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
   * @param throwable        the exception.
   * @param appConfig the application's configuration.
   * @return an error message suitable for clients.
   */
  private def makeClientErrorMessage(throwable: Throwable, appConfig: AppConfig): String =
    throwable match {
      case knownError: RequestRejectedException => knownError.toString
      case other =>
        if (appConfig.showInternalErrors) { other.toString }
        else { GENERIC_INTERNAL_SERVER_ERROR_MESSAGE }
    }
}
