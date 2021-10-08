package org.knora.webapi.messages.admin.responder.listsmessages

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.valueObjects.{Name, Labels, Comments}

/**
 * List payload
 */
sealed abstract case class ListCreatePayloadADM private (
  id: Option[IRI] = None,
  projectIri: IRI,
  name: Option[Name] = None,
  labels: Labels,
  comments: Comments
)

object ListCreatePayloadADM {

  /** The create constructor */
  def create(
    id: Option[IRI],
    projectIri: IRI,
    name: Option[Name],
    labels: Labels,
    comments: Comments
  ): ListCreatePayloadADM =
    new ListCreatePayloadADM(
      id = id,
      projectIri = projectIri,
      name = name,
      labels = labels,
      comments = comments
    ) {}
}
