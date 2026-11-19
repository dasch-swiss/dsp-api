/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import zio.*

import org.knora.webapi.slice.`export`.domain.DataTaskId
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.security.ServicePrincipal

/** An export endpoint, as named in a service call's audit line and span. */
enum ExportAction(val value: String) {
  case Create   extends ExportAction("export.create")
  case Status   extends ExportAction("export.status")
  case Download extends ExportAction("export.download")
  case Delete   extends ExportAction("export.delete")
}

/** How a service's export call ended, as the server saw it; `not_owner` and `not_found` give the client one 404. */
enum ExportOutcome(val value: String) {
  case Ok       extends ExportOutcome("ok")
  case NotOwner extends ExportOutcome("not_owner")
  case NotFound extends ExportOutcome("not_found")
  case Conflict extends ExportOutcome("conflict")
  case Error    extends ExportOutcome("error")
}

/**
 * The single audit line of one export call by a trusted service. Every field is present, an unknown one as `-`.
 * `projectIri` is the export's project when the export was found, else the requested one; `requestedProjectIri` is
 * always the path value, so a refused call names both. `exportId` is the export's, else the requested one, and
 * absent only on a trigger that created none. `jti` is the only value the caller chose freely, so it is the
 * last field and is quoted.
 */
final case class ExportServiceAudit(
  principal: ServicePrincipal,
  action: ExportAction,
  outcome: ExportOutcome,
  projectIri: ProjectIri,
  requestedProjectIri: ProjectIri,
  exportId: Option[DataTaskId],
) {

  def render: String =
    "Export service call: " +
      s"action=${action.value} outcome=${outcome.value} service=${principal.name.value} kid=${principal.kid} " +
      s"project_iri=${projectIri.value} requested_project_iri=${requestedProjectIri.value} " +
      s"export_id=${exportId.fold("-")(_.value)} jti=${ExportServiceAudit.quoted(principal.jti)}"

  def log: UIO[Unit] = ZIO.logInfo(render)
}

object ExportServiceAudit {

  val ServiceNameKey = "dsp.service.name"
  val ServiceKidKey  = "dsp.service.kid"
  val ActionKey      = "dsp.service.action"
  val ShortcodeKey   = "dsp.project.shortcode"

  private val MaxJtiChars = 256

  // Control, line-separator and format characters (bidi overrides among them) become spaces, so the jti cannot
  // forge a second line or disguise the one it is in.
  private[projects] def quoted(value: String): String = {
    val safe = value
      .take(MaxJtiChars)
      .map(c =>
        if (c.isControl || c == '\u2028' || c == '\u2029' || Character.getType(c) == Character.FORMAT) ' ' else c,
      )
    "\"" + safe.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
  }
}
