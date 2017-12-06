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

package org.knora.webapi.messages.admin.responder.groupsmessages

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.messages.admin.responder.KnoraRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectsADMJsonProtocol}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder._
import org.knora.webapi.messages.v1.responder.groupmessages.GroupInfoV1
import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.{BadRequestException, IRI}
import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new group.
  *
  * @param name        the name of the group to be created (unique).
  * @param description the description of the group to be created.
  * @param project     the project inside which the group will be created.
  * @param status      the status of the group to be created (active = true, inactive = false).
  * @param selfjoin    the status of self-join of the group to be created.
  */
case class CreateGroupApiRequestADM(name: String,
                                    description: Option[String],
                                    project: IRI,
                                    status: Boolean,
                                    selfjoin: Boolean) extends GroupsADMJsonProtocol {

    def toJsValue = createGroupApiRequestADMFormat.write(this)
}

/**
  * Represents an API request payload that asks the Knora API server to update an existing group.
  *
  * @param name        the new group's name.
  * @param description the new group's description.
  * @param status      the new group's status.
  * @param selfjoin    the new group's self-join status.
  */
case class ChangeGroupApiRequestADM(name: Option[String] = None,
                                    description: Option[String] = None,
                                    status: Option[Boolean] = None,
                                    selfjoin: Option[Boolean] = None) extends GroupsADMJsonProtocol {

    val parametersCount = List(
        name,
        description,
        status,
        selfjoin
    ).flatten.size

    // something needs to be sent, i.e. everything 'None' is not allowed
    if (parametersCount == 0) throw BadRequestException("No data sent in API request.")


    def toJsValue = changeGroupApiRequestADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing a request message that can be sent to [[GroupsResponderADM]].
  */
sealed trait GroupsResponderRequestADM extends KnoraRequestADM

// Requests
/**
  * Get all information about all groups.
  *
  * @param requestingUser the user initiating the request.
  */
case class GroupsGetRequestADM(requestingUser: Option[UserADM]) extends GroupsResponderRequestADM

/**
  * Get everything about a single group identified through it's IRI or shortname. Because it is only required to have unique
  * names inside a project, it is required to supply the name of the project in conjunction with the shortname.
  *
  * @param maybeGroupIri   IRI of the group.
  * @param maybeGroupName  the name of the group.
  * @param maybeProjectIri the IRI of the project the group belongs to.
  * @param requestingUser the user initiating the request.
  */
case class GroupGetRequestADM(maybeGroupIri: Option[IRI], maybeGroupName: Option[String], maybeProjectIri: Option[IRI], requestingUser: Option[UserADM]) extends GroupsResponderRequestADM {

    // need either group IRI, or group name and project IRI
    if (maybeGroupIri.isEmpty || (maybeGroupName.isEmpty || maybeProjectIri.isEmpty)) {
        throw BadRequestException("Need to provide either group IRI, or group name and project IRI.")
    }

}

/**
  * Returns all members of the group identified by iri or by group name / project IRI.
  *
  * @param maybeGroupIri   IRI of the group.
  * @param maybeGroupName  the name of the group.
  * @param maybeProjectIri the IRI of the project the group belongs to.
  * @param requestingUser the user initiating the request.
  */
case class GroupMembersGetRequestADM(maybeGroupIri: Option[IRI], maybeGroupName: Option[String], maybeProjectIri: Option[IRI], requestingUser: Option[UserADM]) extends GroupsResponderRequestADM {

    // need either group IRI, or group name and project IRI
    if (maybeGroupIri.isEmpty || (maybeGroupName.isEmpty || maybeProjectIri.isEmpty)) {
        throw BadRequestException("Need to provide either group IRI, or group name and project IRI.")
    }

}

/**
  * Requests the creation of a new group.
  *
  * @param createRequest the [[CreateGroupApiRequestADM]] information for creating the new group.
  * @param requestingUser the user initiating the request.
  * @param apiRequestID  the ID of the API request.
  */
case class GroupCreateRequestADM(createRequest: CreateGroupApiRequestADM,
                                 requestingUser: UserADM,
                                    apiRequestID: UUID) extends GroupsResponderRequestADM

/**
  * Request updating of an existing group.
  *
  * @param groupIri           the IRI of the group to be updated.
  * @param changeGroupRequest the data which needs to be update.
  * @param requestingUser the user initiating the request.
  * @param apiRequestID       the ID of the API request.
  */
case class GroupChangeRequestADM(groupIri: IRI,
                                    changeGroupRequest: ChangeGroupApiRequestADM,
                                 requestingUser: UserADM,
                                    apiRequestID: UUID) extends GroupsResponderRequestADM

/**
  * Request updating the group's permissions.
  *
  * @param requestingUser the user initiating the request.
  * @param apiRequestID the ID of the API request.
  */
case class GroupPermissionUpdateRequestADM(requestingUser: UserADM,
                                           apiRequestID: UUID) extends GroupsResponderRequestADM


// Responses
/**
  * Represents the Knora API v1 JSON response to a request for information about all groups.
  *
  * @param groups information about all existing groups.
  */
case class GroupsResponseADM(groups: Seq[GroupADM]) extends KnoraResponseV1 with GroupsADMJsonProtocol {
    def toJsValue = groupsResponseADMFormat.write(this)
}

/**
  * Represents the Knora API v1 JSON response to a request for information about a single group.
  *
  * @param group all information about the group.
  */
case class GroupResponseADM(group: GroupADM) extends KnoraResponseV1 with GroupsADMJsonProtocol {
    def toJsValue = groupResponseADMFormat.write(this)
}

/**
  * Represents an answer to a group membership request.
  *
  * @param members the group's members.
  */
case class GroupMembersResponseADM(members: Seq[UserADM]) extends KnoraResponseV1 with GroupsADMJsonProtocol {
    def toJsValue = groupMembersResponseADMFormat.write(this)
}

/**
  * Represents an answer to a group creating/modifying operation.
  *
  * @param group the new group information of the created/modified group.
  */
case class GroupOperationResponseADM(group: GroupADM) extends KnoraResponseV1 with GroupsADMJsonProtocol {
    def toJsValue = groupOperationResponseADMFormat.write(this)
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
case class GroupADM(id: IRI,
                    name: String,
                    description: Option[String] = None,
                    project: ProjectADM,
                    status: Boolean,
                    selfjoin: Boolean) {

    def asGroupInfoV1: GroupInfoV1 = {

        GroupInfoV1(
            id = id,
            name = name,
            description = description,
            project = project.id,
            status = status,
            selfjoin = selfjoin
        )
    }
}


/**
  * Payload used for updating of an existing group.
  *
  * @param name        the name of the group.
  * @param description the description of the group.
  * @param status      the group's status.
  * @param selfjoin    the group's self-join status.
  */
case class GroupUpdatePayloadADM(name: Option[String] = None,
                                 description: Option[String] = None,
                                 status: Option[Boolean] = None,
                                 selfjoin: Option[Boolean] = None)


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about groups.
  */
trait GroupsADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with ProjectsADMJsonProtocol {

    import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._

    implicit val groupADMFormat: JsonFormat[GroupADM] = jsonFormat6(GroupADM)
    implicit val groupsResponseADMFormat: RootJsonFormat[GroupsResponseADM] = jsonFormat(GroupsResponseADM, "groups")
    implicit val groupResponseADMFormat: RootJsonFormat[GroupResponseADM] = jsonFormat(GroupResponseADM, "group")
    implicit val groupMembersResponseADMFormat: RootJsonFormat[GroupMembersResponseADM] = jsonFormat(GroupMembersResponseADM, "members")
    implicit val createGroupApiRequestADMFormat: RootJsonFormat[CreateGroupApiRequestADM] = jsonFormat(CreateGroupApiRequestADM, "name", "description", "project", "status", "selfjoin")
    implicit val changeGroupApiRequestADMFormat: RootJsonFormat[ChangeGroupApiRequestADM] = jsonFormat(ChangeGroupApiRequestADM, "name", "description", "status", "selfjoin")
    implicit val groupOperationResponseADMFormat: RootJsonFormat[GroupOperationResponseADM] = jsonFormat(GroupOperationResponseADM, "group")
}