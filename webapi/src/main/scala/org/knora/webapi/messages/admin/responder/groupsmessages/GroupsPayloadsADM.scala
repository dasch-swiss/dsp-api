package org.knora.webapi.messages.admin.responder.groupsmessages

import org.knora.webapi.messages.admin.responder.valueObjects._

/**
 * Group create payload
 */
final case class GroupCreatePayloadADM(
  id: Option[GroupIRI] = None,
  name: GroupName,
  descriptions: GroupDescription,
  project: ProjectIRI,
  status: GroupStatus,
  selfjoin: GroupSelfJoin
)

/**
 * Payload used for updating of an existing group.
 *
 * @param name          the name of the group.
 * @param descriptions  the descriptions of the group.
 * @param status        the group's status.
 * @param selfjoin      the group's self-join status.
 */
final case class GroupUpdatePayloadADM(
  name: Option[GroupName] = None,
  descriptions: Option[GroupDescription] = None,
//  TODO-mpro remove status from here and create sperate payload?
  status: Option[GroupStatus] = None,
  selfjoin: Option[GroupSelfJoin] = None
)
