package org.knora.webapi.messages.admin.responder.groupsmessages

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.valueObjects.{Description, Name, Selfjoin, Status}

/**
 * Group create payload
 */
final case class GroupCreatePayloadADM(
  id: Option[IRI],
  name: Name,
  descriptions: Description,
  project: IRI,
  status: Status,
  selfjoin: Selfjoin
)
