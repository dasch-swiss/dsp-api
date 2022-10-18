/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.groupsmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.RootJsonFormat

import java.util.UUID

import dsp.errors.BadRequestException
import dsp.valueobjects.V2
import org.knora.webapi.IRI
import org.knora.webapi.messages.ResponderRequest.KnoraRequestADM
import org.knora.webapi.messages.admin.responder.KnoraResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
 * Represents an API request payload that asks the Knora API server to create a new group.
 *
 * @param id            the optional IRI of the group to be created (unique).
 * @param name          the name of the group to be created (unique).
 * @param descriptions  the descriptions of the group to be created.
 * @param project       the project inside which the group will be created.
 * @param status        the status of the group to be created (active = true, inactive = false).
 * @param selfjoin      the status of self-join of the group to be created.
 */
case class CreateGroupApiRequestADM(
  id: Option[IRI] = None,
  name: String,
  descriptions: Seq[V2.StringLiteralV2],
  project: IRI,
  status: Boolean,
  selfjoin: Boolean
) extends GroupsADMJsonProtocol {
  def toJsValue: JsValue = createGroupApiRequestADMFormat.write(this)
}

/**
 * Represents an API request payload that asks the Knora API server to update
 * an existing group. There are two change cases that are covered with this
 * data structure:
 * (1) change of name, descriptions, and selfjoin
 * (2) change of status
 *
 * @param name          the new group's name.
 * @param descriptions  the new group's descriptions.
 * @param status        the new group's status.
 * @param selfjoin      the new group's self-join status.
 */
