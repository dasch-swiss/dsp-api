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
import akka.stream.ActorMaterializer
import org.knora.webapi.SipiException
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.sipimessages.GetImageMetadataResponseV2JsonProtocol._
import org.knora.webapi.messages.v2.responder.sipimessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil.try2Message
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
    }

    /**
      * Asks Sipi for metadata about a file.
      *
      * @param getFileMetadataRequestV2 the request.
      * @return a [[GetImageMetadataResponseV2]] containing the requested metadata.
      */
    private def getFileMetadataV2(getFileMetadataRequestV2: GetImageMetadataRequestV2): Try[GetImageMetadataResponseV2] = {
        val knoraInfoUrl = getFileMetadataRequestV2.fileUrl + "/knora.json"

        val sipiResponseFuture: Future[HttpResponse] = for {
            response: HttpResponse <- Http().singleRequest(
                HttpRequest(
                    method = HttpMethods.GET,
                    uri = knoraInfoUrl
                )
            )

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
        } yield responseStr.parseJson.convertTo[GetImageMetadataResponseV2]
    }

    /**
      * Asks Sipi to move a file from temporary storage to permanent storage.
      *
      * @param moveTemporaryFileToPermanentStorageRequestV2 the request.
      * @return a [[SuccessResponseV2]].
      */
    private def moveTemporaryFileToPermanentStorageV2(moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequestV2): Try[SuccessResponseV2] = {
        val moveFileUrl = s"${settings.internalSipiBaseUrl}/${settings.sipiMoveFileRouteV2}"

        val formParams = Map(
            "internalFilename" -> moveTemporaryFileToPermanentStorageRequestV2.internalFilename
        )

        val sipiResponseFuture: Future[HttpResponse] = for {
            request <- Marshal(FormData(formParams)).to[RequestEntity]

            response: HttpResponse <- Http().singleRequest(
                HttpRequest(
                    method = HttpMethods.POST,
                    uri = moveFileUrl
                )
            )

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
        } yield SuccessResponseV2("Moved file to permanent storage")
    }
}
