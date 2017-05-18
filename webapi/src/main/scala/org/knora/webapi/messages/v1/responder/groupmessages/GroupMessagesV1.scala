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
import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.responders.v1.GroupsResponderV1
import spray.json.{DefaultJsonProtocol, JsonFormat, NullOptions, RootJsonFormat}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new group.
  *
  * @param name               the name of the group to be created (unique).
  * @param description        the description of the group to be created.
  * @param belongsToProject   the project inside which the group will be created.
  * @param status             the status of the group to be created.
  * @param hasSelfJoinEnabled the status of self-join of the group to be created.
  */
case class CreateGroupApiRequestV1(name: String,
                                   description: Option[String],
                                   belongsToProject: IRI,
                                   status: Boolean = true,
                                   hasSelfJoinEnabled: Boolean = false) extends GroupV1JsonProtocol {
    def toJsValue = createGroupApiRequestV1Format.write(this)
}

/**
  * Represents an API request payload that asks the Knora API server to update an existing group.
  *
  * @param name               the new group's name.
  * @param description        the new group's description.
  * @param status             the new group's status.
  * @param hasSelfJoinEnabled the new group's self-join status.
  */
case class ChangeGroupApiRequestV1(name: Option[String] = None,
                                   description: Option[String] = None,
                                   status: Option[Boolean] = None,
                                   hasSelfJoinEnabled: Option[Boolean] = None) extends GroupV1JsonProtocol{
    def toJsValue = changeGroupApiRequestV1Format.write(this)
}

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
  * @param groupIri      Iri of the group.
  * @param userProfileV1 the profile of the user making the request.
  */
case class GroupInfoByIRIGetRequest(groupIri: IRI, userProfileV1: Option[UserProfileV1]) extends GroupsResponderRequestV1

/**
  * Find everything about a single group identified through it's shortname. Because it is only required to have unique
  * names inside a project, it is required to supply the name of the project.
  *
  * @param projectIri    the IRI of the project the group is part of.
  * @param groupName     the name of the group.
  * @param userProfileV1 the profile of the user making the request.
  */
case class GroupInfoByNameGetRequest(projectIri: IRI, groupName: String, userProfileV1: Option[UserProfileV1]) extends GroupsResponderRequestV1

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

/**
  * Requests the creation of a new group.
  *
  * @param createRequest the [[CreateGroupApiRequestV1]] information for creating the new group.
  * @param userProfile   the user profile of the user creating the new group.
  * @param apiRequestID  the ID of the API request.
  */
case class GroupCreateRequestV1(createRequest: CreateGroupApiRequestV1,
                                userProfile: UserProfileV1,
                                apiRequestID: UUID) extends GroupsResponderRequestV1

/**
  * Request updating of an existing group.
  *
  * @param groupIri           the IRI of the group to be updated.
  * @param changeGroupRequest the data which needs to be update.
  * @param userProfile        the user profile of the user requesting the update.
  * @param apiRequestID       the ID of the API request.
  */
case class GroupChangeRequestV1(groupIri: IRI,
                                changeGroupRequest: ChangeGroupApiRequestV1,
                                userProfile: UserProfileV1,
                                apiRequestID: UUID) extends GroupsResponderRequestV1

/**
  * Request updating the group's permissions.
  *
  * @param userProfile  the user profile of the user requesting the update.
  * @param apiRequestID the ID of the API request.
  */
case class GroupPermissionUpdateRequest(userProfile: UserProfileV1,
                                        apiRequestID: UUID) extends GroupsResponderRequestV1


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
  * Represents an answer to a group creating/modifying operation.
  *
  * @param group_info the new group info of the created/modified group.
  */
case class GroupOperationResponseV1(group_info: GroupInfoV1) extends KnoraResponseV1 with GroupV1JsonProtocol {
    def toJsValue = groupOperationResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * The information describing a group.
  *
  * @param id                 the IRI if the group.
  * @param name               the name of the group.
  * @param description        the description of the group.
  * @param belongsToProject   the project this group belongs to.
  * @param status             the group's status.
  * @param hasSelfJoinEnabled the group's self-join status.
  *
  */
case class GroupInfoV1(id: IRI,
                       name: String,
                       description: Option[String] = None,
                       belongsToProject: IRI,
                       status: Boolean,
                       hasSelfJoinEnabled: Boolean)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formating

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about groups.
  */
trait GroupV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit val groupInfoV1Format: JsonFormat[GroupInfoV1] = jsonFormat6(GroupInfoV1)
    implicit val groupsResponseV1Format: RootJsonFormat[GroupsResponseV1] = jsonFormat1(GroupsResponseV1)
    implicit val groupInfoResponseV1Format: RootJsonFormat[GroupInfoResponseV1] = jsonFormat1(GroupInfoResponseV1)
    implicit val createGroupApiRequestV1Format: RootJsonFormat[CreateGroupApiRequestV1] = jsonFormat5(CreateGroupApiRequestV1)
    implicit val changeGroupApiRequestV1Format: RootJsonFormat[ChangeGroupApiRequestV1] = jsonFormat4(ChangeGroupApiRequestV1)
    implicit val groupOperationResponseV1Format: RootJsonFormat[GroupOperationResponseV1] = jsonFormat1(GroupOperationResponseV1)
}