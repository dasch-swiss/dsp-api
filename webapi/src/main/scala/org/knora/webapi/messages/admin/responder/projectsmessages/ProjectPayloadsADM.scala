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

// TODO: https://github.com/dasch-swiss/dsp-api/pull/1909#discussion_r718330669

/**
 * Project payload
 */
sealed abstract case class ProjectCreatePayloadADM private (
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

object ProjectCreatePayloadADM {

  /** The create constructor */
  def create(
    id: Option[IRI] = None,
    shortname: Shortname,
    shortcode: Shortcode,
    longname: Option[Longname] = None,
    description: Description,
    keywords: Keywords,
    logo: Option[Logo] = None,
    status: Status,
    selfjoin: Selfjoin
  ): ProjectCreatePayloadADM =
    new ProjectCreatePayloadADM(
      id = id,
      shortname = shortname,
      shortcode = shortcode,
      longname = longname,
      description = description,
      keywords = keywords,
      logo = logo,
      status = status,
      selfjoin = selfjoin
    ) {}
}
