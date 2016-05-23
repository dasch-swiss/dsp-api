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

package org.knora.webapi.messages.v1.responder.groupmessages

import org.knora.webapi.messages.v1.responder._
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.responders.v1.GroupsResponderV1
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException}
import spray.json.{DefaultJsonProtocol, JsonFormat, NullOptions, RootJsonFormat}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing a request message that can be sent to [[GroupsResponderV1]].
  */
sealed trait GroupsResponderRequestV1 extends KnoraRequestV1

// Requests
/**
  * Get all information about all groups.
  *
  * @param userProfile the profile of the user making the request.
  */
case class GroupsGetRequestV1(userProfile: Option[UserProfileV1]) extends GroupsResponderRequestV1


/**
  * Get everything about a single group identified through it's IRI.
  *
  * @param iri Iri of the group.
  * @param requestType is the type of the group information: full or short.
  * @param userProfileV1 the profile of the user making the request.
  */
case class GroupInfoByIRIGetRequest(iri: IRI, requestType: GroupInfoType.Value, userProfileV1: Option[UserProfileV1]) extends GroupsResponderRequestV1


/**
  * Find everything about a single group identified through it's shortname.
  *
  * @param name of the group.
  * @param requestType is the type of the project information.
  * @param userProfileV1 the profile of the user making the request.
  */
case class GroupInfoByNameGetRequest(name: String, requestType: GroupInfoType.Value, userProfileV1: Option[UserProfileV1]) extends GroupsResponderRequestV1


// Responses
/**
  * Represents the Knora API v1 JSON response to a request for information about all groups.
  *
  * @param groups information about all existing groups.
  * @param userdata information about the user that made the request.
  */
case class GroupsResponseV1(groups: Seq[GroupInfoV1], userdata: Option[UserDataV1]) extends KnoraResponseV1 {
    def toJsValue = GroupV1JsonProtocol.groupsResponseV1Format.write(this)
}

/**
  * Represents the Knora API v1 JSON response to a request for information about a single group.
  *
  * @param group_info all information about the group.
  * @param userdata information about the user that made the request.
  */
case class GroupInfoResponseV1(group_info: GroupInfoV1, userdata: Option[UserDataV1]) extends KnoraResponseV1 {
    def toJsValue = GroupV1JsonProtocol.groupInfoResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * The information describing a group.
  *
  * @param id the IRI if the group.
  * @param name the name of the group.
  * @param description the description of the group.
  */
case class GroupInfoV1(id: IRI,
                       name: String,
                       description: Option[String] = None)

object GroupInfoType extends Enumeration {
    val SHORT = Value(0, "short")
    val FULL = Value(1, "full")

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
      * [[InconsistentTriplestoreDataException]].
      *
      * @param name the name of the value.
      * @return the requested value.
      */
    def lookup(name: String): Value = {
        valueMap.get(name) match {
            case Some(value) => value
            case None => throw InconsistentTriplestoreDataException(s"Project info type not supported: $name")
        }
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formating

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about groups.
  */
object GroupV1JsonProtocol extends DefaultJsonProtocol with NullOptions {

    implicit val groupInfoV1Format: JsonFormat[GroupInfoV1] = jsonFormat3(GroupInfoV1)
    // we have to use lazyFormat here because `UserV1JsonProtocol` contains an import statement for this object.
    // this results in recursive import statements
    // rootFormat makes it return the expected type again.
    // https://github.com/spray/spray-json#jsonformats-for-recursive-types
    implicit val groupsResponseV1Format: RootJsonFormat[GroupsResponseV1] = rootFormat(lazyFormat(jsonFormat2(GroupsResponseV1)))
    implicit val groupInfoResponseV1Format: RootJsonFormat[GroupInfoResponseV1] = rootFormat(lazyFormat(jsonFormat2(GroupInfoResponseV1)))
}