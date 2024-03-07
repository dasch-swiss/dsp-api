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
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsADMJsonProtocol
import org.knora.webapi.slice.admin.domain.model.Group

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
 * An abstract trait representing a request message that can be sent to 'GroupsResponderADM'.
 */
sealed trait GroupsResponderRequestADM extends KnoraRequestADM with RelayedMessage

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
  groupIris: Set[IRI],
) extends GroupsResponderRequestADM

// Responses
/**
 * Represents the Knora API v1 JSON response to a request for information about all groups.
 *
 * @param groups information about all existing groups.
 */
case class GroupsGetResponseADM(groups: Seq[Group]) extends AdminKnoraResponseADM with GroupsADMJsonProtocol {
  def toJsValue = groupsGetResponseADMFormat.write(this)
}

/**
 * Represents the Knora API v1 JSON response to a request for information about a single group.
 *
 * @param group all information about the group.
 */
case class GroupGetResponseADM(group: Group) extends AdminKnoraResponseADM with GroupsADMJsonProtocol {
  def toJsValue = groupResponseADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
 * A spray-json protocol for generating Knora API v1 JSON providing data about groups.
 */
trait GroupsADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with ProjectsADMJsonProtocol {

  implicit val groupADMFormat: JsonFormat[Group] = jsonFormat6(Group.apply)
  implicit val groupsGetResponseADMFormat: RootJsonFormat[GroupsGetResponseADM] =
    jsonFormat(GroupsGetResponseADM.apply, "groups")
  implicit val groupResponseADMFormat: RootJsonFormat[GroupGetResponseADM] =
    jsonFormat(GroupGetResponseADM.apply, "group")
}
