/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import org.knora.webapi.Core
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import spray.json.{JsObject, _}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

/**
  * Created by subotic on 26.06.17.
  */
trait TestingUtilities extends WordSpecLike with Matchers with BeforeAndAfterAll with RequestBuilding {

    this: Core =>

    implicit protected val ec: ExecutionContextExecutor = system.dispatcher
    implicit protected val materializer = ActorMaterializer()

    protected def singleAwaitingRequest(request: HttpRequest, duration: Duration = 15.seconds): HttpResponse = {
        val responseFuture = Http().singleRequest(request)
        Await.result(responseFuture, duration)
    }

    protected def getResponseString(request: HttpRequest): String = {
        val response = singleAwaitingRequest(request)

        //log.debug("REQUEST: {}", request)
        //log.debug("RESPONSE: {}", response.toString())

        val responseBodyFuture: Future[String] = response.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
        val responseBodyStr = Await.result(responseBodyFuture, 5.seconds)

        assert(response.status === StatusCodes.OK, s",\n REQUEST: $request,\n RESPONSE: $response")
        responseBodyStr
    }

    protected def checkResponseOK(request: HttpRequest): Unit = {
        getResponseString(request)
    }

    protected def getResponseJson(request: HttpRequest): JsObject = {
        getResponseString(request).parseJson.asJsObject
    }

}
