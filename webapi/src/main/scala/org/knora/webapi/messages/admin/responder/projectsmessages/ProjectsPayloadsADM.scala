package org.knora.webapi.messages.admin.responder.projectsmessages

import org.knora.webapi.messages.admin.responder.valueObjects._

/**
 * Project creation payload
 */
final case class ProjectCreatePayloadADM(
  id: Option[ProjectIRI] = None,
  shortname: Shortname,
  shortcode: Shortcode,
  longname: Option[Longname] = None,
  description: ProjectDescription,
  keywords: Keywords,
  logo: Option[Logo] = None,
  status: ProjectStatus,
  selfjoin: ProjectSelfJoin
)
