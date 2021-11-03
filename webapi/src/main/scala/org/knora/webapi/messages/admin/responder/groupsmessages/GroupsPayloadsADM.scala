package org.knora.webapi.messages.admin.responder.groupsmessages

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.valueObjects.{GroupDescription, GroupName, GroupSelfJoin, GroupStatus}

/**
 * Group create payload
 */
final case class GroupCreatePayloadADM(
  id: Option[IRI],
  name: GroupName,
  descriptions: GroupDescription,
  project: IRI,
  status: GroupStatus,
  selfjoin: GroupSelfJoin
)
