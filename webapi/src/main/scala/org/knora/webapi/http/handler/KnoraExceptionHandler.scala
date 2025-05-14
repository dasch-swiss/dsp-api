/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.handler

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue

import dsp.errors.*
import dsp.errors.InternalServerException
import dsp.errors.RequestRejectedException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.messages.util.rdf.JsonLDString
import org.knora.webapi.store.triplestore.errors.TriplestoreTimeoutException

import pekko.http.scaladsl.model.*
import pekko.http.scaladsl.server.Directives.complete
import pekko.http.scaladsl.server.Directives.extractRequest
import pekko.http.scaladsl.server.ExceptionHandler

/**
 * The Knora exception handler is used by pekko-http to convert any exceptions thrown during route processing
 * into HttpResponses. It is brought implicitly into scope by the application actor.
 */
object KnoraExceptionHandler extends LazyLogging {

  // A generic error message that we return to clients when an internal server error occurs.
  private val GENERIC_INTERNAL_SERVER_ERROR_MESSAGE =
    "The request could not be completed because of an internal server error."

  def apply(appConfig: AppConfig): ExceptionHandler = ExceptionHandler {

    case rre: RequestRejectedException =>
      extractRequest { request =>
        val url = request.uri.path.toString

        if (url.startsWith("/v2")) {
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

        if (url.startsWith("/v2")) {
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

        if (url.startsWith("/v2")) {
          complete(exceptionToJsonHttpResponseV2(other, appConfig))
        } else {
          complete(exceptionToJsonHttpResponseADM(other, appConfig))
        }
      }
  }

  /**
   * Converts an exception to a similar HTTP status code.
   *
   * @param ex an exception.
   * @return an HTTP status code.
   */
  private def fromException(ex: Throwable): StatusCode =
    ex match {
      // Subclasses of RequestRejectedException
      case NotFoundException(_)           => StatusCodes.NotFound
      case ForbiddenException(_)          => StatusCodes.Forbidden
      case BadCredentialsException(_)     => StatusCodes.Unauthorized
      case DuplicateValueException(_)     => StatusCodes.BadRequest
      case OntologyConstraintException(_) => StatusCodes.BadRequest
      case EditConflictException(_)       => StatusCodes.Conflict
      case BadRequestException(_)         => StatusCodes.BadRequest
      case ValidationException(_)         => StatusCodes.BadRequest
      case RequestRejectedException(_)    => StatusCodes.BadRequest
      // RequestRejectedException must be the last one in this group

      // Subclasses of InternalServerException
      case UpdateNotPerformedException(_)    => StatusCodes.Conflict
      case TriplestoreTimeoutException(_, _) => StatusCodes.GatewayTimeout
      case InternalServerException(_)        => StatusCodes.InternalServerError
      // InternalServerException must be the last one in this group

      case _ => StatusCodes.InternalServerError
    }

  /**
   * Converts an exception to an HTTP response in JSON format specific to `V2`.
   *
   * @param ex the exception to be converted.
   * @return an [[HttpResponse]] in JSON format.
   */
  private def exceptionToJsonHttpResponseV2(ex: Throwable, appConfig: AppConfig): HttpResponse = {
    // Get the HTTP status code that corresponds to the exception.
    val httpStatus: StatusCode = fromException(ex)

    // Generate an HTTP response containing the error message...

    val jsonLDDocument = JsonLDDocument(
      body = JsonLDObject(
        Map(OntologyConstants.KnoraApiV2Complex.Error -> JsonLDString(makeClientErrorMessage(ex, appConfig))),
      ),
      context = JsonLDObject(
        Map(
          OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(
            OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion,
          ),
        ),
      ),
    )

    // ... and the HTTP status code.
    HttpResponse(
      status = httpStatus,
      entity = HttpEntity(ContentType(MediaTypes.`application/json`), jsonLDDocument.toCompactString(false)),
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
    val httpStatus: StatusCode = fromException(ex)

    // Generate an HTTP response containing the error message ...
    val responseFields: Map[String, JsValue] = Map(
      "error" -> JsString(makeClientErrorMessage(ex, appConfig)),
    )

    // ... and the HTTP status code.
    HttpResponse(
      status = httpStatus,
      entity = HttpEntity(ContentType(MediaTypes.`application/json`), JsObject(responseFields).compactPrint),
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
