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

package org.knora.webapi.responders.v1

import akka.actor.Status
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model._
import akka.pattern._
import akka.stream.ActorMaterializer
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.sipimessages.RepresentationV1JsonProtocol._
import org.knora.webapi.messages.v1.responder.sipimessages.SipiConstants.FileType
import org.knora.webapi.messages.v1.responder.sipimessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.valuemessages.{FileValueV1, StillImageFileValueV1, TextFileValueV1}
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{InputValidation, PermissionUtilV1}
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Responds to requests for information about binary representations of resources, and returns responses in Knora API
  * v1 format.
  */
class SipiResponderV1 extends ResponderV1 {

    implicit val materializer = ActorMaterializer()

    // Converts SPARQL query results to ApiValueV1 objects.
    val valueUtilV1 = new ValueUtilV1(settings)

    /**
      * Receives a message of type [[SipiResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case SipiFileInfoGetRequestV1(fileValueIri, userProfile) => future2Message(sender(), getFileInfoForSipiV1(fileValueIri, userProfile), log)
        case convertPathRequest: SipiResponderConversionPathRequestV1 => future2Message(sender(), convertPathV1(convertPathRequest), log)
        case convertFileRequest: SipiResponderConversionFileRequestV1 => future2Message(sender(), convertFileV1(convertFileRequest), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Methods for generating complete responses.

    /**
      * Returns a [[SipiFileInfoGetResponseV1]] containing the permissions and path for a file.
      *
      * @param filename the iri of the resource.
      * @return a [[SipiFileInfoGetResponseV1]].
      */
    private def getFileInfoForSipiV1(filename: String, userProfile: UserProfileV1): Future[SipiFileInfoGetResponseV1] = {
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getFileValue(
                triplestore = settings.triplestoreType,
                filename = filename
            ).toString())

            queryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            rows: Seq[VariableResultsRow] = queryResponse.results.bindings
            // check if rows were found for the given filename
            _ = if (rows.isEmpty) throw BadRequestException(s"No file value was found for filename $filename")

            // check that only one file value was found (by grouping by file value IRI)
            groupedByResourceIri = rows.groupBy {
                (row: VariableResultsRow) =>
                    row.rowMap("fileValue")
            }
            _ = if(groupedByResourceIri.size > 1) throw InconsistentTriplestoreDataException(s"filename $filename is referred to from more than one file value")

            valueProps = valueUtilV1.createValueProps(filename, rows)

