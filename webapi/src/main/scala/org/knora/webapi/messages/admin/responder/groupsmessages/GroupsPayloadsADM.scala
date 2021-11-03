package org.knora.webapi.messages.admin.responder.groupsmessages

import org.knora.webapi.IRI
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
