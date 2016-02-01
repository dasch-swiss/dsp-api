/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.messages.v1respondermessages.graphdatamessages

import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.resourcemessages.{PropsV1, ResourceInfoV1}
import org.knora.webapi.messages.v1respondermessages.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1respondermessages.{KnoraRequestV1, KnoraResponseV1}
import spray.json._

import scala.collection.breakOut


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait for messages that can be sent to `ResourceResponderV1` (there is no GraphDataResponder).
  */
sealed trait GraphDataResponderRequestV1 extends KnoraRequestV1

/**
  * Requests visualization graph data. A successful response will be a [[GraphDataGetResponseV1]].
  * @param iri the IRI of the list.
  * @param userProfile the profile of the user making the request.
  */
case class GraphDataGetRequestV1(iri: IRI, userProfile: UserProfileV1, level: Int = 1) extends GraphDataResponderRequestV1


/**
  * An abstract class extended by `GraphDataGetResponseV1`.
  */
sealed abstract class GraphDataResponseV1 extends KnoraResponseV1 {
    /**
      * Information about the user that made the request.
      * @return a [[UserDataV1]].
      */
    def userdata: UserDataV1
}

/**
  * Provides a graph data representation
  * @param graph the graph representation holding a list of nodes and edges
  * @param userdata information about the user that made the request.
  */
case class GraphDataGetResponseV1(graph: GraphV1, userdata: UserDataV1) extends GraphDataResponseV1 {
    def toJsValue = GraphDataV1JsonProtocol.graphDataGetResponseV1Format.write(this)

}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents a graph in Knora API v1 format.
  * @param id the IRI that generates the graph.
  * @param nodes a flat list of all the nodes in the graph
  * @param edges a flat list of all the edges in the graph
  */
case class GraphV1(id: IRI, nodes: Seq[GraphNodeV1], edges: Seq[GraphDataEdgeV1])

/**
  * Represents a graph node in Knora API v1 format.
  * @param id the IRI that generates the graph.
  * @param resinfo
  * @param properties
  */
case class GraphNodeV1(id: IRI, resinfo: Option[ResourceInfoV1], properties: Option[PropsV1])


/**
  * Represents a graph edge
  * @param label the edge label
  * @param from the central IRI
  * @param to the incoming IRI
  */
case class GraphDataEdgeV1(label: Option[String], from: IRI, to: IRI)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about lists.
  */

object GraphDataV1JsonProtocol extends DefaultJsonProtocol with NullOptions {

    import org.knora.webapi.messages.v1respondermessages.resourcemessages.ResourceV1JsonProtocol._
    import org.knora.webapi.messages.v1respondermessages.usermessages.UserDataV1JsonProtocol._

    implicit object GraphDataV1JsonFormat extends JsonFormat[GraphV1] {
        /**
          * Recursively converts a [[GraphV1]] to a [[JsValue]].
          * @param graph a [[GraphV1]].
          * @return a [[JsValue]].
          */
        def write(graph: GraphV1): JsValue = {
            // TODO: verify this works correctly (it probably doesn't yet)

            val edges: Map[String, JsValue] = graph.edges.map {
                edge =>
                    val edgefields = Map(
                        "label" -> edge.label.toJson,
                        "from" -> edge.from.toJson,
                        "to" -> edge.to.toJson)
                    (edge.from + ";" + edge.to, JsObject(edgefields))
            }(breakOut)
            // JsObject(edges)
            val nodes: Map[IRI, JsValue] = graph.nodes.map {
                node =>
                    val nodefields = Map(
                        "id" -> node.id.toJson,
                        "resinfo" -> node.resinfo.toJson,
                        "properties" -> node.properties.toJson)
                    (node.id, JsObject(nodefields))
            }(breakOut)
            // JsObject(nodes)
            val jsgraph = Map(
                "nodes" -> JsObject(nodes),
                "edges" -> JsObject(edges))
            JsObject(jsgraph)
        }


        /**
          * Not implemented.
          */
        def read(jsonVal: JsValue) = ???
    }

    implicit val graphDataGetResponseV1Format: RootJsonFormat[GraphDataGetResponseV1] = jsonFormat2(GraphDataGetResponseV1)
}