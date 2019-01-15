/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.sipi

import java.util

import akka.actor.{Actor, ActorLogging, ActorSystem}
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{CloseableHttpResponse, HttpDelete, HttpGet, HttpPost}
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.apache.http.{Consts, HttpHost, HttpRequest, NameValuePair}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.GetImageMetadataResponseV2JsonProtocol._
import org.knora.webapi.messages.store.sipimessages.RepresentationV1JsonProtocol._
import org.knora.webapi.messages.store.sipimessages.SipiConstants.FileType
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.v1.responder.valuemessages.{FileValueV1, StillImageFileValueV1, TextFileValueV1}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.routing.JWTHelper
import org.knora.webapi.util.ActorUtil.{handleUnexpectedMessage, try2Message}
import org.knora.webapi.util.{SipiUtil, StringFormatter}
import org.knora.webapi.{BadRequestException, KnoraDispatchers, NotImplementedException, Settings, SipiException}
import spray.json._

import scala.concurrent.ExecutionContext
import scala.util.Try

/**
  * Makes requests to Sipi.
  */
class SipiConnector extends Actor with ActorLogging {

    implicit val system: ActorSystem = context.system
    implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

    val settings = Settings(system)

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val targetHost: HttpHost = new HttpHost(settings.internalSipiHost, settings.internalSipiPort, "http")

    private val sipiTimeoutMillis = settings.sipiTimeout.toMillis.toInt

    private val sipiRequestConfig = RequestConfig.custom()
            .setConnectTimeout(sipiTimeoutMillis)
            .setConnectionRequestTimeout(sipiTimeoutMillis)
            .setSocketTimeout(sipiTimeoutMillis)
            .build()

    private val httpClient: CloseableHttpClient = HttpClients.custom.setDefaultRequestConfig(sipiRequestConfig).build