            permissionCode: Option[Int] = PermissionUtilV1.getUserPermissionV1WithValueProps(
                valueIri = filename,
                valueProps = valueProps,
                subjectProject = None, // no need to specify this here, because it's in valueProps
                userProfile = userProfile
            )
        } yield SipiFileInfoGetResponseV1(
            permissionCode = permissionCode.getOrElse(0) // Sipi expects a permission code from 0 to 8
        )
    }


    /**
      * Makes a conversion request to Sipi and creates a [[SipiResponderConversionResponseV1]]
      * containing the file values to be added to the triplestore.
      *
      * @param url               the Sipi route to be called.
      * @param conversionRequest the message holding the information to make the request.
      * @return a [[SipiResponderConversionResponseV1]].
      */
    private def callSipiConvertRoute(url: String, conversionRequest: SipiResponderConversionRequestV1): Future[SipiResponderConversionResponseV1] = {

        val conversionResultFuture: Future[HttpResponse] = for {
            request <- Marshal(FormData(conversionRequest.toFormData())).to[RequestEntity]

            response <- Http().singleRequest(
                HttpRequest(
                    method = HttpMethods.POST,
                    uri = url,
                    entity = request
                )
            )
        } yield response

        //
        // handle unsuccessful requests to Sipi
        //
        val recoveredConversionResultFuture = conversionResultFuture.recoverWith {
            case noResponse: akka.http.impl.engine.HttpConnectionTimeoutException =>
                // this problem is hardly the user's fault. Create a SipiException
                throw SipiException(message = "Sipi not reachable", e = noResponse, log = log)

            case err =>
                // unknown error
                throw SipiException(message = s"Unknown error: ${err.toString}", e = err, log = log)
        }

        for {
            conversionResultResponse <- recoveredConversionResultFuture

            httpStatusCode: StatusCode = conversionResultResponse.status
            statusInt: Int = httpStatusCode.intValue / 100

            /* get json from response body */
            responseAsJson: JsValue <- statusInt match {
                case 2 => conversionResultResponse.entity.toStrict(5.seconds).map(
                    (strict: Strict) =>
                        try {
                            strict.data.decodeString("UTF-8").parseJson
                        } catch {
                            // the Sipi response message could not be parsed correctly
                            case e: spray.json.JsonParser.ParsingException => throw SipiException(message = "JSON response returned by Sipi is not valid JSON", e = e, log = log)

                            case all: Exception => throw SipiException(message = "JSON response returned by Sipi is not valid JSON", e = all, log = log)
                        }
                ) // returns a Future(Map(...))
                case 4 =>
                    // Bad Request: it is the user's responsibility
                    val errMessage: Future[SipiErrorConversionResponse] = conversionResultResponse.entity.toStrict(5.seconds).map(
                        (strict: Strict) =>
                            try {
                                strict.data.decodeString("UTF-8").parseJson.convertTo[SipiErrorConversionResponse]
                            } catch {
                                // the Sipi error message could not be parsed correctly
                                case e: spray.json.JsonParser.ParsingException => throw SipiException(message = "JSON error response returned by Sipi is invalid, it cannot be turned into a SipiErrorConversionResponse", e = e, log = log)

                                case all: Exception => throw SipiException(message = "JSON error response returned by Sipi is not valid JSON", e = all, log = log)
                            }
                    )

                    // most probably the user sent invalid data which caused a Sipi error
                    errMessage.map(errMsg => throw BadRequestException(s"Sipi returned a non successful HTTP status code $httpStatusCode: $errMsg"))
                case 5 =>
                    // Internal Server Error: not the user's fault
                    val errString: Future[String] = conversionResultResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
                    errString.map(errStr => throw SipiException(s"Sipi reported an internal server error $httpStatusCode - $errStr"))
                case _ => throw SipiException(s"Sipi returned $httpStatusCode!")
            }

            // get file type from Sipi response
            fileType: String = responseAsJson.asJsObject.fields.getOrElse("file_type", throw SipiException(message = "Sipi did not return a file type")) match {
                case JsString(ftype: String) => ftype
                case other => throw SipiException(message = s"Sipi did not return a correct file type, but: ${other}")
            }

            // turn fileType returned by Sipi (a string) into an enum
            fileTypeEnum: FileType.Value = SipiConstants.FileType.lookup(fileType)

            // create the apt case class depending on the file type returned by Sipi
            fileValuesV1: Vector[FileValueV1] = fileTypeEnum match {
                case SipiConstants.FileType.IMAGE =>
                    // parse response as a [[SipiImageConversionResponse]]
                    val imageConversionResult = try {
                        responseAsJson.convertTo[SipiImageConversionResponse]
                    } catch {
                        case e: DeserializationException => throw SipiException(message = "JSON response returned by Sipi is invalid, it cannot be turned into a SipiImageConversionResponse", e = e, log = log)
                    }

                    // create two StillImageFileValueV1s
                    Vector(StillImageFileValueV1(// full representation
                        internalMimeType = InputValidation.toSparqlEncodedString(imageConversionResult.mimetype_full, () => throw BadRequestException(s"The internal MIME type returned by Sipi is invalid: '${imageConversionResult.mimetype_full}")),
                        originalFilename = InputValidation.toSparqlEncodedString(imageConversionResult.original_filename, () => throw BadRequestException(s"The original filename returned by Sipi is invalid: '${imageConversionResult.original_filename}")),
                        originalMimeType = Some(InputValidation.toSparqlEncodedString(imageConversionResult.original_mimetype, () => throw BadRequestException(s"The original MIME type returned by Sipi is invalid: '${imageConversionResult.original_mimetype}"))),
                        dimX = imageConversionResult.nx_full,
                        dimY = imageConversionResult.ny_full,
                        internalFilename = InputValidation.toSparqlEncodedString(imageConversionResult.filename_full, () => throw BadRequestException(s"The internal filename returned by Sipi is invalid: '${imageConversionResult.filename_full}")),
                        qualityLevel = 100,
                        qualityName = Some(SipiConstants.StillImage.fullQuality)
                    ),
                        StillImageFileValueV1(// thumbnail representation
                            internalMimeType = InputValidation.toSparqlEncodedString(imageConversionResult.mimetype_thumb, () => throw BadRequestException(s"The internal MIME type returned by Sipi is invalid: '${imageConversionResult.mimetype_full}")),
                            originalFilename = InputValidation.toSparqlEncodedString(imageConversionResult.original_filename, () => throw BadRequestException(s"The original filename returned by Sipi is invalid: '${imageConversionResult.original_filename}")),
                            originalMimeType = Some(InputValidation.toSparqlEncodedString(imageConversionResult.original_mimetype, () => throw BadRequestException(s"The original MIME type returned by Sipi is invalid: '${imageConversionResult.original_mimetype}"))),
                            dimX = imageConversionResult.nx_thumb,
                            dimY = imageConversionResult.ny_thumb,
                            internalFilename = InputValidation.toSparqlEncodedString(imageConversionResult.filename_thumb, () => throw BadRequestException(s"The internal filename returned by Sipi is invalid: '${imageConversionResult.filename_thumb}")),
                            qualityLevel = 10,
                            qualityName = Some(SipiConstants.StillImage.thumbnailQuality),
                            isPreview = true
                        ))

                case SipiConstants.FileType.TEXT =>

                    // parse response as a [[SipiTextResponse]]
                    val textStoreResult = try {
                        responseAsJson.convertTo[SipiTextResponse]
                    } catch {
                        case e: DeserializationException => throw SipiException(message = "JSON response returned by Sipi is invalid, it cannot be turned into a SipiImageConversionResponse", e = e, log = log)
                    }

                    Vector(TextFileValueV1(
                        internalMimeType = InputValidation.toSparqlEncodedString(textStoreResult.mimetype, () => throw BadRequestException(s"The internal MIME type returned by Sipi is invalid: '${textStoreResult.mimetype}")),
                        internalFilename = InputValidation.toSparqlEncodedString(textStoreResult.filename, () => throw BadRequestException(s"The internal filename returned by Sipi is invalid: '${textStoreResult.filename}")),
                        originalFilename = InputValidation.toSparqlEncodedString(textStoreResult.original_filename, () => throw BadRequestException(s"The internal filename returned by Sipi is invalid: '${textStoreResult.original_filename}")),
                        originalMimeType = Some(InputValidation.toSparqlEncodedString(textStoreResult.mimetype, () => throw BadRequestException(s"The orignal MIME type returned by Sipi is invalid: '${textStoreResult.original_mimetype}")))
                    ))

                case unknownType => throw NotImplementedException(s"Could not handle file type $unknownType")

                // TODO: add missing file types
            }

        } yield SipiResponderConversionResponseV1(fileValuesV1, file_type = fileTypeEnum)
    }


    /**
      * Convert a file that has been sent to Knora (non GUI-case).
      *
      * @param conversionRequest the information about the file (uploaded by Knora).
      * @return a [[SipiResponderConversionResponseV1]] representing the file values to be added to the triplestore.
      */
    private def convertPathV1(conversionRequest: SipiResponderConversionPathRequestV1): Future[SipiResponderConversionResponseV1] = {
        val url = s"${settings.sipiImageConversionUrl}/${settings.sipiPathConversionRoute}"

        callSipiConvertRoute(url, conversionRequest)

    }

    /**
      * Convert a file that is already managed by Sipi (GUI-case).
      *
      * @param conversionRequest the information about the file (managed by Sipi).
      * @return a [[SipiResponderConversionResponseV1]] representing the file values to be added to the triplestore.
      */
    private def convertFileV1(conversionRequest: SipiResponderConversionFileRequestV1): Future[SipiResponderConversionResponseV1] = {
        val url = s"${settings.sipiImageConversionUrl}/${settings.sipiFileConversionRoute}"

        callSipiConvertRoute(url, conversionRequest)
    }

}
