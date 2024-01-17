/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortcodeIdentifier
import org.knora.webapi.messages.admin.responder.sipimessages.SipiFileInfoGetResponseADM
import org.knora.webapi.responders.admin.SipiResponder
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointAndZioHandler
import org.knora.webapi.slice.common.domain.SparqlEncodedString

final case class FilesEndpointsHandler(
  filesEndpoints: FilesEndpoints,
  sipiResponder: SipiResponder,
  mapper: HandlerMapper
) {

  private val getAdminFilesShortcodeFileIri =
    SecuredEndpointAndZioHandler[(ShortcodeIdentifier, SparqlEncodedString), SipiFileInfoGetResponseADM](
      filesEndpoints.getAdminFilesShortcodeFileIri,
      (user: User) => { case (shortcode: ShortcodeIdentifier, filename: SparqlEncodedString) =>
        sipiResponder.getFileInfoForSipiADM(shortcode, filename.value, user)
      }
    )

  val allHandlers = List(getAdminFilesShortcodeFileIri).map(mapper.mapEndpointAndHandler(_))
}

object FilesEndpointsHandler {
  val layer = ZLayer.derive[FilesEndpointsHandler]
}
