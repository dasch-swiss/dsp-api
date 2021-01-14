/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

package org.knora.webapi.util

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * Object containing methods for dealing with [[HttpResponse]]
  */
object AkkaHttpUtils extends LazyLogging {

  /**
    * Given an [[HttpResponse]] containing json, return the said json.
    *
    * @param response the [[HttpResponse]] containing json
    * @return an [[JsObject]]
    */
  def httpResponseToJson(response: HttpResponse)(implicit ec: ExecutionContext, system: ActorSystem): JsObject = {

    import DefaultJsonProtocol._
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

    implicit val materializer: Materializer = Materializer.matFromSystem(system)

    val jsonFuture: Future[JsObject] = response match {
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
        Unmarshal(entity).to[JsObject]
      case other =>
        throw new Exception(other.toString())
    }

    //FIXME: There is probably a better non blocking way of doing it.
    Await.result(jsonFuture, Timeout(10.seconds).duration)
  }
}
