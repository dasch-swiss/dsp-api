/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.handler

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.extractRequest
import akka.http.scaladsl.server.ExceptionHandler
import com.typesafe.scalalogging.LazyLogging
import spray.json.JsNumber
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue

import dsp.errors.InternalServerException
import dsp.errors.RequestRejectedException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.status.ApiStatusCodesV1
import org.knora.webapi.http.status.ApiStatusCodesV2
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.messages.util.rdf.JsonLDString

/**
 * The Knora exception handler is used by akka-http to convert any exceptions thrown during route processing
 * into HttpResponses. It is brought implicitly into scope by the application actor.
 */
object KnoraExceptionHandler extends LazyLogging {

  // A generic error message that we return to clients when an internal server error occurs.
  private val GENERIC_INTERNAL_SERVER_ERROR_MESSAGE =
    "The request could not be completed because of an internal server error."

  def apply(appConfig: AppConfig): ExceptionHandler = ExceptionHandler {

    /* TODO: Find out which response format should be generated, by looking at what the client is requesting / accepting (issue #292) */

    case rre: RequestRejectedException =>
      extractRequest { request =>
        val url = request.uri.path.toString

        if (url.startsWith("/v1")) {
          complete(exceptionToJsonHttpResponseV1(rre, appConfig))
        } else if (url.startsWith("/v2")) {
          complete(exceptionToJsonHttpResponseV2(rre, appConfig))
        } else {
          complete(exceptionToJsonHttpResponseADM(rre, appConfig))
        }
      }

    case ise: InternalServerException =>
      extractRequest { request =>
        val uri = request.uri
        val url = uri.path.toString

        logger.error(s"Internal Server Exception: Unable to run route $url", ise)

        if (url.startsWith("/v1")) {
          complete(exceptionToJsonHttpResponseV1(ise, appConfig))
        } else if (url.startsWith("/v2")) {
          complete(exceptionToJsonHttpResponseV2(ise, appConfig))
        } else {
          complete(exceptionToJsonHttpResponseADM(ise, appConfig))
        }
      }

    case other =>
      extractRequest { request =>
        val uri = request.uri
        val url = uri.path.toString

        logger.debug(s"KnoraExceptionHandler - case: other - url: $url")
        logger.error(s"Unable to run route $url", other)

        if (url.startsWith("/v1")) {
          complete(exceptionToJsonHttpResponseV1(other, appConfig))
        } else if (url.startsWith("/v2")) {
          complete(exceptionToJsonHttpResponseV2(other, appConfig))
        } else {
          complete(exceptionToJsonHttpResponseADM(other, appConfig))
        }
      }
  }

  /**
   * Converts an exception to an HTTP response in JSON format specific to `V1`.
   *
   * @param ex the exception to be converted.
   * @return an [[HttpResponse]] in JSON format.
   */
  private def exceptionToJsonHttpResponseV1(ex: Throwable, appConfig: AppConfig): HttpResponse = {
    // Get the API status code that corresponds to the exception.
    val apiStatus: ApiStatusCodesV1.Value = ApiStatusCodesV1.fromException(ex)

    // Convert the API status code to the corresponding HTTP status code.
    val httpStatus: StatusCode = ApiStatusCodesV1.toHttpStatus(apiStatus)

    // Generate an HTTP response containing the error message, the API status code, and the HTTP status code.

    // this is a special case where we need to add 'access'
    val maybeAccess: Option[(String, JsValue)] = if (apiStatus == ApiStatusCodesV1.NO_RIGHTS_FOR_OPERATION) {
      Some("access" -> JsString("NO_ACCESS"))
    } else {
      None
    }

    val responseFields: Map[String, JsValue] = Map(
      "status" -> JsNumber(apiStatus.id),
      "error"  -> JsString(makeClientErrorMessage(ex, appConfig))
    ) ++ maybeAccess

    HttpResponse(
      status = httpStatus,
      entity = HttpEntity(ContentType(MediaTypes.`application/json`), JsObject(responseFields).compactPrint)
    )
  }

  /**
   * Converts an exception to an HTTP response in JSON format specific to `V2`.
   *
   * @param ex the exception to be converted.
   * @return an [[HttpResponse]] in JSON format.
   */
  private def exceptionToJsonHttpResponseV2(ex: Throwable, appConfig: AppConfig): HttpResponse = {
    // Get the HTTP status code that corresponds to the exception.
    val httpStatus: StatusCode = ApiStatusCodesV2.fromException(ex)

    // Generate an HTTP response containing the error message...

    val jsonLDDocument = JsonLDDocument(
      body = JsonLDObject(
        Map(OntologyConstants.KnoraApiV2Complex.Error -> JsonLDString(makeClientErrorMessage(ex, appConfig)))
      ),
      context = JsonLDObject(
        Map(
          OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(
            OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion
          )
        )
      )
    )

    // ... and the HTTP status code.
    HttpResponse(
      status = httpStatus,
      entity = HttpEntity(ContentType(MediaTypes.`application/json`), jsonLDDocument.toCompactString(false))
    )
  }

  /**
   * Converts an exception to an HTTP response in JSON format specific to `ADM`.
   *
   * @param ex the exception to be converted.
   * @return an [[HttpResponse]] in JSON format.
   */
  private def exceptionToJsonHttpResponseADM(ex: Throwable, appConfig: AppConfig): HttpResponse = {

    // Get the HTTP status code that corresponds to the exception.
    val httpStatus: StatusCode = ApiStatusCodesV2.fromException(ex)

    // Generate an HTTP response containing the error message ...
    val responseFields: Map[String, JsValue] = Map(
      "error" -> JsString(makeClientErrorMessage(ex, appConfig))
    )

    // ... and the HTTP status code.
    HttpResponse(
      status = httpStatus,
      entity = HttpEntity(ContentType(MediaTypes.`application/json`), JsObject(responseFields).compactPrint)
    )
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