    override def receive: Receive = {
        case convertPathRequest: SipiConversionPathRequestV1 => try2Message(sender(), convertPathV1(convertPathRequest), log)
        case convertFileRequest: SipiConversionFileRequestV1 => try2Message(sender(), convertFileV1(convertFileRequest), log)
        case getFileMetadataRequestV2: GetImageMetadataRequestV2 => try2Message(sender(), getFileMetadataV2(getFileMetadataRequestV2), log)
        case moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequestV2 => try2Message(sender(), moveTemporaryFileToPermanentStorageV2(moveTemporaryFileToPermanentStorageRequestV2), log)
        case deleteTemporaryFileRequestV2: DeleteTemporaryFileRequestV2 => try2Message(sender(), deleteTemporaryFileV2(deleteTemporaryFileRequestV2), log)
        case SipiGetTextFileRequest(fileUrl, requestingUser) => try2Message(sender(), sipiGetXsltTransformationRequestV2(fileUrl, requestingUser), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Convert a file that has been sent to Knora (non GUI-case).
      *
      * @param conversionRequest the information about the file (uploaded by Knora).
      * @return a [[SipiConversionResponseV1]] representing the file values to be added to the triplestore.
      */
    private def convertPathV1(conversionRequest: SipiConversionPathRequestV1): Try[SipiConversionResponseV1] = {
        val url = s"${settings.internalSipiImageConversionUrlV1}/${settings.sipiPathConversionRouteV1}"

        callSipiConvertRoute(url, conversionRequest)
    }

    /**
      * Convert a file that is already managed by Sipi (GUI-case).
      *
      * @param conversionRequest the information about the file (managed by Sipi).
      * @return a [[SipiConversionResponseV1]] representing the file values to be added to the triplestore.
      */
    private def convertFileV1(conversionRequest: SipiConversionFileRequestV1): Try[SipiConversionResponseV1] = {
        val url = s"${settings.internalSipiImageConversionUrlV1}/${settings.sipiFileConversionRouteV1}"

        callSipiConvertRoute(url, conversionRequest)
    }

    /**
      * Makes a conversion request to Sipi and creates a [[SipiConversionResponseV1]]
      * containing the file values to be added to the triplestore.
      *
      * @param urlPath           the Sipi route to be called.
      * @param conversionRequest the message holding the information to make the request.
      * @return a [[SipiConversionResponseV1]].
      */
    private def callSipiConvertRoute(urlPath: String, conversionRequest: SipiConversionRequestV1): Try[SipiConversionResponseV1] = {
        val context: HttpClientContext = HttpClientContext.create

        val formParams = new util.ArrayList[NameValuePair]()

        for ((key, value) <- conversionRequest.toFormData()) {
            formParams.add(new BasicNameValuePair(key, value))
        }

        val postEntity = new UrlEncodedFormEntity(formParams, Consts.UTF_8)
        val httpPost = new HttpPost(urlPath)
        httpPost.setEntity(postEntity)

        val conversionResultTry: Try[String] = Try {
            var maybeResponse: Option[CloseableHttpResponse] = None

            try {
                maybeResponse = Some(httpClient.execute(targetHost, httpPost, context))

                val responseEntityStr: String = Option(maybeResponse.get.getEntity) match {
                    case Some(responseEntity) => EntityUtils.toString(responseEntity)
                    case None => ""
                }

                val statusCode: Int = maybeResponse.get.getStatusLine.getStatusCode
                val statusCategory: Int = statusCode / 100

                // Was the request successful?
                if (statusCategory == 2) {
                    // Yes.
                    responseEntityStr
                } else {
                    // No. Throw an appropriate exception.
                    val sipiErrorMsg = SipiUtil.getSipiErrorMessage(responseEntityStr)

                    if (statusCategory == 4) {
                        throw BadRequestException(s"Sipi responded with HTTP status code $statusCode: $sipiErrorMsg")
                    } else {
                        throw SipiException(s"Sipi responded with HTTP status code $statusCode: $sipiErrorMsg")
                    }
                }
            } finally {
                maybeResponse match {
                    case Some(response) => response.close()
                    case None => ()
                }
            }
        }

        //
        // handle unsuccessful requests to Sipi
        //
        val recoveredConversionResultTry = conversionResultTry.recoverWith {
            case badRequestException: BadRequestException => throw badRequestException
            case sipiException: SipiException => throw sipiException
            case e: Exception => throw SipiException("Failed to connect to Sipi", e, log)
        }

        for {
            responseAsStr: String <- recoveredConversionResultTry

            /* get json from response body */
            responseAsJson: JsValue = JsonParser(responseAsStr)

            // get file type from Sipi response
            fileType: String = responseAsJson.asJsObject.fields.getOrElse("file_type", throw SipiException(message = "Sipi did not return a file type")) match {
                case JsString(ftype: String) => ftype
                case other => throw SipiException(message = s"Sipi returned an invalid file type: $other")
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
                        internalMimeType = stringFormatter.toSparqlEncodedString(imageConversionResult.mimetype_full, throw BadRequestException(s"The internal MIME type returned by Sipi is invalid: '${imageConversionResult.mimetype_full}")),
                        originalFilename = stringFormatter.toSparqlEncodedString(imageConversionResult.original_filename, throw BadRequestException(s"The original filename returned by Sipi is invalid: '${imageConversionResult.original_filename}")),
                        originalMimeType = Some(stringFormatter.toSparqlEncodedString(imageConversionResult.original_mimetype, throw BadRequestException(s"The original MIME type returned by Sipi is invalid: '${imageConversionResult.original_mimetype}"))),
                        dimX = imageConversionResult.nx_full,
                        dimY = imageConversionResult.ny_full,
                        internalFilename = stringFormatter.toSparqlEncodedString(imageConversionResult.filename_full, throw BadRequestException(s"The internal filename returned by Sipi is invalid: '${imageConversionResult.filename_full}")),
                        qualityLevel = 100,
                        qualityName = Some(SipiConstants.StillImage.fullQuality)
                    ),
                        StillImageFileValueV1(// thumbnail representation
                            internalMimeType = stringFormatter.toSparqlEncodedString(imageConversionResult.mimetype_thumb, throw BadRequestException(s"The internal MIME type returned by Sipi is invalid: '${imageConversionResult.mimetype_full}")),
                            originalFilename = stringFormatter.toSparqlEncodedString(imageConversionResult.original_filename, throw BadRequestException(s"The original filename returned by Sipi is invalid: '${imageConversionResult.original_filename}")),
                            originalMimeType = Some(stringFormatter.toSparqlEncodedString(imageConversionResult.original_mimetype, throw BadRequestException(s"The original MIME type returned by Sipi is invalid: '${imageConversionResult.original_mimetype}"))),
                            dimX = imageConversionResult.nx_thumb,
                            dimY = imageConversionResult.ny_thumb,
                            internalFilename = stringFormatter.toSparqlEncodedString(imageConversionResult.filename_thumb, throw BadRequestException(s"The internal filename returned by Sipi is invalid: '${imageConversionResult.filename_thumb}")),
                            qualityLevel = 10,
                            qualityName = Some(SipiConstants.StillImage.thumbnailQuality),
                            isPreview = true
                        ))

                case SipiConstants.FileType.TEXT =>

                    // parse response as a [[SipiTextResponse]]
                    val textStoreResult = try {
                        responseAsJson.convertTo[SipiTextResponse]
                    } catch {
                        case e: DeserializationException => throw SipiException(message = "JSON response returned by Sipi is invalid, it cannot be turned into a SipiTextResponse", e = e, log = log)
                    }

                    Vector(TextFileValueV1(
                        internalMimeType = stringFormatter.toSparqlEncodedString(textStoreResult.mimetype, throw BadRequestException(s"The internal MIME type returned by Sipi is invalid: '${textStoreResult.mimetype}")),
                        internalFilename = stringFormatter.toSparqlEncodedString(textStoreResult.filename, throw BadRequestException(s"The internal filename returned by Sipi is invalid: '${textStoreResult.filename}")),
                        originalFilename = stringFormatter.toSparqlEncodedString(textStoreResult.original_filename, throw BadRequestException(s"The internal filename returned by Sipi is invalid: '${textStoreResult.original_filename}")),
                        originalMimeType = Some(stringFormatter.toSparqlEncodedString(textStoreResult.mimetype, throw BadRequestException(s"The orignal MIME type returned by Sipi is invalid: '${textStoreResult.original_mimetype}")))
                    ))

                case unknownType => throw NotImplementedException(s"Could not handle file type $unknownType")

                // TODO: add missing file types
            }

        } yield SipiConversionResponseV1(fileValuesV1, file_type = fileTypeEnum)
    }

