/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.v1.responder.groupmessages

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.{BadRequestException, IRI}
import org.knora.webapi.messages.v1.responder._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.responders.v1.GroupsResponderV1
import spray.json.{DefaultJsonProtocol, JsonFormat, NullOptions, RootJsonFormat}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

// User 'admin' route for writing.

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
  * @param groupIri      IRI of the group.
  * @param userProfileV1 the profile of the user making the request.
  */
case class GroupInfoByIRIGetRequestV1(groupIri: IRI, userProfileV1: Option[UserProfileV1]) extends GroupsResponderRequestV1

/**
  * Find everything about a single group identified through it's shortname. Because it is only required to have unique
  * names inside a project, it is required to supply the name of the project.
  *
  * @param projectIri    the IRI of the project the group is part of.
  * @param groupName     the name of the group.
  * @param userProfileV1 the profile of the user making the request.
  */
case class GroupInfoByNameGetRequestV1(projectIri: IRI, groupName: String, userProfileV1: Option[UserProfileV1]) extends GroupsResponderRequestV1

/**
  * Returns all members of the group identified by iri.
  *
  * @param groupIri      the IRI of th group.
  * @param userProfileV1 the profile of the user making the request.
  */
case class GroupMembersByIRIGetRequestV1(groupIri: IRI, userProfileV1: UserProfileV1) extends GroupsResponderRequestV1

/**
  * Returns all members of the group identified by group name / project IRI.
  *
  * @param projectIri    the IRI of the project the group is part of.
  * @param groupName     the name of the group.
  * @param userProfileV1 the profile of the user making the request.
  */
case class GroupMembersByNameGetRequestV1(projectIri: IRI, groupName: String, userProfileV1: UserProfileV1) extends GroupsResponderRequestV1



// Responses
/**
  * Represents the Knora API v1 JSON response to a request for information about all groups.
  *
  * @param groups information about all existing groups.
  */
case class GroupsResponseV1(groups: Seq[GroupInfoV1]) extends KnoraResponseV1 with GroupV1JsonProtocol {
    def toJsValue = groupsResponseV1Format.write(this)
}

/**
  * Represents the Knora API v1 JSON response to a request for information about a single group.
  *
  * @param group_info all information about the group.
  */
case class GroupInfoResponseV1(group_info: GroupInfoV1) extends KnoraResponseV1 with GroupV1JsonProtocol {
    def toJsValue = groupInfoResponseV1Format.write(this)
}

/**
  * Represents an answer to a group membership request.
  *
  * @param members the group's members.
  */
case class GroupMembersResponseV1(members: Seq[IRI]) extends KnoraResponseV1 with GroupV1JsonProtocol {
    def toJsValue = groupMembersResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * The information describing a group.
  *
  * @param id          the IRI if the group.
  * @param name        the name of the group.
  * @param description the description of the group.
  * @param project     the project this group belongs to.
  * @param status      the group's status.
  * @param selfjoin    the group's self-join status.
  *
  */
case class GroupInfoV1(id: IRI,
                       name: String,
                       description: Option[String] = None,
                       project: IRI,
                       status: Boolean,
                       selfjoin: Boolean)



//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formating

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about groups.
  */
trait GroupV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit val groupInfoV1Format: JsonFormat[GroupInfoV1] = jsonFormat6(GroupInfoV1)
    implicit val groupsResponseV1Format: RootJsonFormat[GroupsResponseV1] = jsonFormat(GroupsResponseV1, "groups")
    implicit val groupInfoResponseV1Format: RootJsonFormat[GroupInfoResponseV1] = jsonFormat(GroupInfoResponseV1, "group_info")
    implicit val groupMembersResponseV1Format: RootJsonFormat[GroupMembersResponseV1] = jsonFormat(GroupMembersResponseV1, "members")
}