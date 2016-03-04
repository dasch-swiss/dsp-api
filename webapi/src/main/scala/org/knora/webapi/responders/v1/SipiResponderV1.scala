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
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.sipimessages.RepresentationV1JsonProtocol._
import org.knora.webapi.messages.v1respondermessages.sipimessages.SipiConstants.FileType
import org.knora.webapi.messages.v1respondermessages.sipimessages._
import org.knora.webapi.messages.v1respondermessages.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse}
import org.knora.webapi.messages.v1respondermessages.usermessages.UserProfileV1
import org.knora.webapi.messages.v1respondermessages.valuemessages.{ApiValueV1, FileValueV1, StillImageFileValueV1}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.InputValidation
import spray.client.pipelining._
import spray.http._
import spray.json._

import scala.concurrent.Future

/**
  * Responds to requests for information about binary representations of resources, and returns responses in Knora API
  * v1 format.
  */
class SipiResponderV1 extends ResponderV1 {

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
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Methods for generating complete responses.

    /**
      * Returns a [[SipiFileInfoGetResponseV1]] containing the permissions and path for a file.
      *
      * @param fileValueIri the iri of the resource.
      * @return a [[SipiFileInfoGetResponseV1]].
      */
    private def getFileInfoForSipiV1(fileValueIri: IRI, userProfile: UserProfileV1): Future[SipiFileInfoGetResponseV1] = {
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getFileValue(fileValueIri).toString())
            queryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            rows = queryResponse.results.bindings
            valueProps = valueUtilV1.createValueProps(fileValueIri, rows)
            valueV1: ApiValueV1 = valueUtilV1.makeValueV1(valueProps)
            path = valueV1 match {
                case imageValueV1: StillImageFileValueV1 => imageValueV1.internalFilename // return internal filename associated with the given file value Iri to Sipi
                // TODO: prepend file value specific cases for each file value type (movie, audio etc.)
                case otherFileValueV1: FileValueV1 => throw NotImplementedException(s"Handling of file value type ${otherFileValueV1.valueTypeIri} not implemented yet")
                case otherValue => throw InconsistentTriplestoreDataException(s"Value $fileValueIri is not a FileValue, it is an instance of ${otherValue.valueTypeIri}")
            }
            permissionCode = PermissionUtilV1.getUserPermissionV1WithValueProps(fileValueIri, valueProps, userProfile)
        } yield SipiFileInfoGetResponseV1(
            permissionCode = permissionCode,
            path = permissionCode.map(_ => path)
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

        // delete tmp file (depending on the kind of request given: only necessary if Knora stored the file - non GUI-case)
        def deleteTmpFile(conversionRequest: SipiResponderConversionRequestV1): Unit = {
            conversionRequest match {
                case (conversionPathRequest: SipiResponderConversionPathRequestV1) =>
                    // a tmp file has been created by the resources route (non GUI-case), delete it
                    InputValidation.deleteFileFromTmpLocation(conversionPathRequest.source)
                case _ => ()
            }
        }

        // define a pipeline function that gets turned into a generic [[HTTP Response]] (containing JSON)
        val pipeline: HttpRequest => Future[HttpResponse] = (
            addHeader("Accept", "application/json")
                ~> sendReceive
                ~> unmarshal[HttpResponse]
            )

        // send a conversion request to SIPI and parse the response as a generic [[HTTPResponse]]
        val conversionResultFuture: Future[HttpResponse] = for {
            formData <- Future(conversionRequest.toFormData())
            postRequest <- Future(Post(url, FormData(formData)))
            pipelineResult <- pipeline(postRequest)
        } yield pipelineResult

        //
        // handle unsuccessful requests to Sipi
        //
        val recoveredConversionResultFuture = conversionResultFuture.recoverWith {
            case noResponse: spray.can.Http.ConnectionAttemptFailedException =>
                deleteTmpFile(conversionRequest) // delete tmp file (if given)
                // this problem is hardly the user's fault. Create a SipiException
                throw SipiException(message = "Sipi not reachable", e = noResponse, log = log)

            case httpError: spray.httpx.UnsuccessfulResponseException =>
                deleteTmpFile(conversionRequest) // delete tmp file (if given)
            val statusCode: StatusCode = httpError.response.status

                val statusInt: Int = statusCode.intValue / 100

                // match status codes
                statusInt match {
                    case 4 =>
                        // Bad Request: it is the user's responsibility
                        val errMsg = try {
                            // parse answer as a Sipi error message
                            httpError.response.entity.asString.parseJson.convertTo[SipiErrorConversionResponse]
                        } catch {
                            // the Sipi error message could not be parsed correctly
                            case e: DeserializationException => throw SipiException(message = "JSON error response returned by Sipi is invalid, it cannot be turned into a SipiErrorConversionResponse", e = e, log = log)
                        }
                        // most probably the user sent invalid data which caused a Sipi error
                        throw BadRequestException(s"Sipi returned a non successful HTTP status code ${statusCode}: ${errMsg}")

                    case 5 =>
                        // Internal Server Error: not the user's fault
                        throw SipiException(s"Sipi reported an internal server error ${statusCode}", e = httpError, log = log)
                }

            case err =>
                // unknown error
                deleteTmpFile(conversionRequest) // delete tmp file (if given)
                throw SipiException(message = s"Unknown error: ${err.toString}", e = err, log = log)

        }

        for {

            conversionResultResponse <- recoveredConversionResultFuture

            // delete tmp file
            _ = deleteTmpFile(conversionRequest)

            // get file type from Sipi response
            responseAsMap: Map[String, JsValue] = conversionResultResponse.entity.asString.parseJson.asJsObject.fields.toMap

            statusCode: Int = responseAsMap.getOrElse("status", throw SipiException(message = "Sipi did not return a status code")) match {
                case JsNumber(ftype: BigDecimal) => ftype.toInt
                case other => throw SipiException(message = s"Sipi did not return a correct status code, but ${other.toString()}")
            }

            // check if Sipi returned a status code != 0
            _ = if (statusCode != 0) throw BadRequestException(s"Sipi returned a HTTP 200 status code, a unsuccessful status code ${statusCode}")

            fileType: String = responseAsMap.getOrElse("file_type", throw SipiException(message = "Sipi did not return a file type")) match {
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
                        conversionResultResponse.entity.asString.parseJson.convertTo[SipiImageConversionResponse]
                    } catch {
                        case e: DeserializationException => throw SipiException(message = "JSON response returned by Sipi is invalid, it cannot be turned into a SipiImageConversionResponse", e = e, log = log)
                    }

                    // create two StillImageFileValueV1s
                    Vector(StillImageFileValueV1(// full representation
                        internalMimeType = InputValidation.toSparqlEncodedString(imageConversionResult.mimetype_full),
                        originalFilename = InputValidation.toSparqlEncodedString(imageConversionResult.original_filename),
                        originalMimeType = Some(InputValidation.toSparqlEncodedString(imageConversionResult.original_mimetype)),
                        dimX = imageConversionResult.nx_full,
                        dimY = imageConversionResult.ny_full,
                        internalFilename = InputValidation.toSparqlEncodedString(imageConversionResult.filename_full),
                        qualityLevel = 100,
                        qualityName = Some(SipiConstants.StillImage.fullQuality)
                    ),
                        StillImageFileValueV1(// thumbnail representation
                            internalMimeType = InputValidation.toSparqlEncodedString(imageConversionResult.mimetype_thumb),
                            originalFilename = InputValidation.toSparqlEncodedString(imageConversionResult.original_filename),
                            originalMimeType = Some(InputValidation.toSparqlEncodedString(imageConversionResult.original_mimetype)),
                            dimX = imageConversionResult.nx_thumb,
                            dimY = imageConversionResult.ny_thumb,
                            internalFilename = InputValidation.toSparqlEncodedString(imageConversionResult.filename_thumb),
                            qualityLevel = 10,
                            qualityName = Some(SipiConstants.StillImage.thumbnailQuality),
                            isPreview = true
                        ))

                case unknownType => throw BadRequestException(s"Could not handle file type $unknownType")

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

        val url = s"${settings.sipiUrl}/${settings.sipiPathConversionRoute}"

        callSipiConvertRoute(url, conversionRequest)

    }

    /**
      * Convert a file that is already managed by Sipi (GUI-case).
      *
      * @param conversionRequest the information about the file (managed by Sipi).
      * @return a [[SipiResponderConversionResponseV1]] representing the file values to be added to the triplestore.
      */
    private def convertFileV1(conversionRequest: SipiResponderConversionFileRequestV1): Future[SipiResponderConversionResponseV1] = {
        val url = s"${settings.sipiUrl}/${settings.sipiFileConversionRoute}"

        callSipiConvertRoute(url, conversionRequest)
    }

}
