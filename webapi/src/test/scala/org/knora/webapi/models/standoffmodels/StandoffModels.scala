/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models.standoffmodels

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceV2

import java.time.Instant
import java.util.UUID

sealed abstract case class XXX private (
//  internalFilename: String,
//  label: String
) {
  def toJsonLd(
    shortcode: String = "0001",
    ontologyName: String = "knora-api",
    className: Option[String] = None,
    ontologyIRI: Option[String] = None
  ): String =
    // TODO: implement
    ""

  def toMessage(
    resourceIri: Option[String] = None,
    internalMimeType: Option[String] = None,
    originalFilename: Option[String] = None,
    originalMimeType: Option[String] = None,
    comment: Option[String] = None,
    customValueIri: Option[SmartIri] = None,
    customValueUUID: Option[UUID] = None,
    customValueCreationDate: Option[Instant] = None,
    valuePermissions: Option[String] = None,
    resourcePermissions: Option[String] = None,
    resourceCreationDate: Option[Instant] = None,
    resourceClassIRI: Option[SmartIri] = None,
    valuePropertyIRI: Option[SmartIri] = None,
    project: Option[ProjectADM] = None
  ): CreateResourceV2 =
    // TODO: implement
    CreateResourceV2(
      ???,
      ???,
      ???,
      ???,
      ???
    )
}

object XXX {
  def make(): XXX =
    new XXX() {}
}
