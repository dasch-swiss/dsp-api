package org.knora.webapi.messages.admin.responder.groupsmessages

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.valueObjects.{Name, Description, Selfjoin, Status}

/**
 * Group payload
 */
sealed abstract case class GroupCreatePayloadADM private (
  //  TODO: shouldn't IRI be a value object too - since it's just String synonym?
  id: Option[IRI],
  name: Name,
  descriptions: Description,
  project: IRI,
  status: Status,
  selfjoin: Selfjoin
)

object GroupCreatePayloadADM {

  /** The create constructor */
  def create(
    id: Option[IRI],
    name: Name,
    descriptions: Description,
    project: IRI,
    status: Status,
    selfjoin: Selfjoin
  ): GroupCreatePayloadADM =
    new GroupCreatePayloadADM(
      id = id,
      name = name,
      descriptions = descriptions,
      project = project,
      status = status,
      selfjoin = selfjoin
    ) {}
}
