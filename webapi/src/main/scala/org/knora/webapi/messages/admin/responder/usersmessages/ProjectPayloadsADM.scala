package org.knora.webapi.messages.admin.responder.usersmessages

import org.knora.webapi.IRI

trait ProjectCreatePayloadTraitADM {
  def create(
    id: Option[IRI],
    shortname: Shortname,
    shortcode: Shortcode,
    longname: Option[Longname],
    description: Description,
    keywords: Keywords,
    logo: Option[Logo],
    status: Status,
    selfjoin: Selfjoin
  ): ProjectCreatePayloadADM
}

/**
 * Project payload
 */
sealed abstract case class ProjectCreatePayloadADM(
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

object ProjectCreatePayloadADM extends ProjectCreatePayloadTraitADM {

  /** The create constructor */
  override def create(
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
