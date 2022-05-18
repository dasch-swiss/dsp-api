/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.groupsmessages

import dsp.valueobjects.Iri._
import dsp.valueobjects.Group._

/**
 * Group create payload
 */
final case class GroupCreatePayloadADM(
  id: Option[GroupIRI] = None,
  name: GroupName,
  descriptions: GroupDescriptions,
  project: ProjectIRI,
  status: GroupStatus,
  selfjoin: GroupSelfJoin
)

/**
 * Payload used for updating of an existing group.
 *
 * @param name          the name of the group.
 * @param descriptions  the descriptions of the group.
 * @param status        the group's status.
 * @param selfjoin      the group's self-join status.
 */
final case class GroupUpdatePayloadADM(
  name: Option[GroupName] = None,
  descriptions: Option[GroupDescriptions] = None,
//  TODO-mpro: create separate payload for status update
  status: Option[GroupStatus] = None,
  selfjoin: Option[GroupSelfJoin] = None
)
