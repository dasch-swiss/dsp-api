/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import java.time.Instant
import java.util.UUID
import scala.language.implicitConversions

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.KnoraIris.*
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri as KResourceClassIri
import org.knora.webapi.slice.common.jena.StatementOps.*

final case class KnoraApiCreateValueModel(
  shortcode: Shortcode,
  resourceIri: ResourceIri,
  resourceClassIri: KResourceClassIri,
  valuePropertyIri: PropertyIri,
  valueType: SmartIri,
  valueIri: Option[ValueIri],
  valueUuid: Option[UUID],
  valueCreationDate: Option[Instant],
  valuePermissions: Option[String],
  valueFileValueFilename: Option[String],
  valueContent: ValueContentV2,
)
