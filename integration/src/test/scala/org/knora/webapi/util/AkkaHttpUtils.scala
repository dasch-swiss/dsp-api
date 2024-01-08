/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko
import spray.json._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import pekko.actor.ActorSystem
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.model.StatusCodes
import pekko.http.scaladsl.unmarshalling.Unmarshal
import pekko.util.Timeout

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
    import pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

    val jsonFuture: Future[JsObject] = response match {
      case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[JsObject]
      case other                                      => throw new Exception(other.toString())
    }

    // FIXME: There is probably a better non blocking way of doing it.
    Await.result(jsonFuture, Timeout(10.seconds).duration)
  }
}
