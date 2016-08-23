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

import java.util.UUID

import org.knora.webapi
import org.knora.webapi.messages.v1.responder._
import org.knora.webapi.messages.v1.responder.groupmessages.GroupInfoType.GroupInfoType
import org.knora.webapi.messages.v1.responder.usermessages.{NewUserDataV1, UserDataV1, UserProfileV1}
import org.knora.webapi.responders.v1.GroupsResponderV1
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException}
import spray.json.{DefaultJsonProtocol, JsonFormat, NullOptions, RootJsonFormat}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new group.
  *
  * @param name               the name of the group to be created (unique).
  * @param description        the description of the group to be created.
  * @param belongsToProject   the project inside which the group will be created.
  * @param isActiveGroup      the status of the group to be created.
  * @param hasSelfJoinEnabled the status of self-join of the group to be created.
  */
case class CreateGroupApiRequestV1(name: String,
                                   description: String,
                                   belongsToProject: Option[IRI],
                                   isActiveGroup: Boolean,
                                   hasSelfJoinEnabled: Boolean) {
    def toJsValue = GroupV1JsonProtocol.createGroupApiRequestV1Format.write(this)
}

/**
  * Represents an API request payload that asks the Knora API server to update one property of an existing group.
  *
  * @param propertyIri  the property of the group to be updated.
  * @param newValue     the new value for the property of the group to be updated.
  */