case class ChangeGroupApiRequestADM(
  name: Option[String] = None,
  descriptions: Option[Seq[V2.StringLiteralV2]] = None,
  status: Option[Boolean] = None,
  selfjoin: Option[Boolean] = None
) extends GroupsADMJsonProtocol {
//  TODO-mpro: once status is separate route then it can be removed
  private val parametersCount = List(
    name,
    descriptions,
    status,
    selfjoin
  ).flatten.size

  // something needs to be sent, i.e. everything 'None' is not allowed
  if (parametersCount == 0) throw BadRequestException("No data sent in API request.")

  /**
   * check that only allowed information for the 2 cases is sent and not more.
   */
  // change status case
  if (status.isDefined) {
    if (parametersCount > 1) throw BadRequestException("Too many parameters sent for group status change.")
  }

  // change basic group information case
  if (parametersCount > 3) throw BadRequestException("Too many parameters sent for basic group information change.")

  def toJsValue: JsValue = changeGroupApiRequestADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
 * An abstract trait representing a request message that can be sent to 'GroupsResponderADM'.
 */
sealed trait GroupsResponderRequestADM extends KnoraRequestADM

/**
 * Get all information about all groups.      the user initiating the request.
 */
case class GroupsGetADM() extends GroupsResponderRequestADM

/**
 * Get all information about all groups.
 */
case class GroupsGetRequestADM() extends GroupsResponderRequestADM

/**
 * Get everything about a single group identified through its IRI. A successful response will be
 * an [[Option[GroupADM] ]], which will be `None` if the group was not found.
 *
 * @param groupIri             IRI of the group.
 */
case class GroupGetADM(groupIri: IRI) extends GroupsResponderRequestADM

/**
 * Get everything about a single group identified through its IRI. The response will be a
 * [[GroupGetResponseADM]], or an error if the group was not found.
 *
 * @param groupIri             IRI of the group.
 */
case class GroupGetRequestADM(groupIri: IRI) extends GroupsResponderRequestADM

/**
 * Get everything about a multiple groups identified by their IRIs. The response will be a
 * [[Set[GroupGetResponseADM] ]], or an error if one or more groups was not found.
 *
 * @param groupIris            the IRIs of the groups being requested
 */
case class MultipleGroupsGetRequestADM(
  groupIris: Set[IRI]
) extends GroupsResponderRequestADM

/**
 * Returns all members of the group identified by iri.
 *
 * @param groupIri             IRI of the group.
 * @param requestingUser       the user initiating the request.
 */
case class GroupMembersGetRequestADM(groupIri: IRI, requestingUser: UserADM) extends GroupsResponderRequestADM

/**
 * Requests the creation of a new group.
 *
 * @param createRequest        the [[GroupCreatePayloadADM]] information for creating the new group.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class GroupCreateRequestADM(
  createRequest: GroupCreatePayloadADM,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends GroupsResponderRequestADM

/**
 * Request updating of an existing group.
 *
 * @param groupIri             the IRI of the group to be updated.
 * @param changeGroupRequest   the data which needs to be update.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class GroupChangeRequestADM(
  groupIri: IRI,
  changeGroupRequest: GroupUpdatePayloadADM,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends GroupsResponderRequestADM

/**
 * Request changing the status (active/inactive) of an existing group.
 *
 * @param groupIri             the IRI of the group to be deleted.
 * @param changeGroupRequest   the data which needs to be update.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class GroupChangeStatusRequestADM(
  groupIri: IRI,
  changeGroupRequest: ChangeGroupApiRequestADM,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends GroupsResponderRequestADM

/**
 * Request updating the group's permissions.
 *
 * @param requestingUser the user initiating the request.
 * @param apiRequestID   the ID of the API request.
 */
case class GroupPermissionUpdateRequestADM(requestingUser: UserADM, apiRequestID: UUID)
    extends GroupsResponderRequestADM

// Responses
/**
 * Represents the Knora API v1 JSON response to a request for information about all groups.
 *
 * @param groups information about all existing groups.
 */
case class GroupsGetResponseADM(groups: Seq[GroupADM]) extends KnoraResponseADM with GroupsADMJsonProtocol {
  def toJsValue = groupsGetResponseADMFormat.write(this)
}

/**
 * Represents the Knora API v1 JSON response to a request for information about a single group.
 *
 * @param group all information about the group.
 */
case class GroupGetResponseADM(group: GroupADM) extends KnoraResponseADM with GroupsADMJsonProtocol {
  def toJsValue = groupResponseADMFormat.write(this)
}

/**
 * Represents an answer to a group membership request.
 *
 * @param members the group's members.
 */
case class GroupMembersGetResponseADM(members: Seq[UserADM]) extends KnoraResponseADM with GroupsADMJsonProtocol {
  def toJsValue = groupMembersResponseADMFormat.write(this)
}

/**
 * Represents an answer to a group creating/modifying operation.
 *
 * @param group the new group information of the created/modified group.
 */
case class GroupOperationResponseADM(group: GroupADM) extends KnoraResponseADM with GroupsADMJsonProtocol {
  def toJsValue = groupOperationResponseADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
 * The information describing a group.
 *
 * @param id            the IRI if the group.
 * @param name          the name of the group.
 * @param descriptions  the descriptions of the group.
 * @param project       the project this group belongs to.
 * @param status        the group's status.
 * @param selfjoin      the group's self-join status.
 */
case class GroupADM(
  id: IRI,
  name: String,
  descriptions: Seq[StringLiteralV2],
  project: ProjectADM,
  status: Boolean,
  selfjoin: Boolean
) extends Ordered[GroupADM] {

  /**
   * Allows to sort collections of GroupADM. Sorting is done by the id.
   */
  def compare(that: GroupADM): Int = this.id.compareTo(that.id)

  def asGroupShortADM: GroupShortADM =
    GroupShortADM(
      id = id,
      name = name,
      descriptions = descriptions,
      status = status,
      selfjoin = selfjoin
    )
}

/**
 * The information describing a group (without project).
 *
 * @param id            the IRI if the group.
 * @param name          the name of the group.
 * @param descriptions  the descriptions of the group.
 * @param status        the group's status.
 * @param selfjoin      the group's self-join status.
 */
case class GroupShortADM(id: IRI, name: String, descriptions: Seq[StringLiteralV2], status: Boolean, selfjoin: Boolean)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
 * A spray-json protocol for generating Knora API v1 JSON providing data about groups.
 */
trait GroupsADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with ProjectsADMJsonProtocol {

  import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._

  implicit val groupADMFormat: JsonFormat[GroupADM]           = jsonFormat6(GroupADM)
  implicit val groupShortADMFormat: JsonFormat[GroupShortADM] = jsonFormat5(GroupShortADM)
  implicit val groupsGetResponseADMFormat: RootJsonFormat[GroupsGetResponseADM] =
    jsonFormat(GroupsGetResponseADM, "groups")
  implicit val groupResponseADMFormat: RootJsonFormat[GroupGetResponseADM] = jsonFormat(GroupGetResponseADM, "group")
  implicit val groupMembersResponseADMFormat: RootJsonFormat[GroupMembersGetResponseADM] =
    jsonFormat(GroupMembersGetResponseADM, "members")
  implicit val createGroupApiRequestADMFormat: RootJsonFormat[CreateGroupApiRequestADM] =
    jsonFormat(CreateGroupApiRequestADM, "id", "name", "descriptions", "project", "status", "selfjoin")
  implicit val changeGroupApiRequestADMFormat: RootJsonFormat[ChangeGroupApiRequestADM] =
    jsonFormat(ChangeGroupApiRequestADM, "name", "descriptions", "status", "selfjoin")
  implicit val groupOperationResponseADMFormat: RootJsonFormat[GroupOperationResponseADM] =
    jsonFormat(GroupOperationResponseADM, "group")
}