    /**
      * Asks Sipi for metadata about a file.
      *
      * @param getFileMetadataRequestV2 the request.
      * @return a [[GetImageMetadataResponseV2]] containing the requested metadata.
      */
    private def getFileMetadataV2(getFileMetadataRequestV2: GetImageMetadataRequestV2): Try[GetImageMetadataResponseV2] = {
        val knoraInfoUrl = getFileMetadataRequestV2.fileUrl + "/knora.json"

        val request = new HttpGet(knoraInfoUrl)

        for {
            responseStr <- doSipiRequest(request)
        } yield responseStr.parseJson.convertTo[GetImageMetadataResponseV2]
    }

    /**
      * Asks Sipi to move a file from temporary storage to permanent storage.
      *
      * @param moveTemporaryFileToPermanentStorageRequestV2 the request.
      * @return a [[SuccessResponseV2]].
      */
    private def moveTemporaryFileToPermanentStorageV2(moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequestV2): Try[SuccessResponseV2] = {
        val token: String = JWTHelper.createToken(
            userIri = moveTemporaryFileToPermanentStorageRequestV2.requestingUser.id,
            secret = settings.jwtSecretKey,
            longevity = settings.jwtLongevity,
            content = Map(
                "knora-data" -> JsObject(
                    Map(
                        "permission" -> JsString("StoreFile"),
                        "filename" -> JsString(moveTemporaryFileToPermanentStorageRequestV2.internalFilename)
                    )
                )
            )
        )

        val moveFileUrl = s"${settings.internalSipiBaseUrl}/${settings.sipiMoveFileRouteV2}?token=$token"

        val formParams = new util.ArrayList[NameValuePair]()
        formParams.add(new BasicNameValuePair("filename", moveTemporaryFileToPermanentStorageRequestV2.internalFilename))
        val requestEntity = new UrlEncodedFormEntity(formParams, Consts.UTF_8)
        val queryHttpPost = new HttpPost(moveFileUrl)
        queryHttpPost.setEntity(requestEntity)

        for {
            _ <- doSipiRequest(queryHttpPost)
        } yield SuccessResponseV2("Moved file to permanent storage.")
    }

