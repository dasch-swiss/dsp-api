package org.knora.webapi.messages.v2.responder.searchmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.messages.v2.responder.{KnoraRequestV2, KnoraResponseV2}
import spray.json.{DefaultJsonProtocol, NullOptions, RootJsonFormat}

/**
  * An abstract trait for messages that can be sent to `SearchResponderV1`.
  */
sealed trait SearchResponderRequestV2 extends KnoraRequestV2 {

    def userProfile: UserProfileV1
}


/**
  * Requests a fulltext search. A successful response will be a [[SearchGetResponseV2]].
  *
  * @param userProfile the profile of the user making the request.
  */
case class FulltextSearchGetRequestV2(searchValue: String,
                                      userProfile: UserProfileV1) extends SearchResponderRequestV2

/**
  * Represents a response to a user search query (both fulltext and extended search)
  *
  */
case class SearchGetResponseV2(nhits: Int) extends KnoraResponseV2 {
    def toJsValue = SearchV1JsonProtocol.searchResponseV1Format.write(this)
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about representations of a resource.
  */
object SearchV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit val searchResponseV1Format: RootJsonFormat[SearchGetResponseV2] = jsonFormat1(SearchGetResponseV2)
}