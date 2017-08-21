package org.knora.webapi.util

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * Object containing methods for dealing with [[HttpResponse]]
  */
object AkkaHttpUtils {

    /**
      * Given an [[HttpResponse]] containing json, return the said json.
      * @param response the [[HttpResponse]] containing json
      * @return an [[JsObject]]
      */
    def httpResponseToJson(response: HttpResponse)(implicit ec: ExecutionContext, system: ActorSystem, log: LoggingAdapter): JsObject = {

        import DefaultJsonProtocol._
        import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

        implicit val materializer = ActorMaterializer()

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
    def httpResponseToJsonLDExpanded(response: HttpResponse)(implicit ec: ExecutionContext, system: ActorSystem, log: LoggingAdapter): JsObject = {

        val json: JsObject = httpResponseToJson(response)

        /*
        val opts: JsonLdOptions = new JsonLdOptions()
        val expanded = JsonLdProcessor.expand(httpResponseToJson(response), opts)
        println("expanded json-ld: " + expanded)
        */

        json
    }

}
