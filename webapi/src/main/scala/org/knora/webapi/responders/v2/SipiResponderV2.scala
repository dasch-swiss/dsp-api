/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.util.FastFuture
import akka.stream.ActorMaterializer
import org.knora.webapi.SipiException
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.sipimessages.GetImageMetadataResponseV2JsonProtocol._
import org.knora.webapi.messages.v2.responder.sipimessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.routing.JWTHelper
import org.knora.webapi.util.ActorUtil.{handleUnexpectedMessage, try2Message}
import spray.json._

import scala.concurrent.{Await, Future}
import scala.util.Try

/**
  * Makes requests to Sipi.
  */
class SipiResponderV2 extends Responder {
    implicit val materializer: ActorMaterializer = ActorMaterializer()

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

        val request = FastFuture.successful(
            HttpRequest(
                method = HttpMethods.GET,
                uri = knoraInfoUrl
            )
        )

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

        val formParams = Map(
            "filename" -> moveTemporaryFileToPermanentStorageRequestV2.internalFilename
        )

        val requestFuture = for {
            requestEntity <- Marshal(FormData(formParams)).to[RequestEntity]
        } yield HttpRequest(
            method = HttpMethods.POST,
            uri = moveFileUrl,
            entity = requestEntity
        )

        for {
            _ <- doSipiRequest(requestFuture)
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

        val requestFuture = FastFuture.successful(
            HttpRequest(
                method = HttpMethods.DELETE,
                uri = deleteFileUrl
            )
        )

        for {
            _ <- doSipiRequest(requestFuture)
        } yield SuccessResponseV2("Deleted temporary file.")
    }

    /**
      * Makes an HTTP request to Sipi and returns the response.
      *
      * @param requestFuture the HTTP request.
      * @return Sipi's response.
      */
    private def doSipiRequest(requestFuture: Future[HttpRequest]): Try[String] = {
        val sipiResponseFuture: Future[HttpResponse] =
            for {
                request <- requestFuture
                response <- Http().singleRequest(request)
            } yield response

        // Block until Sipi responds, to ensure that the number of concurrent connections to Sipi will never be greater
        // than the value of akka.actor.deployment./responderManager/sipiRouterV2.nr-of-instances
        val sipiResponseTry: Try[HttpResponse] = Try {
            Await.ready(sipiResponseFuture, settings.sipiTimeout)
            sipiResponseFuture.value.get
        }.flatten

        val sipiResponseTryRecovered: Try[HttpResponse] = sipiResponseTry.recoverWith {
            case exception: Exception => throw SipiException(message = "Sipi error", e = exception, log = log)
        }

        for {
            sipiResponse: HttpResponse <- sipiResponseTryRecovered
            httpStatusCode: StatusCode = sipiResponse.status
            strictEntityFuture: Future[HttpEntity.Strict] = sipiResponse.entity.toStrict(settings.sipiTimeout)
            strictEntity: HttpEntity.Strict = Await.result(strictEntityFuture, settings.sipiTimeout)
            responseStr: String = strictEntity.data.decodeString("UTF-8")

            _ = if (httpStatusCode != StatusCodes.OK) {
                throw SipiException(s"Sipi returned HTTP status code ${httpStatusCode.intValue} with message: $responseStr")
            }
        } yield responseStr
    }
}
