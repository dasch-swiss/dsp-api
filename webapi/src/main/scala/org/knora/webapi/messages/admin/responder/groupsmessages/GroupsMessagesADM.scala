/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.groupsmessages
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json.JsonFormat
import spray.json.RootJsonFormat

import org.knora.webapi.IRI
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.ResponderRequest.KnoraRequestADM
import org.knora.webapi.messages.admin.responder.AdminKnoraResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsADMJsonProtocol
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.User

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
 * An abstract trait representing a request message that can be sent to 'GroupsResponderADM'.
 */
sealed trait GroupsResponderRequestADM extends KnoraRequestADM with RelayedMessage

/**
 * Get all information about all groups.      the user initiating the request.
 */
case class GroupsGetADM() extends GroupsResponderRequestADM

/**
 * Get everything about a single group identified through its IRI. A successful response will be
 * an [[Option[GroupADM] ]], which will be `None` if the group was not found.
 *
 * @param groupIri             IRI of the group.
 */
case class GroupGetADM(groupIri: IRI) extends GroupsResponderRequestADM

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
case class GroupMembersGetRequestADM(groupIri: IRI, requestingUser: User) extends GroupsResponderRequestADM

// Responses
/**
 * Represents the Knora API v1 JSON response to a request for information about all groups.
 *
 * @param groups information about all existing groups.
 */
case class GroupsGetResponseADM(groups: Seq[GroupADM]) extends AdminKnoraResponseADM with GroupsADMJsonProtocol {
  def toJsValue = groupsGetResponseADMFormat.write(this)
}

/**
 * Represents the Knora API v1 JSON response to a request for information about a single group.
 *
 * @param group all information about the group.
 */
case class GroupGetResponseADM(group: GroupADM) extends AdminKnoraResponseADM with GroupsADMJsonProtocol {
  def toJsValue = groupResponseADMFormat.write(this)
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

  def groupIri: GroupIri = GroupIri.unsafeFrom(id)

  /**
   * Allows to sort collections of GroupADM. Sorting is done by the id.
   */
  def compare(that: GroupADM): Int = this.id.compareTo(that.id)
}
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
 * A spray-json protocol for generating Knora API v1 JSON providing data about groups.
 */
trait GroupsADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with ProjectsADMJsonProtocol {

  implicit val groupADMFormat: JsonFormat[GroupADM] = jsonFormat6(GroupADM)
  implicit val groupsGetResponseADMFormat: RootJsonFormat[GroupsGetResponseADM] =
    jsonFormat(GroupsGetResponseADM, "groups")
  implicit val groupResponseADMFormat: RootJsonFormat[GroupGetResponseADM] = jsonFormat(GroupGetResponseADM, "group")
}
