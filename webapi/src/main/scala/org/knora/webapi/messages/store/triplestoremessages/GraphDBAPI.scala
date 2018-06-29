package org.knora.webapi.messages.store.triplestoremessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

// Used to communicate with the GraphDB API

sealed trait GraphDBAPI

case class GraphDBRepository(externalUrl: String,
                             id: String,
                             local: Boolean,
                             location: String,
                             readable: Boolean,
                             sesameType: String,
                             title: String,
                             typeOf: String,
                             unsupported: Boolean,
                             uri: String,
                             writable: Boolean
                            ) extends GraphDBAPI

object GraphDBJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
    // 'typeOf' in the case class is 'type' in json
    implicit val graphDBRepositoryFormat: RootJsonFormat[GraphDBRepository] = jsonFormat(GraphDBRepository, "externalUrl", "id", "local", "location", "readable", "sesameType", "title", "type", "unsupported", "uri", "writable")
}