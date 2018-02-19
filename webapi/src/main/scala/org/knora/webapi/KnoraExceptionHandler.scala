package org.knora.webapi

import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import org.knora.webapi.http.{ApiStatusCodesV1, ApiStatusCodesV2}
import spray.json.{JsNumber, JsObject, JsString, JsValue}

/**
  * The Knora exception handler is used by akka-http to convert any exceptions thrown during route processing
  * into HttpResponses. It is brought implicitly into scope at the top level [[KnoraService]].
  */
object KnoraExceptionHandler {

    // A generic error message that we return to clients when an internal server error occurs.
    private val GENERIC_INTERNAL_SERVER_ERROR_MESSAGE = "The request could not be completed because of an internal server error."

    def apply(settingsImpl: SettingsImpl, log: LoggingAdapter): ExceptionHandler = ExceptionHandler {

        /* TODO: Find out which response format should be generated, by looking at what the client is requesting / accepting (issue #292) */

        case rre: RequestRejectedException =>
            extractRequest { request =>
                val url = request.uri.path.toString

                // println(s"KnoraExceptionHandler - case: rre - url: $url")

                if (url.contains("v1")) {
                    complete(exceptionToJsonHttpResponseV1(rre, settingsImpl))
                } else {
                    complete(exceptionToJsonHttpResponseV2(rre, settingsImpl))
                }
            }

        case ise: InternalServerException =>
            extractRequest { request =>
                val uri = request.uri
                val url = uri.path.toString

                // println(s"KnoraExceptionHandler - case: ise - url: $url")
                log.error(ise, s"Unable to run route $url")

                if (url.contains("v1")) {
                    complete(exceptionToJsonHttpResponseV1(ise, settingsImpl))
                } else {
                    complete(exceptionToJsonHttpResponseV2(ise, settingsImpl))
                }
            }

        case other =>
            extractRequest { request =>
                val uri = request.uri
                val url = uri.path.toString

                log.debug(s"KnoraExceptionHandler - case: other - url: $url")
                log.error(other, s"Unable to run route $url")

                if (url.contains("v1")) {
                    complete(exceptionToJsonHttpResponseV1(other, settingsImpl))
                } else {
                    complete(exceptionToJsonHttpResponseV2(other, settingsImpl))
                }
            }
    }

    /**
      * Converts an exception to an HTTP response in JSON format.
      *
      * @param ex the exception to be converted.
      * @return an [[HttpResponse]] in JSON format.
      */
    private def exceptionToJsonHttpResponseV1(ex: Throwable, settings: SettingsImpl): HttpResponse = {
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
            "error" -> JsString(makeClientErrorMessage(ex, settings))
        ) ++ maybeAccess

        HttpResponse(
            status = httpStatus,
            entity = HttpEntity(ContentType(MediaTypes.`application/json`), JsObject(responseFields).compactPrint)
        )
    }

    /**
      * Converts an exception to an HTTP response in JSON format.
      *
      * @param ex the exception to be converted.
      * @return an [[HttpResponse]] in JSON format.
      */
    private def exceptionToJsonHttpResponseV2(ex: Throwable, settings: SettingsImpl): HttpResponse = {

        // Get the HTTP status code that corresponds to the exception.
        val httpStatus: StatusCode = ApiStatusCodesV2.fromException(ex)

        // Generate an HTTP response containing the error message ...
        val responseFields: Map[String, JsValue] = Map(
            "error" -> JsString(makeClientErrorMessage(ex, settings))
        )

        // ... and the HTTP status code.
        HttpResponse(
            status = httpStatus,
            entity = HttpEntity(ContentType(MediaTypes.`application/json`), JsObject(responseFields).compactPrint)
        )
    }

    /**
      * Converts an exception to an HTTP response in HTML format.
      *
      * @param ex the exception to be converted.
      * @return an [[HttpResponse]] in HTML format.
      */
    private def exceptionToHtmlHttpResponseV1(ex: Throwable, settings: SettingsImpl): HttpResponse = {
        // Get the API status code that corresponds to the exception.
        val apiStatus: ApiStatusCodesV1.Value = ApiStatusCodesV1.fromException(ex)

        // Convert the API status code to the corresponding HTTP status code.
        val httpStatus: StatusCode = ApiStatusCodesV1.toHttpStatus(apiStatus)

        // Generate an HTTP response containing the error message, the API status code, and the HTTP status code.
        HttpResponse(
            status = httpStatus,
            entity = HttpEntity(
                ContentTypes.`text/xml(UTF-8)`,
                <html xmlns="http://www.w3.org/1999/xhtml">
                    <head>
                        <title>Error</title>
                    </head>
                    <body>
                        <h2>Error</h2>
                        <p>
                            <code>
                                {makeClientErrorMessage(ex, settings)}
                            </code>
                        </p>
                        <h2>Status code</h2>
                        <p>
                            <code>
                                {apiStatus.id}
                            </code>
                        </p>
                    </body>
                </html>.toString()
            )
        )
    }

    /**
      * Converts an exception to an HTTP response in HTML format.
      *
      * @param ex the exception to be converted.
      * @return an [[HttpResponse]] in HTML format.
      */
    private def exceptionToHtmlHttpResponseV2(ex: Throwable, settings: SettingsImpl): HttpResponse = {

        // Get the HTTP status code that corresponds to the exception.
        val httpStatus: StatusCode = ApiStatusCodesV2.fromException(ex)

        // Generate an HTTP response containing the error message and the HTTP status code.
        HttpResponse(
            status = httpStatus,
            entity = HttpEntity(
                ContentTypes.`text/xml(UTF-8)`,
                <html xmlns="http://www.w3.org/1999/xhtml">
                    <head>
                        <title>Error</title>
                    </head>
                    <body>
                        <h2>Error</h2>
                        <p>
                            <code>
                                {makeClientErrorMessage(ex, settings)}
                            </code>
                        </p>
                    </body>
                </html>.toString()
            )
        )
    }

    /**
      * Given an exception, returns an error message suitable for clients.
      *
      * @param ex       the exception.
      * @param settings the application settings.
      * @return an error message suitable for clients.
      */
    private def makeClientErrorMessage(ex: Throwable, settings: SettingsImpl): String = {
        ex match {
            case rre: RequestRejectedException => rre.toString

            case other =>
                if (settings.showInternalErrors) {
                    other.toString
                } else {
                    GENERIC_INTERNAL_SERVER_ERROR_MESSAGE
                }
        }
    }

}
