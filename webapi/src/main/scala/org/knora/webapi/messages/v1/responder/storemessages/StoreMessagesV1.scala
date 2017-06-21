/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.messages.v1.responder.storemessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.messages.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import spray.json._


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

sealed trait StoreResponderRequestV1 extends KnoraRequestV1

/**
  * Requests to load the triplestore with data referenced inside [[RdfDataObject]]. Any data contained inside the
  * triplestore will be deleted first.
  *
  * @param rdfDataObjects a sequence of [[RdfDataObject]] objects containing the path to the data and the name of
  *                       the named graph into which the data should be loaded.
  */
case class ResetTriplestoreContentRequestV1(rdfDataObjects: Seq[RdfDataObject]) extends StoreResponderRequestV1

case class ResetTriplestoreContentResponseV1(message: String) extends KnoraResponseV1 with StoreV1JsonProtocol {
    def toJsValue = resetTriplestoreContentResponseV1Format.write(this)
}



//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON for property values.
  */
trait StoreV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions with TriplestoreJsonProtocol {

    /* Very strange construct at the end is needed, but I don't really understand why and what it means */
    implicit val resetTriplestoreContentResponseV1Format: RootJsonFormat[ResetTriplestoreContentResponseV1] = jsonFormat[String, ResetTriplestoreContentResponseV1](ResetTriplestoreContentResponseV1, "message")
}