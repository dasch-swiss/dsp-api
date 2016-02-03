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

import java.io.File

import akka.actor.Status
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.sipimessages.RepresentationV1JsonProtocol._
import org.knora.webapi.messages.v1respondermessages.sipimessages._
import org.knora.webapi.messages.v1respondermessages.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse}
import org.knora.webapi.messages.v1respondermessages.usermessages.UserProfileV1
import org.knora.webapi.messages.v1respondermessages.valuemessages.{FileValueV1, StillImageFileValueV1}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{ErrorHandlingMap, InputValidation}
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport._

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
        case SipiBinaryFileRequestV1(originalFilename, originalMimeType, sourceTmpFilename, userProfile)
        => future2Message(sender(), createFileValueV1(originalFilename, originalMimeType, sourceTmpFilename, userProfile), log)
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
            valueV1 = valueUtilV1.makeValueV1(valueProps)
            path = valueV1 match {
                case fileValueV1: FileValueV1 => valueUtilV1.makeSipiFileGetUrlFromFileValueV1(fileValueV1)
                case otherValue => throw InconsistentTriplestoreDataException(s"Value $fileValueIri is not a FileValue, it is an instance of ${otherValue.valueTypeIri}")
            }
            permissionCode = PermissionUtilV1.getUserPermissionV1WithValueProps(fileValueIri, valueProps, userProfile)
        } yield SipiFileInfoGetResponseV1(
            permissionCode = permissionCode,
            path = permissionCode.map(_ => path)
        )
    }

    private def createFileValueV1(originalFilename: String, originalMimeType: String, sourceTmpFilename: String, userProfile: UserProfileV1): Future[SipiBinaryFileResponseV1] = {

        def handleImageMimeType(originalFilename: String, originalMimeType: String, sourceTmpFilename: String): Future[Vector[FileValueV1]] = {

            //http://spray.io/documentation/1.2.2/spray-client/
            val pipeline: HttpRequest => Future[SipiImageConversionResponse] = (
                addHeader("Accept", "application/json")
                    //~> logRequest
                    ~> sendReceive
                    //~> logResponse
                    ~> unmarshal[SipiImageConversionResponse]
                )

            val url = settings.sipiURL + ":" + settings.sipiPort + "/" + settings.sipiConversionRoute

            val targetThumb = File.createTempFile("tmp_", ".jpg", new File(settings.tmpDataDir))
            val targetFull = File.createTempFile("tmp_", ".jpx", new File(settings.tmpDataDir))

            for {

            // send a conversion request to SIPI and parse the response as a [[SipiConversionResponse]]
                conversionFull: SipiImageConversionResponse <-
                pipeline(Post(url, FormData(Map(
                    "source" -> sourceTmpFilename,
                    "target" -> targetFull.toString,
                    "format" -> "jpx"
                ))))

                // also send dimensions for thumbnail
                conversionThumb: SipiImageConversionResponse <-
                pipeline(Post(url, FormData(Map(
                    "source" -> sourceTmpFilename,
                    "target" -> targetThumb.toString,
                    "format" -> "jpg",
                    "size" -> "!128,128"
                ))))

                // check if conversion was successful
                _ = if (!(conversionFull.status == 0 && conversionThumb.status == 0)) {
                    throw BadRequestException(s"Provided image file ${originalFilename} could not be converted by SIPI")
                }

            // TODO: move converted files to final location
            // TODO: delete source file from disk (tmp)

            } yield Vector(StillImageFileValueV1(
                internalMimeType = InputValidation.toSparqlEncodedString(conversionFull.mimetype),
                originalFilename = originalFilename,
                originalMimeType = Some(originalMimeType),
                dimX = conversionFull.nx,
                dimY = conversionFull.ny,
                internalFilename = "file.jp2",
                qualityLevel = 100,
                qualityName = Some("full")
            ),
                StillImageFileValueV1(
                    internalMimeType = InputValidation.toSparqlEncodedString(conversionThumb.mimetype),
                    originalFilename = originalFilename,
                    originalMimeType = Some(originalMimeType),
                    dimX = conversionThumb.nx,
                    dimY = conversionThumb.ny,
                    internalFilename = "file.jpg",
                    qualityLevel = 10,
                    qualityName = Some("thumbnail"),
                    isPreview = true
                ))

        }

        // Create a Vector of Tuples of handlers and a Vector of appropriate mime types.
        val handlers = Vector(
            (handleImageMimeType: (String, String, String) => Future[Vector[FileValueV1]], settings.imageMimeTypes)
        )

        // Turn the `handlers` into a Map of mime type (key) -> handler (value)
        val mimeTypes2Handlers: ErrorHandlingMap[String, (String, String, String) => Future[Vector[FileValueV1]]] = new ErrorHandlingMap(handlers.flatMap {
            case (handler, mimeTypes) => mimeTypes.map(mimeType => mimeType -> handler)
        }.toMap, { key: IRI => s"Unknown value type: $key" }, { errorMessage: String => throw BadRequestException(errorMessage) })

        for {
            handler <- Future(mimeTypes2Handlers(originalMimeType))
            fileValuesV1 <- handler(originalFilename, originalMimeType, sourceTmpFilename)
        } yield SipiBinaryFileResponseV1(fileValuesV1)
    }
}