case class UpdateGroupApiRequestV1(propertyIri: String,
                                     newValue: String) {
    def toJsValue = GroupV1JsonProtocol.updateGroupApiRequestV1Format.write(this)
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
  * @param infoType is the type of the group information: full or short.
  * @param userProfile the profile of the user making the request.
  */
case class GroupsGetRequestV1(infoType: GroupInfoType, userProfile: Option[UserProfileV1]) extends GroupsResponderRequestV1


/**
  * Get everything about a single group identified through it's IRI.
  *
  * @param iri Iri of the group.
  * @param infoType is the type of the group information: full or short.
  * @param userProfileV1 the profile of the user making the request.
  */
case class GroupInfoByIRIGetRequest(iri: IRI, infoType: GroupInfoType.Value, userProfileV1: Option[UserProfileV1]) extends GroupsResponderRequestV1


/**
  * Find everything about a single group identified through it's shortname. Because it is only required to have unique
  * names inside a project, it is required to supply the name of the project.
  *
  * @param projectIri the IRI of the project the group is part of.
  * @param groupName the name of the group.
  * @param infoType is the type of the project information.
  * @param userProfileV1 the profile of the user making the request.
  */
case class GroupInfoByNameGetRequest(projectIri: IRI, groupName: String, infoType: GroupInfoType.Value, userProfileV1: Option[UserProfileV1]) extends GroupsResponderRequestV1


/**
  * Requests the creation of a new group.
  *
  * @param newGroupInfo the [[NewGroupInfoV1]] information for creating the new group.
  * @param userProfile the user profile of the user creating the new group.
  * @param apiRequestID the ID of the API request.
  */
case class GroupCreateRequestV1(newGroupInfo: NewGroupInfoV1,
                                userProfile: UserProfileV1,
                                apiRequestID: UUID) extends GroupsResponderRequestV1

/**
  * Request updating of an existing group.
  *
  * @param groupIri the IRI of the group to be updated.
  * @param propertyIri the IRI of the property to be updated.
  * @param newValue the new value for the property.
  * @param userProfile the user profile of the user requesting the update.
  * @param apiRequestID the ID of the API request.
  */
case class GroupInfoUpdateRequestV1(groupIri: webapi.IRI,
                                    propertyIri: webapi.IRI,
                                    newValue: Any,
                                    userProfile: UserProfileV1,
                                    apiRequestID: UUID) extends GroupsResponderRequestV1

/**
  * Request updating the group's permissions.
  *
  * @param userProfile the user profile of the user requesting the update.
  * @param apiRequestID the ID of the API request.
  */
case class GroupPermissionUpdateRequest(userProfile: UserProfileV1,
                                        apiRequestID: UUID) extends GroupsResponderRequestV1


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

/**
  * Represents an answer to a group creating/modifying operation.
  *
  * @param group_info the new group info of the created/modified group.
  * @param userdata   information about the user that made the request.
  */
case class GroupOperationResponseV1(group_info: GroupInfoV1, userdata: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = GroupV1JsonProtocol.groupOperationResponseV1Format.write(this)
}
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * The information describing a group.
  *
  * @param id the IRI if the group.
  * @param name the name of the group.
  * @param description the description of the group.
  * @param belongsToProject the project this group belongs to.
  * @param isActiveGroup the group's status.
  * @param hasSelfJoinEnabled the group's self-join status.
  * @param hasPermissions the permissions attached to the group.
  *
  */
case class GroupInfoV1(id: IRI,
                       name: String,
                       description: Option[String] = None,
                       belongsToProject: IRI,
                       isActiveGroup: Option[Boolean] = None,
                       hasSelfJoinEnabled: Option[Boolean] = None,
                       hasPermissions: Seq[GroupPermissionV1] = Nil) {

    def convertToShortGroupInfoV1: GroupInfoV1 = {
        GroupInfoV1(
            id = id,
            name = name,
            description = description,
            belongsToProject = belongsToProject,
            isActiveGroup = None, // removed
            hasSelfJoinEnabled = None, // removed
            hasPermissions = Nil // removed
        )
    }
}


/**
  * Represents basic information about the group which need to be supplied during group creation.
  *
  * @param name the name of the group.
  * @param description the optional description of the group
  * @param belongsToProject the project this group belongs to.
  * @param isActiveGroup the group's status.
  * @param hasSelfJoinEnabled the group's self-join status.
  */
case class NewGroupInfoV1(name: String,
                          description: Option[String] = None,
                          belongsToProject: IRI,
                          isActiveGroup: Boolean = true,
                          hasSelfJoinEnabled: Boolean = false)

/**
  * Represents a group permission.
  * @param name the name of the permission.
  * @param value the value of the permission.
  */
case class GroupPermissionV1(name: IRI, value: Either[Boolean, List[IRI]])

object GroupInfoType extends Enumeration {
    type GroupInfoType = Value
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

    import org.knora.webapi.messages.v1.responder.usermessages.UserV1JsonProtocol._

    implicit val groupPermissionV1Format: JsonFormat[GroupPermissionV1] = jsonFormat2(GroupPermissionV1)
    implicit val groupInfoV1Format: JsonFormat[GroupInfoV1] = jsonFormat7(GroupInfoV1)
    // we have to use lazyFormat here because `UserV1JsonProtocol` contains an import statement for this object.
    // this results in recursive import statements
    // rootFormat makes it return the expected type again.
    // https://github.com/spray/spray-json#jsonformats-for-recursive-types
    implicit val groupsResponseV1Format: RootJsonFormat[GroupsResponseV1] = rootFormat(lazyFormat(jsonFormat2(GroupsResponseV1)))
    implicit val groupInfoResponseV1Format: RootJsonFormat[GroupInfoResponseV1] = rootFormat(lazyFormat(jsonFormat2(GroupInfoResponseV1)))
    implicit val createGroupApiRequestV1Format: RootJsonFormat[CreateGroupApiRequestV1] = rootFormat(lazyFormat(jsonFormat5(CreateGroupApiRequestV1)))
    implicit val updateGroupApiRequestV1Format: RootJsonFormat[UpdateGroupApiRequestV1] = rootFormat(lazyFormat(jsonFormat2(UpdateGroupApiRequestV1)))
    implicit val groupOperationResponseV1Format: RootJsonFormat[GroupOperationResponseV1] = rootFormat(lazyFormat(jsonFormat2(GroupOperationResponseV1)))
}