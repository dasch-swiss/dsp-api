/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko
import spray.json.*
import zio.json.*

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*

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

    import DefaultJsonProtocol.*
    import pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*

    val jsonFuture: Future[JsObject] = response match {
      case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[JsObject]
      case other                                      => throw new Exception(other.toString())
    }

    Await.result(jsonFuture, Timeout(10.seconds).duration)
  }

  def httpResponseToString(response: HttpResponse)(implicit ec: ExecutionContext, system: ActorSystem): String =
    Await.result(Unmarshal(response.entity).to[String], Timeout(10.seconds).duration)

  def httpResponseTo[A](
    response: HttpResponse,
  )(implicit ec: ExecutionContext, system: ActorSystem, decoder: JsonDecoder[A]): A =
    httpResponseToString(response).fromJson[A] match {
      case Left(error)  => throw new Exception(s"Failed to decode response: $error")
      case Right(value) => value
    }
}
