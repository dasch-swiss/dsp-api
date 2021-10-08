package org.knora.webapi.messages.admin.responder.projectsmessages

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.valueObjects.{
  Shortname,
  Longname,
  Shortcode,
  Description,
  Keywords,
  Logo,
  Status,
  Selfjoin
}

/**
 * Project payload
 */
final case class ProjectCreatePayloadADM(
  id: Option[IRI],
  shortname: Shortname,
  shortcode: Shortcode,
  longname: Option[Longname],
  description: Description,
  keywords: Keywords,
  logo: Option[Logo],
  status: Status,
  selfjoin: Selfjoin
)
