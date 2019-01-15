/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.responders.v2

import java.util

import akka.stream.ActorMaterializer
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{CloseableHttpResponse, HttpDelete, HttpGet, HttpPost}
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.apache.http.{Consts, HttpHost, HttpRequest, NameValuePair}
import org.knora.webapi.{BadRequestException, SipiException}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.sipimessages.GetImageMetadataResponseV2JsonProtocol._
import org.knora.webapi.messages.v2.responder.sipimessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.routing.JWTHelper
import org.knora.webapi.util.ActorUtil.{handleUnexpectedMessage, try2Message}
import org.knora.webapi.util.SipiUtil
import spray.json._

import scala.util.Try

/**
  * Makes requests to Sipi.
  */
class SipiResponderV2 extends Responder {
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    private val targetHost: HttpHost = new HttpHost(settings.internalSipiHost, settings.internalSipiPort, "http")

    private val sipiTimeoutMillis = settings.sipiTimeout.toMillis.toInt

    private val sipiRequestConfig = RequestConfig.custom()
        .setConnectTimeout(sipiTimeoutMillis)
        .setConnectionRequestTimeout(sipiTimeoutMillis)
        .setSocketTimeout(sipiTimeoutMillis)
        .build()

    private val httpClient: CloseableHttpClient = HttpClients.custom.setDefaultRequestConfig(sipiRequestConfig).build

    override def receive: Receive = {
        case getFileMetadataRequestV2: GetImageMetadataRequestV2 => try2Message(sender(), getFileMetadataV2(getFileMetadataRequestV2), log)
        case moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequestV2 => try2Message(sender(), moveTemporaryFileToPermanentStorageV2(moveTemporaryFileToPermanentStorageRequestV2), log)
        case deleteTemporaryFileRequestV2: DeleteTemporaryFileRequestV2 => try2Message(sender(), deleteTemporaryFileV2(deleteTemporaryFileRequestV2), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
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
