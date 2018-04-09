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

package org.knora.webapi.util

import java.io.ByteArrayInputStream
import java.util

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.github.jsonldjava.core.{JsonLdOptions, JsonLdProcessor}
import com.github.jsonldjava.utils.JsonUtils
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * Object containing methods for dealing with [[HttpResponse]]
  */
object AkkaHttpUtils {

    /**
      * Given an [[HttpResponse]] containing json, return the said json.
      *
      * @param response the [[HttpResponse]] containing json
      * @return an [[JsObject]]
      */
    def httpResponseToJson(response: HttpResponse)(implicit ec: ExecutionContext, system: ActorSystem, log: LoggingAdapter): JsObject = {

        import DefaultJsonProtocol._
        import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

        implicit val materializer: ActorMaterializer = ActorMaterializer()

        val jsonFuture: Future[JsObject] = response match {
            case HttpResponse(StatusCodes.OK, _, entity, _) =>
                Unmarshal(entity).to[JsObject]
            case other =>
                throw new Exception(other.toString())
        }

        //FIXME: There is probably a better non blocking way of doing it.
        Await.result(jsonFuture, Timeout(10.seconds).duration)
    }

    /**
      * Given an [[HttpResponse]] containing json-ld, return the said json-ld in expanded form.
      *
      * @param response the [[HttpResponse]] containing json
      * @return an [[JsObject]]
      */
    def httpResponseToJsonLDExpanded(response: HttpResponse)(implicit ec: ExecutionContext, system: ActorSystem, log: LoggingAdapter): Map[String, Any] = {

        implicit val materializer: ActorMaterializer = ActorMaterializer()

        val jsonStringFuture: Future[String] = Unmarshal(response.entity).to[String]

        val jsonString = Await.result(jsonStringFuture, Timeout(1.second).duration)

        val istream = new ByteArrayInputStream(jsonString.getBytes(java.nio.charset.StandardCharsets.UTF_8))

        val jsonObject: AnyRef = JsonUtils.fromInputStream(istream)

        val context = new util.HashMap()

        val options = new JsonLdOptions()

        val normalized: util.Map[String, Object] = JsonLdProcessor.compact(jsonObject, context, options)


        /*
        val opts: JsonLdOptions = new JsonLdOptions()
        val expanded = JsonLdProcessor.expand(httpResponseToJson(response), opts)
        println("expanded json-ld: " + expanded)
        */

        JavaUtil.deepJavatoScala(normalized).asInstanceOf[Map[String, Any]]
    }

}
