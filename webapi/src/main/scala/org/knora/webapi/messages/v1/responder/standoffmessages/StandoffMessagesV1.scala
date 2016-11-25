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

package org.knora.webapi.messages.v1.responder.standoffmessages

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.{IRI, OntologyConstants}
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import spray.json._

import scala.collection.immutable.SortedSet




/**
  * An abstract trait representing a Knora v1 API request message that can be sent to `StandoffResponderV1`.
  */
sealed trait StandoffResponderRequestV1 extends KnoraRequestV1

case class CreateStandoffRequestV1(projectIri: IRI, resourceIri: IRI, propertyIri: IRI, xml: String, userProfile: UserProfileV1, apiRequestID: UUID) extends StandoffResponderRequestV1


case class CreateStandoffResponseV1(userdata: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = RepresentationV1JsonProtocol.createStandoffResponseV1Format.write(this)
}

/**
  * Represents the data types of standoff classes.
  */
object StandoffDataTypeClasses extends Enumeration {

    val StandoffLinkTag = Value(OntologyConstants.KnoraBase.StandoffLinkTag)

    val StandoffDateTag = Value(OntologyConstants.KnoraBase.StandoffDateTag)

    val StandoffUriTag = Value(OntologyConstants.KnoraBase.StandoffUriTag)

    val StandoffColorTag = Value(OntologyConstants.KnoraBase.StandoffColorTag)

    val StandoffIntegerTag = Value(OntologyConstants.KnoraBase.StandoffIntegerTag)

    val StandoffDecimalTag = Value(OntologyConstants.KnoraBase.StandoffDecimalTag)

    val StandoffIntervalTag = Value(OntologyConstants.KnoraBase.StandoffIntervalTag)

    val StandoffBooleanTag = Value(OntologyConstants.KnoraBase.StandoffBooleanTag)

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
      * [[org.knora.webapi.BadRequestException]].
      *
      * @param name     the name of the value.
      * @param errorFun the function to be called in case of an error.
      * @return the requested value.
      */
    def lookup(name: String, errorFun: () => Nothing): Value = {
        valueMap.get(name) match {
            case Some(value) => value
            case None => errorFun()
        }
    }

    def getStandoffClassIris: SortedSet[IRI] = StandoffDataTypeClasses.values.map(_.toString)

}

/**
  * Represents collections of standoff properties.
  */
object StandoffProperties {

    // represents the standoff properties defined on the base standoff tag
    val systemProperties = Set(
        OntologyConstants.KnoraBase.StandoffTagHasStart,
        OntologyConstants.KnoraBase.StandoffTagHasEnd,
        OntologyConstants.KnoraBase.StandoffTagHasStartIndex,
        OntologyConstants.KnoraBase.StandoffTagHasEndIndex,
        OntologyConstants.KnoraBase.StandoffTagHasStartParentIndex,
        OntologyConstants.KnoraBase.StandoffTagHasEndParentIndex,
        OntologyConstants.KnoraBase.StandoffTagHasUUID
    )
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON for standoff handling.
  */
object RepresentationV1JsonProtocol extends DefaultJsonProtocol with NullOptions with SprayJsonSupport {

    import org.knora.webapi.messages.v1.responder.usermessages.UserDataV1JsonProtocol._

    implicit val createStandoffResponseV1Format: RootJsonFormat[CreateStandoffResponseV1] = jsonFormat1(CreateStandoffResponseV1)
}