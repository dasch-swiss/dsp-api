package org.knora.webapi

import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import org.knora.webapi.messages.v1.responder.ApiStatusCodesV1
import spray.json.{JsNumber, JsObject, JsString, JsValue}

/**
  * The Knora exception handler is used by akka-http to convert any exceptions thrown during route processing
  * into HttpResponses. It is brought implicitly into scope at the top level [[KnoraService]].
  */
object KnoraExceptionHandler {

    def apply(settingsImpl: SettingsImpl, log: LoggingAdapter): ExceptionHandler = ExceptionHandler {

        /* FIXME: distinguish when json or html response format should be used, by looking at what the client is requesting / accepting */

        case bce: BadCredentialsException =>
            extractUri { uri =>
                log.error(s"Request to $uri could not be handled normally")
                complete(
                    HttpResponse(
                        status = StatusCodes.Unauthorized,
                        entity = HttpEntity(
                            ContentTypes.`application/json`,
                            JsObject(
                                "status" -> JsNumber(2),
                                "message" -> JsString(bce.getMessage)
                            ).compactPrint
                        )
                    )
                )
            }

        case rre: RequestRejectedException =>
            extractUri { uri =>
                log.error(s"Request to $uri could not be handled normally")
                complete(exceptionToJsonHttpResponse(rre, settingsImpl))
            }

        case ise: InternalServerException =>
            extractUri { uri =>
                log.error(ise, ise.getMessage)
                complete(exceptionToJsonHttpResponse(ise, settingsImpl))
            }

        case other =>
            extractUri { uri =>
                log.error(other, s"Unable to run route: $uri")
                complete(exceptionToJsonHttpResponse(other, settingsImpl))
            }


    }

    /**
      * Converts an exception to an HTTP response in JSON format.
      *
      * @param ex the exception to be converted.
      * @return an [[HttpResponse]] in JSON format.
      */
    private def exceptionToJsonHttpResponse(ex: Throwable, settings: SettingsImpl): HttpResponse = {
        // Get the API status code that corresponds to the exception.
        val apiStatus: ApiStatusCodesV1.Value = ApiStatusCodesV1.fromException(ex)

        // Convert the API status code to the corresponding HTTP status code.
        val httpStatus: StatusCode = ApiStatusCodesV1.toHttpStatus(apiStatus)

        // Generate an HTTP response containing the error message, the API status code, and the HTTP status code.

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
      * Converts an exception to an HTTP response in HTML format.
      *
      * @param ex the exception to be converted.
      * @return an [[HttpResponse]] in HTML format.
      */
    private def exceptionToHtmlHttpResponse(ex: Throwable, settings: SettingsImpl): HttpResponse = {
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
      * Given an exception, returns an error message suitable for clients.
      *
      * @param ex       the exception.
      * @param settings the application settings.
      * @return an error message suitable for clients.
      */
    private def makeClientErrorMessage(ex: Throwable, settings: SettingsImpl): String = {
        ex match {
            case rre: RequestRejectedException => rre.toString


        }
    }

}
