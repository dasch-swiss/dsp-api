/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.admin.responder.storesmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.messages.admin.responder.{KnoraRequestADM, KnoraResponseADM}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import spray.json._


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

sealed trait StoreResponderRequestADM extends KnoraRequestADM

/**
  * Requests to load the triplestore with data referenced inside [[RdfDataObject]]. Any data contained inside the
  * triplestore will be deleted first.
  *
  * @param rdfDataObjects a sequence of [[RdfDataObject]] objects containing the path to the data and the name of
  *                       the named graph into which the data should be loaded.
  */
case class ResetTriplestoreContentRequestADM(rdfDataObjects: Seq[RdfDataObject]) extends StoreResponderRequestADM

case class ResetTriplestoreContentResponseADM(message: String) extends KnoraResponseADM with StoresADMJsonProtocol {
    def toJsValue = resetTriplestoreContentResponseADMFormat.write(this)
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API ADM JSON for property values.
  */
trait StoresADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with TriplestoreJsonProtocol {

    /* Very strange construct at the end is needed, but I don't really understand why and what it means */
    implicit val resetTriplestoreContentResponseADMFormat: RootJsonFormat[ResetTriplestoreContentResponseADM] = jsonFormat[String, ResetTriplestoreContentResponseADM](ResetTriplestoreContentResponseADM, "message")
}