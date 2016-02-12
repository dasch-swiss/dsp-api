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
import org.knora.webapi.messages.v1respondermessages.sipimessages._
import org.knora.webapi.messages.v1respondermessages.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse}
import org.knora.webapi.messages.v1respondermessages.usermessages.UserProfileV1
import org.knora.webapi.messages.v1respondermessages.valuemessages.{FileValueV1, StillImageFileValueV1}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.InputValidation
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
        case convertPathRequest: SipiResponderConversionPathRequestV1
        => future2Message(sender(), convertPathV1(convertPathRequest), log)
        case convertFileRequest: SipiResponderConversionFileRequestV1
        => future2Message(sender(), convertFileV1(convertFileRequest), log)
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

    private def callSipiConvertRoute(url: String, conversionRequest: SipiResponderConversionRequestV1): Future[SipiResponderConversionResponseV1] = {
        //http://spray.io/documentation/1.2.2/spray-client/
        val pipeline: HttpRequest => Future[SipiConversionResponse] = (
            addHeader("Accept", "application/json")
                //~> logRequest
                ~> sendReceive
                //~> logResponse
                ~> unmarshal[SipiConversionResponse]
            )

        for {

            // send a conversion request to SIPI and parse the response as a [[SipiConversionResponse]]
            conversionResult: SipiConversionResponse <-
            pipeline(Post(url, FormData(conversionRequest.toFormData())))

            // TODO: if anything goes wrong with the HTTP request (status code != 200), an uncaught error is thrown and the temporary file is never deleted

            _ = conversionRequest match {
                case (conversionPathRequest: SipiResponderConversionPathRequestV1) =>
                    // a tmp file has been created by the resources route (non GUI-case), delete it
                    InputValidation.deleteFileFromTmpLocation(conversionPathRequest.source)
                case _ => ()
            }

            // TODO: At the moment, this cannot happen because in case of a Sipi error, Sipi sends a non successful HTTP status code
            // TODO: the reason why I made all the members optional in SipiConversionResponse, is because Sipi could just send a Sipi status error code
            _ = if (conversionResult.status != 0) throw BadRequestException("Sipi error when trying to convert the file")

            fileType: String = conversionResult.file_type.getOrElse(throw BadRequestException("Sipi did not return file type"))

            // create the apt case class depending on the file type returned by Sipi
            // TODO: Would it make sense to move that logic in SipiMessages (object Sipi)?
            fileValuesV1: Vector[FileValueV1] = fileType match {
                case Sipi.FileType.image =>
                    // create two StillImageFileValueV1s
                    Vector(StillImageFileValueV1( // full representation
                        internalMimeType = InputValidation.toSparqlEncodedString(conversionResult.mimetype_full.getOrElse(throw BadRequestException("Sipi did not return mimtype for full image"))),
                        originalFilename = InputValidation.toSparqlEncodedString(conversionResult.original_filename.getOrElse(throw BadRequestException("Sipi did not return original filename"))),
                        originalMimeType = Some(InputValidation.toSparqlEncodedString(conversionResult.original_mimetype.getOrElse(throw BadRequestException("Sipi did not return original mimetype")))),
                        dimX = conversionResult.nx_full.getOrElse(throw BadRequestException("Sipi did not return x dim for full image")),
                        dimY = conversionResult.ny_full.getOrElse(throw BadRequestException("Sipi did not return y dim for full image")),
                        internalFilename = InputValidation.toSparqlEncodedString(conversionResult.filename_full.getOrElse(throw BadRequestException("Sipi did not return filename for full image"))),
                        qualityLevel = 100,
                        qualityName = Some("full")
                    ),
                        StillImageFileValueV1( // thumbnail representation
                            internalMimeType = InputValidation.toSparqlEncodedString(conversionResult.mimetype_thumb.getOrElse(throw BadRequestException("Sipi did not return mimtype for thumbnail"))),
                            originalFilename = InputValidation.toSparqlEncodedString(conversionResult.original_filename.getOrElse(throw BadRequestException("Sipi did not return original filename"))),
                            originalMimeType = Some(InputValidation.toSparqlEncodedString(conversionResult.original_mimetype.getOrElse(throw BadRequestException("Sipi did not return original mimetype")))),
                            dimX = conversionResult.nx_thumb.getOrElse(throw BadRequestException("Sipi did not return x dim for thumbnail")),
                            dimY = conversionResult.ny_thumb.getOrElse(throw BadRequestException("Sipi did not return y dim for thumbnail")),
                            internalFilename = InputValidation.toSparqlEncodedString(conversionResult.filename_thumb.getOrElse(throw BadRequestException("Sipi did not return filename for full image"))),
                            qualityLevel = 10,
                            qualityName = Some("thumbnail"),
                            isPreview = true
                        ))

                case unknownType => throw BadRequestException (s"Could not handle file type $unknownType")
            }

        } yield SipiResponderConversionResponseV1(fileValuesV1, file_type = Sipi.FileType.image) // TODO: use an enum for file types (see SipiMessages, object Sipi)
    }

    private def convertPathV1(conversionRequest: SipiResponderConversionRequestV1): Future[SipiResponderConversionResponseV1] = {

        val url = settings.sipiURL + ":" + settings.sipiPort + "/" + settings.sipiPathConversionRoute

        callSipiConvertRoute(url, conversionRequest)

    }

    private def convertFileV1(conversionRequest: SipiResponderConversionRequestV1): Future[SipiResponderConversionResponseV1] = {
        val url = settings.sipiURL + ":" + settings.sipiPort + "/" + settings.sipiFileConversionRoute

        callSipiConvertRoute(url, conversionRequest)
    }

}
