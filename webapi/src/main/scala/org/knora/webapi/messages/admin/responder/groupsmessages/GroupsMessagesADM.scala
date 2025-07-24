/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.groupsmessages
import sttp.tapir.Schema
import sttp.tapir.generic.auto.*
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.messages.admin.responder.AdminKnoraResponseADM
import org.knora.webapi.slice.admin.domain.model.Group

/**
 * Represents the Knora API v1 JSON response to a request for information about all groups.
 *
 * @param groups information about all existing groups.
 */
final case class GroupsGetResponseADM(groups: Seq[Group]) extends AdminKnoraResponseADM
object GroupsGetResponseADM {
  implicit val codec: JsonCodec[GroupsGetResponseADM] = DeriveJsonCodec.gen[GroupsGetResponseADM]
  
  // Custom schema to ensure 'groups' field is not marked as optional in OpenAPI
  // This addresses the Tapir library issue: https://github.com/softwaremill/tapir/issues/993
  implicit val schema: Schema[GroupsGetResponseADM] = {
    // Create a custom schema for Seq[Group] that is not optional
    implicit val seqGroupSchema: Schema[Seq[Group]] = Schema.schemaForIterable[Group, Seq].copy(isOptional = false)
    Schema.derived[GroupsGetResponseADM]
  }
}

/**
 * Represents the Knora API v1 JSON response to a request for information about a single group.
 *
 * @param group all information about the group.
 */
final case class GroupGetResponseADM(group: Group) extends AdminKnoraResponseADM
object GroupGetResponseADM {
  implicit val codec: JsonCodec[GroupGetResponseADM] = DeriveJsonCodec.gen[GroupGetResponseADM]
}
