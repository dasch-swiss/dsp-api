package org.knora.webapi.e2e.v1

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.knora.webapi.ITKnoraLiveSpec
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.util.TestingUtilities
import spray.json._

import scala.concurrent.duration._

/**
  * Causes an internal server error to see if logging is working correctly.
  */
class ErrorV1ITSpec extends ITKnoraLiveSpec with TestingUtilities with TriplestoreJsonProtocol {
    private val rdfDataObjects = List.empty[RdfDataObject]

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/admin/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "Make a request that causes an internal server error" in {
        val request = Get(baseApiUrl + "/v1/error/unitMsg")
        val response = singleAwaitingRequest(request, 1.second)
        // println(response.toString())
        assert(response.status == StatusCodes.InternalServerError)
    }

}