    /**
      * Asks Sipi to delete a temporary file.
      *
      * @param deleteTemporaryFileRequestV2 the request.
      * @return a [[SuccessResponseV2]].
      */
    private def deleteTemporaryFileV2(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequestV2): Try[SuccessResponseV2] = {
        val token: String = JWTHelper.createToken(
            userIri = deleteTemporaryFileRequestV2.requestingUser.id,
            secret = settings.jwtSecretKey,
            longevity = settings.jwtLongevity,
            content = Map(
                "knora-data" -> JsObject(
                    Map(
                        "permission" -> JsString("DeleteTempFile"),
                        "filename" -> JsString(deleteTemporaryFileRequestV2.internalFilename)
                    )
                )
            )
        )

        val deleteFileUrl = s"${settings.internalSipiBaseUrl}/${settings.sipiDeleteTempFileRouteV2}/${deleteTemporaryFileRequestV2.internalFilename}?token=$token"
        val request = new HttpDelete(deleteFileUrl)

        for {
            _ <- doSipiRequest(request)
        } yield SuccessResponseV2("Deleted temporary file.")
    }

    /**
      * Asks Sipi for a file.
      * @param xsltFileUrl the file's URL.
      * @param requestingUser the user making the request.
      */
    private def sipiGetXsltTransformationRequestV2(xsltFileUrl: String, requestingUser: UserADM): Try[SipiGetTextFileResponse] = {
        // ask Sipi to return the XSL transformation
        val request = new HttpGet(xsltFileUrl)

        for {
            responseStr <- doSipiRequest(request)
        } yield SipiGetTextFileResponse(responseStr)
    }

    /**
      * Makes an HTTP request to Sipi and returns the response.
      *
      * @param request the HTTP request.
      * @return Sipi's response.
      */
    private def doSipiRequest(request: HttpRequest): Try[String] = {
        val httpContext: HttpClientContext = HttpClientContext.create

        val sipiResponseTry = Try {
            var maybeResponse: Option[CloseableHttpResponse] = None

            try {
                maybeResponse = Some(httpClient.execute(targetHost, request, httpContext))

                val responseEntityStr: String = Option(maybeResponse.get.getEntity) match {
                    case Some(responseEntity) => EntityUtils.toString(responseEntity)
                    case None => ""
                }

                val statusCode: Int = maybeResponse.get.getStatusLine.getStatusCode
                val statusCategory: Int = statusCode / 100

                // Was the request successful?
                if (statusCategory == 2) {
                    // Yes.
                    responseEntityStr
                } else {
                    // No. Throw an appropriate exception.
                    val sipiErrorMsg = SipiUtil.getSipiErrorMessage(responseEntityStr)

                    if (statusCategory == 4) {
                        throw BadRequestException(s"Sipi responded with HTTP status code $statusCode: $sipiErrorMsg")
                    } else {
                        throw SipiException(s"Sipi responded with HTTP status code $statusCode: $sipiErrorMsg")
                    }
                }
            } finally {
                maybeResponse match {
                    case Some(response) => response.close()
                    case None => ()
                }
            }
        }

        sipiResponseTry.recover {
            case badRequestException: BadRequestException => throw badRequestException
            case sipiException: SipiException => throw sipiException
            case e: Exception => throw SipiException("Failed to connect to Sipi", e, log)
        }
    }
}
